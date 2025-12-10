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

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.Unregistration;
import org.eclipse.lsp4j.UnregistrationParams;
import org.eclipse.lsp4j.services.LanguageClient;
import org.rascalmpl.vscode.lsp.IBaseTextDocumentService;
import org.rascalmpl.vscode.lsp.parametric.ILanguageContributions;
import org.rascalmpl.vscode.lsp.parametric.LanguageContributionsMultiplexer;
import org.rascalmpl.vscode.lsp.util.concurrent.CompletableFutureUtils;

/**
 * Takes care of (un)registering capabilities dynamically.
 * Receives a list of supported capabiltities ({@link AbstractDynamicCapability}) and notifies the client when capabilities change.
 */
public class DynamicCapabilities {

    private static final Logger logger = LogManager.getLogger(DynamicCapabilities.class);

    private final LanguageClient client;
    private final List<AbstractDynamicCapability<?>> supportedCapabilities;
    private final Set<AbstractDynamicCapability<?>> staticCapabilities;
    private final Map<String, Registration> currentRegistrations = new ConcurrentHashMap<>();

    private @MonotonicNonNull ClientCapabilities clientCapabilities;
    private @MonotonicNonNull ServerCapabilities serverCapabilities;

    public DynamicCapabilities(LanguageClient client, List<AbstractDynamicCapability<?>> supportedCapabilities) {
        this.client = client;
        this.supportedCapabilities = supportedCapabilities;
        this.staticCapabilities = new HashSet<>();
    }

    /**
     * Determines whether the server should register capabiltities statically now instead of dynamically later.
     * @param clientCapabilities The capabilities of the client, which determine whether dynamic registration is supported at all.
     * @param serverCapabilities The server capabilities to modify.
     */
    public void setStaticCapabilities(ClientCapabilities clientCapabilities, final ServerCapabilities serverCapabilities) {
        // Use these to determine whether we actually need/are allowed to register certain capabilities
        // If the client does not support dynamic registration for a certain capability, do not register it. Instead, register it statically.
        this.clientCapabilities = clientCapabilities;
        this.serverCapabilities = serverCapabilities;

        for (var cap : supportedCapabilities) {
            if (!cap.checkDynamicCapability(clientCapabilities, serverCapabilities)) {
                staticCapabilities.add(cap);
            }
        }
    }

    /**
     * Update capabilities for language contributions (typically on  {@link IBaseTextDocumentService#unregisterLanguage}).
     * @param contribs The contributions to represent
     * @return A void future that completes when all capabilities are updated.
     */
    public CompletableFuture<Void> updateCapabilities(Map<String,LanguageContributionsMultiplexer> contributions) {
        return updateCapabilities(contributions.values().stream().map(ILanguageContributions.class::cast).collect(Collectors.toList()));
    }

    /**
     * Update capabilities for language contributions.
     * @param contribs The contributions to represent.
     * @return A void future that completes when all capabilities are updated.
     */
    CompletableFuture<Void> updateCapabilities(Collection<ILanguageContributions> contribs) {
        // Compute registrations purely based on contributions
        // This requires waiting for an evaluator to load, which might take long, and should not block our logbook
        return CompletableFutureUtils.reduce(supportedCapabilities.stream().filter(cap -> !staticCapabilities.contains(cap)).map(c -> maybeRegistration(c, contribs)))
            .thenAccept(capabilities -> {
                // Since we have some bookkeeping to do, we will now block our logbook for a moment
                synchronized (currentRegistrations) {
                    List<Registration> registrations = new LinkedList<>();
                    List<Unregistration> unregistrations = new LinkedList<>();
                    for (var entry : capabilities) {
                        var cap = entry.getLeft();
                        var method = cap.methodName();
                        var newRegistration = entry.getRight();
                        var existingRegistration = currentRegistrations.get(method);

                        if (newRegistration.isEmpty()) {
                            if (existingRegistration != null) {
                                // this capability was removed
                                logger.trace("{} is no longer supported by contributions", method);
                                unregistrations.add(unregistration(cap));
                            }
                            // nothing more to do
                            continue;
                        }

                        var registration = newRegistration.get(); // safe, since we checked emptyness before
                        if (existingRegistration != null) {
                            if (Objects.deepEquals(registration.getRegisterOptions(), existingRegistration.getRegisterOptions())) {
                                logger.trace("Options for {} did not change since last registration: {}", method, registration.getRegisterOptions());
                                continue;
                            }
                            logger.trace("Options for {} changed since the previous registration; remove before adding again", method);
                            unregistrations.add(unregistration(cap));
                        }

                        logger.trace("Registering dynamic capability {}", method);
                        registrations.add(registration);
                    }

                    try {
                        doRegistrations(registrations, unregistrations);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (ExecutionException e) {
                        logger.error("Doing registrations failed!", e);
                    }
                }
            }
        )
        .exceptionally(e -> {
            logger.error("Unexpected error occurred while updating dynamic capabilities", e);
            return null;
        });
    }

    private synchronized void doRegistrations(List<Registration> registrations, List<Unregistration> unregistrations) throws InterruptedException, ExecutionException {
        if (!unregistrations.isEmpty()) {
            logger.debug("Unregistering dynamic capabilities: {}", unregistrations);
            client.unregisterCapability(new UnregistrationParams(unregistrations)).get();
            unregistrations.forEach(u -> currentRegistrations.remove(u.getMethod()));
        }

        if (!registrations.isEmpty()) {
            logger.debug("Registering dynamic capabilities: {}", registrations);
            client.registerCapability(new RegistrationParams(registrations)).get();
            registrations.forEach(r -> currentRegistrations.put(r.getMethod(), r));
        }
    }

    private static <T> CompletableFuture<Pair<AbstractDynamicCapability<T>, Optional<Registration>>> maybeRegistration(AbstractDynamicCapability<T> cap, Collection<ILanguageContributions> contribs) {
        var supportingContribs = contribs.stream().filter(c -> cap.hasContribution(c).join()).collect(Collectors.toList()); // join() is fine, since we should only be called inside a promise
        if (supportingContribs.isEmpty()) {
            return CompletableFuture.completedFuture(Pair.of(cap, Optional.empty()));
        }

        var allOpts = supportingContribs.stream()
            .<CompletableFuture<@Nullable T>>map(cap::options)
            .collect(Collectors.toList());
        var mergedOpts = CompletableFutureUtils.reduce(allOpts, cap::mergeOptions); // non-empty, so fine
        return mergedOpts.thenApply(opts -> Pair.of(cap, Optional.of(registration(cap, opts))));
    }

    private static Registration registration(AbstractDynamicCapability<?> cap, @Nullable Object opts) {
        if (opts != null) {
            return new Registration(cap.id(), cap.methodName(), opts);
        }
        return new Registration(cap.id(), cap.methodName());
    }

    private static Unregistration unregistration(AbstractDynamicCapability<?> cap) {
        return new Unregistration(cap.id(), cap.methodName());
    }

}
