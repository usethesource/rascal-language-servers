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

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.ServerCapabilities;

public abstract class AbstractDynamicRegistration<Options> {

    private final String id;
    private final String name;
    private final boolean preferStaticRegistration;

    protected AbstractDynamicRegistration(String name, boolean preferStaticRegistration) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.preferStaticRegistration = preferStaticRegistration;
    }

    protected final String id() {
        return id;
    }

    protected final String name() {
        return name;
    }

    /**
     * Whether this registration prefers static registration.
     */
    protected final boolean preferStaticRegistration() {
        return preferStaticRegistration;
    }

    protected abstract CompletableFuture<@Nullable Registration> tryBuildRegistration(StableCapabilityParams params);

    /**
     * Merges to option objects.
     * @param existingOpts The current options.
     * @param newOpts The new options to merge into the current ones.
     * @return Merged options.
     */
    protected abstract Options mergeOptions(Options existingOpts, Options newOpts);

    protected final @Nullable Options mergeNullableOptions(@Nullable Options o1, @Nullable Options o2) {
        if (o1 == null) {
            return o2;
        }
        if (o2 == null) {
            return o1;
        }
        return mergeOptions(o1, o2);
    }

    /**
     * Check whether to register this capability statically instead of dynamically.
     *
     * If this capability prefers static registration or if the client does not support dynamic registration, set it statically.
     * @param client Client capabilities to determine dynamic registration support.
     * @param result Server capabilities to modify when registerting statically.
     * @return `true` if this capability should be registered statically, `false` otherwise.
     */
    protected final boolean shouldRegisterStatically(ClientCapabilities client) {
        return preferStaticRegistration() || !isDynamicallySupportedBy(client);
    }

    /**
     * Predicate that determines whether the client supports dynamic registration of this capability.
     * @param clientCapabilities The capabilities of the client.
     * @return `true` if it supports dynamic registration, `false` otherwise.
     */
    protected abstract boolean isDynamicallySupportedBy(ClientCapabilities clientCapabilities);

    /**
     * Registers this server capability statically.
     * @param result The server capabilities to modify.
     */
    protected abstract void registerStatically(StableCapabilityParams params, ServerCapabilities result);

    @Override
    public boolean equals(@Nullable Object obj) {
        if (!(obj instanceof AbstractDynamicRegistration)) {
            return false;
        }
        var other = (AbstractDynamicRegistration<?>) obj;
        return Objects.equals(id, other.id)
            && Objects.equals(name(), other.name());
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name());
    }

}
