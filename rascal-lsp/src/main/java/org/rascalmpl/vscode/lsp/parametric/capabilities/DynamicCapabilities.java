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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
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
import org.rascalmpl.vscode.lsp.util.concurrent.CompletableFutureUtils;

/**
 * Takes care of (un)registering capabilities dynamically.
 * Receives a list of supported capabilities ({@link AbstractDynamicCapability}) and notifies the client when capabilities change.
 */
public class DynamicCapabilities {

    private static final Logger logger = LogManager.getLogger(DynamicCapabilities.class);

    private final LanguageClient client;
    private final Executor exec;
    private final CompletableFuture<Void> noop;

    private final Set<AbstractDynamicCapability<?>> dynamicCapabilities;
    private final Set<AbstractDynamicCapability<?>> staticCapabilities;

    private final AtomicReference<Collection<ILanguageContributions>> lastContributions = new AtomicReference<>(Collections.emptyList());
    // Map of method names with current registration values
    private final Map<String, Registration> currentRegistrations = new ConcurrentHashMap<>();

    /**
     * @param client The language client to send register/unregister requests to.
     * @param supportedCapabilities The capabilities to register with the client.
     * @param clientCapabilities The capabilities of the client. Determine whether dynamic registration is supported at all.
     */
    public DynamicCapabilities(LanguageClient client, Executor exec, Set<AbstractDynamicCapability<?>> supportedCapabilities, ClientCapabilities clientCapabilities) {
        this.client = client;
        this.exec = exec;
        this.noop = CompletableFutureUtils.completedFuture(null, exec);

        // Check whether to register capabilities dynamically or statically
        var dynamicCaps = new HashSet<AbstractDynamicCapability<?>>();
        var staticCaps = new HashSet<AbstractDynamicCapability<?>>();
        for (var cap : supportedCapabilities) {
            if (cap.shouldRegisterStatically(clientCapabilities)) {
                logger.trace("Skipping capability {} for dynamic registration", cap.methodName());
                staticCaps.add(cap);
            } else {
                dynamicCaps.add(cap);
            }
        }
        // Set once and only read from now on
        this.dynamicCapabilities = Collections.unmodifiableSet(dynamicCaps);
        this.staticCapabilities = Collections.unmodifiableSet(staticCaps);
    }

    /**
     * Register static capabilities with the server.
     */
    public void registerStaticCapabilities(ServerCapabilities result) {
        for (var cap : staticCapabilities) {
            logger.trace("Registering capability {} statically", cap.methodName());
            cap.registerStatically(result);
        }
    }

