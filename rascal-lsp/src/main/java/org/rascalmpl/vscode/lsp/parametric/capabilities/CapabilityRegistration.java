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
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
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
public class CapabilityRegistration {

    private static final Logger logger = LogManager.getLogger(CapabilityRegistration.class);

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
    public CapabilityRegistration(LanguageClient client, Executor exec, Set<AbstractDynamicCapability<?>> supportedCapabilities, ClientCapabilities clientCapabilities) {
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
        logger.debug("Updating {} dynamic capabilities from {} contributions", dynamicCapabilities.size(), contribs.size());
        // Copy the contributions so we know we are looking at a stable collection of elements.

        /*
            *VERY IMPORTANT* Setting the atomic reference from the thread that called us.
            We need to be sure that the `lastContributions` reference actually points to the most recently known contributions.
            Therefore, we need to set this reference before delegating any work to futures, where we lose guaranteed execution order.
            Additionally, this function should be called from a thread pool with predictable execution order.
        */
        lastContributions.set(List.copyOf(contribs));
        return CompletableFutureUtils.reduce(dynamicCapabilities.stream().map(this::updateRegistration), exec)
            .thenAccept(_v -> logger.debug("Done updating dynamic capabilities"));
    }

    /**
     * Update the registration of a capability (lock-free).
     *
     * 1. Get the current contributions.
     * 2. Compute the registration based on the contributions.
     * 3. Check if the contributions did not change in the meantime. If they did, restart.
     * 4. Lock on the local administration of current registrations.
     * 5. Compute what to do to bring the registration up-to-date with the last contributions.
     *    - If the capability is not yet registered, register it.
     *    - If the capability was already registered, and the options changed, register it again.
     *    - If the capability was already registered, but not supported anymore, unregister it.
     *    - In any other case, do nothing.
     * 6. Ensure eventual consistency.
     *    Another incoming update request might change the contributions or overwrite the registration from a different
     *    thread while this computation is busy. That makes any work done here outdated. Before returning, check the
     *    following post-conditions:
     *    - The contributions are still the same contributions that the update was based on.
     *    - The computed registration update persisted (in the local administration).
     *    If any of the post-conditions is false, the update for this capability needs to be re-computed,
     *    to eventually arrive at a consistent state. Therefore, recursively start from step 1).
     * 7. Done.
     * @param cap The capability to update.
     * @param registration The computed registration to do, or `null` when this capability is absent.
     * @return A future completing with `true` when successful, or `false` otherwise.
     */
    private <T> CompletableFuture<Void> updateRegistration(AbstractDynamicCapability<T> cap) {
        var contribs = lastContributions.get();
        var method = cap.methodName();

        return tryBuildRegistration(cap, contribs).thenCompose(registration -> {
            // Synchronize on `currentRegistrations`, so we can reliable compute the required registration
            // and update the current registration without interference from other threads.
            synchronized (currentRegistrations) {
                // If someone else modified the contributions in the meantime, we need to restart.
                // Since we took a read-only copy of the contributions, instance comparison can be used everywhere
                if (lastContributions.get() != contribs) {
                    return updateRegistration(cap);
                }

                // Get the remaining inputs for our computation.
                var existingRegistration = currentRegistrations.get(method);

                final CompletableFuture<Void> result;
                if (registration == null) {
                    if (existingRegistration == null) {
                        logger.trace("Capability {} is still not supported by contributions", method);
                        result = noop;
                    } else {
                        logger.trace("Capability {} is no longer supported by contributions", method);
                        result = unregister(existingRegistration);
                    }
                } else if (existingRegistration != null) { // registration != null
                    if (Objects.deepEquals(registration.getRegisterOptions(), existingRegistration.getRegisterOptions())) {
                        logger.trace("Options for capability {} did not change since last registration", method);
                        result = noop;
                    } else {
                        logger.trace("Options for capability {} changed since the previous registration ({} vs. {})", method, registration.getRegisterOptions(), existingRegistration.getRegisterOptions());
                        // Unregister the existing registration before registering the updated one to prevent duplicate contributions
                        result = unregister(existingRegistration).thenCompose(_v -> register(registration, null));
                    }
                } else { // registration != null && existingRegistration == null
                    logger.trace("Capability {} is now supported", method);
                    result = register(registration, existingRegistration);
                }

                return result.handle((_v, t) -> {
                    if (t != null) {
                        // An error occurred. Inform the user and do not recurse.
                        handleError(t, cap);
                        return noop;
                    }

                    // Ensure that the registration is eventually consistent.
                    if (Objects.equals(currentRegistrations.get(method), registration) && lastContributions.get() == contribs) {
                        // Our update persisted and the contributions did not change in the meantime. Success!
                        return noop;
                    }

                    // Something changed while we were busy.
                    // Update this registration again, in order to reach a consistent state eventually.
                    logger.trace("Something changed while registering {}; iterate until nothing changes", cap.methodName());
                    return updateRegistration(cap);
                }).thenCompose(Function.identity());
            }
        });
    }

