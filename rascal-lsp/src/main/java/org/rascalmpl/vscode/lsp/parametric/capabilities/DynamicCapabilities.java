/*
 * Copyright (c) 2018-2025, NWO-I CWI and Swat.engineering
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.rascalmpl.vscode.lsp.parametric.capabilities;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.Unregistration;
import org.eclipse.lsp4j.UnregistrationParams;
import org.eclipse.lsp4j.services.LanguageClient;
import org.rascalmpl.vscode.lsp.parametric.ILanguageContributions;
import org.rascalmpl.vscode.lsp.util.concurrent.CompletableFutureUtils;

public class DynamicCapabilities {

    private static final Logger logger = LogManager.getLogger(DynamicCapabilities.class);

    private final LanguageClient client;
    private final List<AbstractDynamicCapability<?>> supportedCapabilities;
    private final Executor exec;
    private final Map<String, Registration> currentRegistrations = new ConcurrentHashMap<>();

    // private @MonotonicNonNull ClientCapabilities clientCapabilities;
    // private @MonotonicNonNull ServerCapabilities serverCapabilities;

    public DynamicCapabilities(LanguageClient client, List<AbstractDynamicCapability<? extends Object>> supportedCapabilities, Executor exec) {
        this.client = client;
        this.supportedCapabilities = supportedCapabilities;
        this.exec = exec;
    }

    // public void setStaticCapabilities(ClientCapabilities clientCapabilities, ServerCapabilities serverCapabilities) {
    //     // TODO Use these to determine whether we actually need/are allowed to register certain capabilities
    //     // If the client does not support dynamic regitration for a certain capability, do not register it. Instead, register it statically.
    //     this.clientCapabilities = clientCapabilities;
    //     this.serverCapabilities = serverCapabilities;
    // }

    public CompletableFuture<Void> registerCapabilities(ILanguageContributions contribs) {
        logger.debug("Registering some capabilities");

        // Compute registrations purely based on contributions
        // This requires waiting for an evaluator to load, which takes long, and should not block our logbook
        return CompletableFutureUtils.reduce(supportedCapabilities.stream().map(c -> registration(c, contribs)))
            .thenApply(LinkedList::new) // Make sure the list is modifiable
            .thenAccept(capabilities -> {
                // Remove capabilities that these contributions do not have
                capabilities.removeIf(p -> p == null);
                logger.debug("Contributions support {}/{} dynamic capabilities", capabilities.size(), supportedCapabilities.size());
                if (capabilities.isEmpty()) {
                    return; // Void
                }

                // Since we have some bookkeeping to do, we will now block our logbook for a moment
                synchronized (currentRegistrations) {
                    List<Registration> registrations = new LinkedList<>();
                    List<Unregistration> unregistrations = new LinkedList<>();
                    for (var entry : capabilities) {
                        var cap = entry.getLeft();
                        var registration = entry.getRight();
                        var opts = registration.getRegisterOptions();

                        logger.debug("Processing capability {}", registration.getMethod());

                        // Check if we already have this registration
                        var existing = currentRegistrations.get(registration.getMethod());
                        if (existing != null) {
                            logger.debug("We registered {} before", registration.getMethod());
                            // We registered this capability before.
                            // Let's see if we need to make any changes do the registration.
                            var mergedOpts = cap.mergeOptions(existing.getRegisterOptions(), opts);
                            if (!mergedOpts.equals(opts)) {
                                logger.debug("Options for {} changed: {} vs. {}", registration.getMethod(), existing.getRegisterOptions(), mergedOpts);
                                // The options of the registration changed; we need to unregister it, and update the options for the new registration.
                                unregistrations.add(unregistration(cap));
                                registration.setRegisterOptions(mergedOpts);
                            } else {
                                // Nothing changed; do not register this.
                                logger.debug("No option changes for {}", registration.getMethod());
                                continue;
                            }
                        }

                        logger.debug("Adding {} to task list", registration.getMethod());
                        registrations.add(registration);
                    }

                    if (!unregistrations.isEmpty()) {
                        logger.debug("Unregistering: {}", unregistrations.stream().map(Unregistration::getMethod).collect(Collectors.toList()));
                        client.unregisterCapability(new UnregistrationParams(unregistrations)).join();
                        unregistrations.forEach(u -> currentRegistrations.remove(u.getMethod()));
                    } else {
                        logger.debug("No capabilities to unregister");
                    }

                    if (!registrations.isEmpty()) {
                        logger.debug("Registering: {}", registrations.stream().map(Registration::getMethod).collect(Collectors.toList()));
                        client.registerCapability(new RegistrationParams(registrations)).join();
                        registrations.forEach(r -> currentRegistrations.put(r.getMethod(), r));
                    } else  {
                        logger.debug("No capabilities to register");
                    }

                    return;

                    // return doUnregistrations(unregistrations)
                    //     .thenCompose((v) -> doRegistrations(registrations));
                }
        })
        .exceptionally(e -> {
            logger.error("Unexpected error occurred while updating dynamic capabilities", e);
            return null;
        });
    }

    public CompletableFuture<Void> unregisterCapabilties(ILanguageContributions contribs) {
        logger.warn("Unegistering dynmaic capabilities not yet supported");
        return CompletableFuture.runAsync(() -> {});
        // return CompletableFutureUtils.reduce(supportedCapabilities.stream().map(c -> removeRegistration(c, contribs)))
        //     .thenApply(us -> us.stream()
        //         .filter(Objects::nonNull)
        //         .map(u -> Either.<Registration, Unregistration>forRight(u))
        //         .collect(Collectors.toList()))
        //     .thenCompose(this::doRegistrationRequests);
    }

    private <T> CompletableFuture<@Nullable Pair<AbstractDynamicCapability<T>, Registration>> registration(AbstractDynamicCapability<T> cap, ILanguageContributions contribs) {
        return cap.hasContribution(contribs).thenCompose(contributes -> contributes
            ? cap.options(contribs)
                .thenApply(opts -> registration(cap, opts))
                .thenApply(r -> Pair.of(cap, r))
            : null
        );
    }

    private CompletableFuture<@Nullable Unregistration> removeRegistration(AbstractDynamicCapability<?> cap, ILanguageContributions contribs) {
        // TODO This is not right, since another contribution might still supply this.
        // We probably need to recalculate all capabilities when something changes in the multiplexer
        return cap.hasContribution(contribs).thenApply(contributes -> contributes
            ? unregistration(cap)
            : null);
    }

    private Registration registration(AbstractDynamicCapability<?> cap, Object opts) {
        return new Registration(cap.id(), cap.methodName(), opts);
    }

    private Unregistration unregistration(AbstractDynamicCapability<?> cap) {
        return new Unregistration(cap.id(), cap.methodName());
    }

    private CompletableFuture<Void> doRegistrations(List<Registration> rs) {
        if (rs.isEmpty()) {
            logger.debug("Skipping registration; nothing to do.");
            return CompletableFuture.completedFuture(null);
        }

        logger.debug("Registering capabilities: {}", rs.stream().map(Registration::getMethod).collect(Collectors.toList()));
        return client.registerCapability(new RegistrationParams(rs))
            .thenAcceptAsync((v) -> rs.forEach(r -> {
                var rem = currentRegistrations.put(r.getMethod(), r);
                logger.debug("Added {}", rem);
            }), exec);
    }

    // private CompletableFuture<Void> doUnregistrations(List<Unregistration> us) {
    //     if (us.isEmpty()) {
    //         logger.debug("Skipping unregistration; nothing to do.");
    //         return CompletableFuture.completedFuture(null);
    //     }

    //     logger.debug("Unregistering capabilities: {}", us.stream().map(Unregistration::getMethod).collect(Collectors.toList()));
    //     return client.unregisterCapability(new UnregistrationParams(us))
    //         .thenAcceptAsync((v) -> us.forEach(u -> {
    //             var rem = currentRegistrations.remove(u.getMethod());
    //             logger.debug("Removed {}", rem);
    //         }), exec);
    // }

}
