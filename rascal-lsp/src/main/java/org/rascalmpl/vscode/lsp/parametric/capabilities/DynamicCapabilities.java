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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.PolyNull;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.Unregistration;
import org.eclipse.lsp4j.UnregistrationParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.rascalmpl.vscode.lsp.parametric.ILanguageContributions;
import org.rascalmpl.vscode.lsp.parametric.LanguageContributionsMultiplexer;
import org.rascalmpl.vscode.lsp.util.concurrent.CompletableFutureUtils;

public class DynamicCapabilities {

    private static final Logger logger = LogManager.getLogger(DynamicCapabilities.class);

    private final LanguageClient client;
    private final List<AbstractDynamicCapability<?>> supportedCapabilities;
    private final Map<String, Registration> currentRegistrations = new ConcurrentHashMap<>();

    // private @MonotonicNonNull ClientCapabilities clientCapabilities;
    // private @MonotonicNonNull ServerCapabilities serverCapabilities;

    public DynamicCapabilities(LanguageClient client, List<AbstractDynamicCapability<?>> supportedCapabilities) {
        this.client = client;
        this.supportedCapabilities = supportedCapabilities;
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
                capabilities.removeIf(Optional::isEmpty);
                logger.debug("Contributions support {}/{} dynamic capabilities", capabilities.size(), supportedCapabilities.size());
                if (capabilities.isEmpty()) {
                    return; // Void
                }

                // Since we have some bookkeeping to do, we will now block our logbook for a moment
                synchronized (currentRegistrations) {
                    List<Registration> registrations = new LinkedList<>();
                    List<Unregistration> unregistrations = new LinkedList<>();
                    for (var optEntry : capabilities) {
                        var entry = optEntry.get();
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
                            var existingOpts = existing.getRegisterOptions();
                            Object mergedOpts = null;
                            if (existingOpts != null &&
                                (mergedOpts = cap.mergeOptions(existingOpts, opts)) != null &&
                                !existingOpts.equals(mergedOpts)) {
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

    public CompletableFuture<Void> updateCapabilities(Map<String, LanguageContributionsMultiplexer> contribs) {
        return updateCapabilities(contribs.values()
            .stream()
            .map(ILanguageContributions.class::cast)
            .collect(Collectors.toList()));
    }

    private CompletableFuture<Void> updateCapabilities(Collection<ILanguageContributions> contribs) {
        return CompletableFutureUtils.reduce(supportedCapabilities.stream()
            .map(cap -> anyTrue(contribs, cap::hasContribution)
                .thenCompose(b -> {
                    if (b) {
                        // has contrib; calculate merged options
                        var remainingOptions = contribs.stream()
                            .map(c -> cap.options(c).thenApply(Object.class::cast))
                            .collect(Collectors.toList());
                        return CompletableFutureUtils.reduce(remainingOptions)
                            .thenApply(opts -> mergeOptions(cap, opts))
                            .thenApply(opts -> Either.<Registration, Unregistration>forLeft(registration(cap, opts)));
                    } else {
                        // does not have contrib
                        return CompletableFuture.completedFuture(Either.<Registration, Unregistration>forRight(unregistration(cap)));
                    }
                })))
            .thenAccept(es -> {
                List<Registration> registrations = new LinkedList<>();
                List<Unregistration> unregistrations = new LinkedList<>();
                for (var e : es) {
                    e.map(registrations::add, unregistrations::add);
                }

                try {
                    doRegistrations(registrations, unregistrations);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    logger.error("Doing registrations failed!", e);
                }
            });
    }

    private static <T> T mergeOptions(AbstractDynamicCapability<? extends T> cap, List<? extends @NonNull T> opts) {
        if (opts.isEmpty()) {
            throw new IllegalArgumentException("Cannot merge empty list of options");
        }
        if (opts.size() == 1) {
            return opts.get(0);
        }

        var merged = opts.get(0);
        for (int i = 1; i < opts.size(); i++) {
            merged = cap.mergeOptions(merged, opts.get(i));
        }
        return merged;
    }

    private synchronized void doRegistrations(List<Registration> registrations, List<Unregistration> unregistrations) throws InterruptedException, ExecutionException {
        if (!unregistrations.isEmpty()) {
            logger.debug("Unregistering: {}", unregistrations);
            client.unregisterCapability(new UnregistrationParams(unregistrations)).get();
            unregistrations.forEach(u -> currentRegistrations.remove(u.getMethod()));
        }

        if (!registrations.isEmpty()) {
            logger.debug("Registering: {}", registrations);
            client.registerCapability(new RegistrationParams(registrations)).get();
            registrations.forEach(r -> currentRegistrations.put(r.getMethod(), r));
        }
    }

    private static <T> CompletableFuture<Optional<Pair<AbstractDynamicCapability<T>, Registration>>> registration(AbstractDynamicCapability<T> cap, ILanguageContributions contribs) {
        return cap.hasContribution(contribs).thenCompose(contributes -> contributes
            ? cap.options(contribs)
                .thenApply(opts -> registration(cap, opts))
                .thenApply(r -> Optional.of(Pair.of(cap, r)))
            : CompletableFuture.completedFuture(Optional.empty())
        );
    }

    private static Registration registration(AbstractDynamicCapability<?> cap, @PolyNull Object opts) {
        if (opts != null) {
            return new Registration(cap.id(), cap.methodName(), opts);
        }
        return new Registration(cap.id(), cap.methodName());
    }

    private static Unregistration unregistration(AbstractDynamicCapability<?> cap) {
        return new Unregistration(cap.id(), cap.methodName());
    }

    private static CompletableFuture<Boolean> anyTrue(Collection<ILanguageContributions> contribs, Function<ILanguageContributions, CompletableFuture<Boolean>> hasContrib) {
        return CompletableFutureUtils.reduce(contribs.stream().map(hasContrib))
            .thenApply(bs -> bs.stream().anyMatch(Boolean::booleanValue));
    }

}