    /**
     * Unregister this registration.
     * First, we check if the local administration (`currentRegistrations`) contains the expected registration and atomically remove it.
     * If this does not succeed, we return to our caller, which should handle this.
     * which should check the administration after we are done, and possibly re-compute the registrations to do.
     * @param reg The registration to undo.
     * @return A future completing with `true` if successful, and `false` otherwise.
     */
    private CompletableFuture<Void> unregister(Registration reg) {
        var method = reg.getMethod();
        var removed = currentRegistrations.remove(method, reg);
        if (!removed) {
            // We lost a race. Someone else claimed this registration already. Do nothing, and let out caller handle the rest.
            return noop;
        }

        logger.debug("Unregistering capability {}", reg.getMethod());
        return client.unregisterCapability(new UnregistrationParams(List.of(new Unregistration(reg.getId(), method))))
            .whenComplete((_v, t) -> {
                if (t != null) {
                    // Unregistration failed, probably because this capability is not supported by the client.
                    // We attempt to revert the registration state to how it was at the start of this function.
                    currentRegistrations.putIfAbsent(method, reg);
                }
            });
    }

    /**
     * Register this registration.
     * @param reg The registration to do.
     * @return A future completing with `true` if successful, and `false` otherwise.
     */
    private CompletableFuture<Void> register(Registration reg, @Nullable Registration existingRegistration) {
        var method = reg.getMethod();
        var updatedReg = currentRegistrations.compute(method, (k, v) -> Objects.equals(v, existingRegistration) ? reg : v); // atomically replace, but allow null value
        if (updatedReg != reg) {
            // We lost a race. Someone else claimed this registration already. Do nothing, and let out caller handle the rest.
            return noop;
        }

        logger.debug("Registering capability {}", reg.getMethod());
        return client.registerCapability(new RegistrationParams(List.of(reg)))
            .whenComplete((_v, t) -> {
                if (t != null) {
                    // Registration failed, probably because this capability is not supported by the client.
                    // We attempt to revert the registration state to how it was at the start of this function.
                    currentRegistrations.compute(method, (k, v) -> Objects.equals(v, reg) ? existingRegistration : v); // atomically replace, or remove when `existingRegistration == null`
                }
            });
    }

    private <T> void handleError(@Nullable Throwable t, AbstractDynamicCapability<T> cap) {
        if (t instanceof CompletionException) {
            t = t.getCause();
        }
        if (t instanceof ResponseErrorException) {
            var message = t.getMessage() != null
                ? t.getMessage()
                : String.format("(Un)registration of capability %s failed", cap.methodName());
            client.showMessage(new MessageParams(MessageType.Error, message));
        }
        logger.error("Unexpected error while (un)registering capability {}", cap.methodName(), t);
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