    /**
     * Update capabilities for language contributions.
     * @param contribs The contributions to represent.
     * @return A future that completes with a boolean that is false when any registration failed, and true otherwise.
     */
    public CompletableFuture<Void> update(Collection<ILanguageContributions> contribs) {
        return CompletableFuture.supplyAsync(() -> {
            logger.debug("Updating {} dynamic capabilities from {} contributions", dynamicCapabilities.size(), contribs.size());
            // Copy the contributions so we know we are looking at a stable set of elements.
            return lastContributions.updateAndGet(_old -> Set.copyOf(contribs));
        }, exec)
        .thenCompose(stableContribs -> CompletableFutureUtils.reduce(dynamicCapabilities.stream().map(cap -> updateRegistration(cap, stableContribs)), exec))
        .thenAccept(_v -> logger.debug("Done updating dynamic capabilities"));
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
    private <T> CompletableFuture<Void> updateRegistration(AbstractDynamicCapability<T> cap, Collection<ILanguageContributions> contribs) {
        var reg = tryBuildRegistration(cap, contribs);
        var method = cap.methodName();
        var existingRegistration = currentRegistrations.get(method);

        return reg.thenCompose(registration -> {
            Function<Boolean, CompletableFuture<Void>> checkDone = successful -> successful.booleanValue()
                ? eventuallyConsistent(cap, registration, contribs)
                : noop;

            if (registration == null) {
                if (existingRegistration != null) {
                    // this capability was removed
                    logger.trace("{} is no longer supported by contributions", method);
                    return unregister(existingRegistration).thenCompose(checkDone);
                }
                logger.trace("{} is still not supported by contributions", method);
                // nothing more to do
                return eventuallyConsistent(cap, registration, contribs);
            }

            if (existingRegistration != null) {
                if (Objects.deepEquals(registration.getRegisterOptions(), existingRegistration.getRegisterOptions())) {
                    logger.trace("Options for {} did not change since last registration", method);
                    return eventuallyConsistent(cap, registration, contribs);
                }
                logger.trace("Options for {} changed since the previous registration ({} vs. {})", method, registration.getRegisterOptions(), existingRegistration.getRegisterOptions());
                return register(registration, existingRegistration).thenCompose(checkDone);
            }

            logger.trace("Registering dynamic capability {}", registration);
            return register(registration, existingRegistration).thenCompose(checkDone);
        });
    }

    /**
     * Ensure that a registration is eventually consistent.
     *
     * Check whether the inputs for computing this registration did not change while updating the registration.
     * If they did not change, this registration arrived at a fixpoint and is done.
     * Otherwise, this registration is updated again, based on the new inputs (contributions and previously registered registration).
     * @param <T> The type of this capability's options.
     * @param cap The capability for which the registration is being updated.
     * @param r The registration that was computed.
     * @param contribs The contributions that were used for registration computation.
     * @return A future that completes when the registration is at a fixpoint.
     */
    private <T> CompletableFuture<Void> eventuallyConsistent(AbstractDynamicCapability<T> cap, @Nullable Registration r, Collection<ILanguageContributions> contribs) {
        var currentContribs = lastContributions.get();
        var currentReg = currentRegistrations.get(cap.methodName());
        if (contribs == currentContribs // instance comparison is safe since we took a read-only copy
            && Objects.equals(currentReg, r)) {
            // Nothing changed; we're done
            return noop;
        }

        // Something changed since we started...
        // To be sure we arrive at a fixpoint, we need to go again
        logger.trace("Something changed while registering {}; iterate until nothing changes", cap.methodName());
        return updateRegistration(cap, currentContribs);
    }

    /**
     * Unregister this registration.
     * Aims to be atomic, i.e. keeps local administration of registered capabilities in sync with the client.
     * @param reg The registration to undo.
     * @return A future completing with `true` if successful, and `false` otherwise.
     */
    private CompletableFuture<Boolean> unregister(Registration reg) {
        return client.unregisterCapability(new UnregistrationParams(List.of(new Unregistration(reg.getId(), reg.getMethod()))))
            .handle((_v, t) -> {
                if (t != null) {
                    handleError(t, "unregistering", reg.getMethod());
                    return false;
                }
                currentRegistrations.remove(reg.getMethod(), reg);
                return true;
            });
    }

    /**
     * Register this registration.
     * Aims to be atomic, i.e. keeps local administration of registered capabilities in sync with the client.
     * @param reg The registration to do.
     * @return A future completing with `true` if successful, and `false` otherwise.
     */
    private CompletableFuture<Boolean> register(Registration reg, @Nullable Registration existingRegistration) {
        return client.registerCapability(new RegistrationParams(List.of(reg)))
            .handle((_v, t) -> {
                if (t != null) {
                    handleError(t, "registering", reg.getMethod());
                    return false;
                }
                currentRegistrations.compute(reg.getMethod(), (k, v) -> Objects.equals(v, existingRegistration) ? reg : v);
                return true;
            });
    }

    private void handleError(Throwable t, String task, String method) {
        if (t instanceof ResponseErrorException) {
            client.showMessage(new MessageParams(MessageType.Error, String.format("%s capability %s failed.", StringUtils.capitalize(task), method)));
        }
        logger.error("Exception while {} capability {}", task, method, t);
    }

    private <T> CompletableFuture<@Nullable Registration> tryBuildRegistration(AbstractDynamicCapability<T> cap, Collection<ILanguageContributions> contribs) {
        if (contribs.isEmpty()) {
            return CompletableFutureUtils.completedFuture(null, exec);
        }

        // Filter contributions by providing this capability
        return CompletableFutureUtils.filter(contribs, cap::isProvidedBy).<@Nullable Registration>thenCompose(cs -> {
            if (cs.isEmpty()) {
                return CompletableFutureUtils.completedFuture(null, exec);
            }

            var allOpts = cs.stream().<CompletableFuture<@Nullable T>>map(cap::options).collect(Collectors.toList());
            return CompletableFutureUtils.reduce(allOpts, cap::mergeNullableOptions) // non-empty, so no need to provide a reduction identity
                .thenApply(opts -> new Registration(cap.id(), cap.methodName(), opts));
        });
    }

}
