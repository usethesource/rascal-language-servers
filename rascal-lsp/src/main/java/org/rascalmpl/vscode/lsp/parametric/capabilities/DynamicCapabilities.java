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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.Unregistration;
import org.eclipse.lsp4j.UnregistrationParams;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
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
    private final Executor exec;
    private final Executor singleExec = NamedThreadPool.singleDaemon("parametric-capabilities");
    private final CompletableFuture<Boolean> truthy;
    private final CompletableFuture<Boolean> falsy;
    private final Collection<AbstractDynamicCapability<?>> supportedCapabilities;

    // Set of capabilities that should bre registered statically instead of dynamically
    private final Set<AbstractDynamicCapability<?>> staticCapabilities;

    // Map of method names with current registration values
    private final Map<String, Registration> currentRegistrations = new ConcurrentHashMap<>();

    /**
     * @param client The language client to send register/unregister requests to.
     * @param supportedCapabilities The capabilities to register with the client.
     * @param clientCapabilities The capabilities of the client. Determine whether dynamic registration is supported at all.
     */
    public DynamicCapabilities(LanguageClient client, Executor exec, List<AbstractDynamicCapability<?>> supportedCapabilities, ClientCapabilities clientCapabilities) {
        this.client = client;
        this.exec = exec;
        this.truthy = CompletableFutureUtils.completedFuture(true, singleExec);
        this.falsy = CompletableFutureUtils.completedFuture(false, singleExec);
        this.supportedCapabilities = List.copyOf(supportedCapabilities);

        // Check which capabilities to register statically
        var caps = new HashSet<AbstractDynamicCapability<?>>();
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
     * @return A future that completes with a boolean that is false when any registration failed, and true otherwise.
     */
    public CompletableFuture<Boolean> updateCapabilities(Collection<ILanguageContributions> contribs) {
        // Copy the contributions so we know we are looking at a stable set of elements.
        // If the contributions change, we expect our caller to call again.
        var stableContribs = List.copyOf(contribs);
        var registrations = supportedCapabilities.stream()
            .filter(cap -> !staticCapabilities.contains(cap))
            .map(c -> tryBuildRegistration(c, stableContribs)
                .thenComposeAsync(r -> updateRegistration(c, r), singleExec));
        return CompletableFutureUtils.reduce(registrations, CompletableFutureUtils.completedFuture(true, singleExec), Boolean::booleanValue, Boolean::logicalAnd);
    }

    /**
     * Update the registration of a capability.
     * - If the capability is not yet registered, register it.
     * - If the capability is already registered, and the options changed, update it (by unregistering and registering with new options).
     * - If the capability is already registered and the options did not change, do nothing.
     * @param cap The capability to update.
     * @param registration The computed registration to do, or `null` when this capability is absent.
     * @return A future completing with `true` when successful, or `false` otherwise.
     */
    private <T> CompletableFuture<Boolean> updateRegistration(AbstractDynamicCapability<T> cap, @Nullable Registration registration) {
        var method = cap.methodName();
        var existingRegistration = currentRegistrations.get(method);

        if (registration == null) {
            if (existingRegistration != null) {
                // this capability was removed
                logger.trace("{} is no longer supported by contributions", method);
                return unregister(existingRegistration);
            }
            // nothing more to do
            return truthy;
        }

        if (existingRegistration != null) {
            if (Objects.deepEquals(registration.getRegisterOptions(), existingRegistration.getRegisterOptions())) {
                logger.trace("Options for {} did not change since last registration: {}", method, registration.getRegisterOptions());
                return truthy;
            }
            logger.trace("Options for {} changed since the previous registration; remove before adding again", method);
            return changeOptions(registration, existingRegistration);
        }

        logger.trace("Registering dynamic capability {}", method);
        return register(registration);
    }

    /**
     * Unregister this registration.
     * Aims to be atomic, i.e. keeps local administration of registered capabilities in sync with the client.
     * @param reg The registration to undo.
     * @return A future completing with `true` if successful, and `false` otherwise.
     */
    private CompletableFuture<Boolean> unregister(Registration reg) {
        // If our administration contains exactly this registration, remove it and inform the client
        if (!currentRegistrations.remove(reg.getMethod(), reg)) {
            return falsy;
        }

        return client.unregisterCapability(new UnregistrationParams(List.of(new Unregistration(reg.getId(), reg.getMethod()))))
            .handle((_v, t) -> {
                if (t == null) {
                    return true;
                }
                handleError(t, "unregistering", reg.getMethod());
                // Unregistration failed; put this back in our administration
                currentRegistrations.putIfAbsent(reg.getMethod(), reg);
                return false;
            });
    }

    /**
     * Register this registration.
     * Aims to be atomic, i.e. keeps local administration of registered capabilities in sync with the client.
     * @param reg The registration to do.
     * @return A future completing with `true` if successful, and `false` otherwise.
     */
    private CompletableFuture<Boolean> register(Registration reg) {
        // If our administration contains no registration, inform the client
        if (currentRegistrations.putIfAbsent(reg.getMethod(), reg) != null) {
            return falsy;
        }

        return client.registerCapability(new RegistrationParams(List.of(reg)))
            .handle((_v, t) -> {
                if (t == null) {
                    return true;
                }
                handleError(t, "registering", reg.getMethod());
                // Registration failed; remove this from our administration
                currentRegistrations.remove(reg.getMethod(), reg);
                return false;
            });
    }

    private void handleError(Throwable t, String task, String method) {
        if (t instanceof ResponseErrorException) {
            client.showMessage(new MessageParams(MessageType.Error, String.format("%s capability %s failed.", StringUtils.capitalize(task), method)));
        }
        logger.error("Exception while {} capability {}", task, method, t);
    }

    /**
     * Update a registration.
     * Aims to be atomic, i.e. keeps local administration of registered capabilities in sync with the client.
     * @param newRegistration The registration with the updated options.
     * @param existingRegistration The registration that we expect to currently be in place.
     * @return A future completing with `true` if successful, or `false` otherwise.
     */
    private CompletableFuture<Boolean> changeOptions(Registration newRegistration, Registration existingRegistration) {
        return unregister(existingRegistration)
            .thenCompose(b -> {
                if (!b.booleanValue()) {
                    /* If unregistration fails, this has one of multiple causes:
                        1. Someone raced us, won, and updated `currentRegistrations`. Our view of the current state is outdated, so we don't do anything and leave it to the winner.
                        2. The unregistration request failed. This happens when a capability is not supported by the client. Since we successfully registered, this should not happen.
                    */
                    return falsy;
                }
                return register(newRegistration);
            });
    }

    private <T> CompletableFuture<@Nullable Registration> tryBuildRegistration(AbstractDynamicCapability<T> cap, Collection<ILanguageContributions> contribs) {
        if (contribs.isEmpty()) {
            return CompletableFutureUtils.completedFuture(null, exec);
        }

        // Filter contributions by providing this capability
        return CompletableFutureUtils.<List<ILanguageContributions>>flatten(
            contribs.stream().map(c -> cap.isProvidedBy(c).thenApply(b -> b.booleanValue() ? List.of(c) : List.of())),
            CompletableFutureUtils.completedFuture(Collections.emptyList(), exec),
            Lists::union
        ).<@Nullable Registration>thenCompose(cs -> {
            if (cs.isEmpty()) {
                return CompletableFutureUtils.completedFuture(null, exec);
            }

            var allOpts = cs.stream()
                .<CompletableFuture<@Nullable T>>map(cap::options)
                .collect(Collectors.toList());
            return CompletableFutureUtils.reduce(allOpts, cap::mergeNullableOptions) // non-empty, so no need to provide a reduction identity
                .thenApply(opts -> new Registration(cap.id(), cap.methodName(), opts));
        });
    }

}
