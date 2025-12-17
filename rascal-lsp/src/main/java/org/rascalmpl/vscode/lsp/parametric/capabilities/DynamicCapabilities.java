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
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.Unregistration;
import org.eclipse.lsp4j.UnregistrationParams;
import org.eclipse.lsp4j.services.LanguageClient;
import org.rascalmpl.vscode.lsp.parametric.ILanguageContributions;
import org.rascalmpl.vscode.lsp.util.Lists;
import org.rascalmpl.vscode.lsp.util.NamedThreadPool;
import org.rascalmpl.vscode.lsp.util.concurrent.CompletableFutureUtils;

/**
 * Takes care of (un)registering capabilities dynamically.
 * Receives a list of supported capabiltities ({@link AbstractDynamicCapability}) and notifies the client when capabilities change.
 */
public class DynamicCapabilities {

    private static final Logger logger = LogManager.getLogger(DynamicCapabilities.class);

    private final LanguageClient client;
    private final Executor parallelExec;
    private final Executor singleExec = NamedThreadPool.single("parametric-capabilities");
    private final Collection<AbstractDynamicCapability<?>> supportedCapabilities;
    private final Set<AbstractDynamicCapability<?>> staticCapabilities;
    private final Map<String, Registration> currentRegistrations = new ConcurrentHashMap<>();

    /**
     * @param client The language client to send regiser/unregister requests to.
     * @param exec The executor to use for asynchronous tasks.
     * @param supportedCapabilities The capabilities to register with the client.
     * @param clientCapabilities The capabilities of the client. Determine whether dynamic registration is supported at all.
     */
    public DynamicCapabilities(LanguageClient client, Executor exec, List<AbstractDynamicCapability<?>> supportedCapabilities, ClientCapabilities clientCapabilities) {
        this.client = client;
        this.parallelExec = exec;
        this.supportedCapabilities = supportedCapabilities;

        // Check which capabilities to register statically
        Set<AbstractDynamicCapability<?>> caps = new HashSet<>();
        for (var cap : supportedCapabilities) {
            if (cap.shouldRegisterStatically(clientCapabilities)) {
                caps.add(cap);
            }
        }
        // Set once and only read from now on
        this.staticCapabilities = Collections.unmodifiableSet(caps);
    }

    /**
     * Register static capabilities with the server.
     */
    public void registerStaticCapabilities(ServerCapabilities result) {
        for (var cap : staticCapabilities) {
            cap.registerStatically(result);
        }
    }

    /**
     * Update capabilities for language contributions.
     * @param contribs The contributions to represent.
     * @return A void future that completes when all capabilities are updated.
     */
    public CompletableFuture<Void> updateCapabilities(Collection<ILanguageContributions> contribs) {
        // Copy the contributions so we know we are looking at a stable set of elements.
        // If the contributions change, we expect our caller to call again.
        var stableContribs = new LinkedHashSet<>(contribs);
        return CompletableFutureUtils.reduce(supportedCapabilities.stream()
            .filter(cap -> !staticCapabilities.contains(cap))
            .map(c -> tryRegistration(c, stableContribs)), parallelExec)
            .thenAcceptAsync(capabiltities -> {
                List<Registration> registrations = new LinkedList<>();
                List<Unregistration> unregistrations = new LinkedList<>();
                for (var entry : capabiltities) {
                    var cap = entry.getLeft();
                    var registration = entry.getRight();
                    var method = cap.methodName();
                    var existingRegistration = currentRegistrations.get(method);

                    if (registration == null) {
                        if (existingRegistration != null) {
                            // this capability was removed
                            logger.trace("{} is no longer supported by contributions", method);
                            unregistrations.add(new Unregistration(cap.id(), cap.methodName()));
                        }
                        // nothing more to do
                        continue;
                    }

                    if (existingRegistration != null) {
                        if (Objects.deepEquals(registration.getRegisterOptions(), existingRegistration.getRegisterOptions())) {
                            logger.trace("Options for {} did not change since last registration: {}", method, registration.getRegisterOptions());
                            continue;
                        }
                        logger.trace("Options for {} changed since the previous registration; remove before adding again", method);
                        unregistrations.add(new Unregistration(cap.id(), cap.methodName()));
                    }

                    logger.trace("Registering dynamic capability {}", method);
                    registrations.add(registration);
                }

                if (!unregistrations.isEmpty()) {
                    logger.debug("Unregistering dynamic capabilities: {}", unregistrations.stream().map(Unregistration::getMethod).collect(Collectors.toList()));
                    try {
                        client.unregisterCapability(new UnregistrationParams(unregistrations)).get();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (ExecutionException e) {
                        logger.error("Doing unregistrations failed!", e);
                        return;
                    }
                    unregistrations.forEach(u -> currentRegistrations.remove(u.getMethod()));
                } else {
                    logger.debug("No unregistrations to do");
                }
                if (!registrations.isEmpty()) {
                    logger.debug("Registering dynamic capabilities: {}", registrations.stream().map(Registration::getMethod).collect(Collectors.toList()));
                    try {
                        client.registerCapability(new RegistrationParams(registrations)).get();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (ExecutionException e) {
                        logger.error("Doing registrations failed!", e);
                        return;
                    }
                    registrations.forEach(r -> currentRegistrations.put(r.getMethod(), r));
                } else {
                    logger.debug("No registrations to do");
                }
            }, singleExec
        );
    }

    private <T> CompletableFuture<Pair<AbstractDynamicCapability<T>, @Nullable Registration>> tryRegistration(AbstractDynamicCapability<T> cap, Collection<ILanguageContributions> contribs) {
        if (contribs.isEmpty()) {
            return CompletableFutureUtils.completedFuture(Pair.of(cap, null), parallelExec);
        }

        // Filter contributions by providing this capability
        return CompletableFutureUtils.<List<ILanguageContributions>>flatten(
            contribs.stream().map(c -> cap.isProvidedBy(c).thenApply(b -> b.booleanValue() ? List.of(c) : List.of())),
            CompletableFutureUtils.completedFuture(Collections.emptyList(), parallelExec),
            Lists::union
        ).<Pair<AbstractDynamicCapability<T>, @Nullable Registration>>thenCompose(cs -> {
            if (cs.isEmpty()) {
                return CompletableFutureUtils.completedFuture(Pair.of(cap, null), parallelExec);
            }

            var allOpts = cs.stream()
                .<CompletableFuture<@Nullable T>>map(cap::options)
                .collect(Collectors.toList());
            return CompletableFutureUtils.reduce(allOpts, cap::mergeNullableOptions) // non-empty, so no need to provide a reduction identity
                .thenApply(opts -> Pair.of(cap, new Registration(cap.id(), cap.methodName(), opts)));
        });
    }

}
