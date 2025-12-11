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

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.ServerCapabilities;
import org.rascalmpl.vscode.lsp.parametric.ILanguageContributions;

/**
 * Abstract superclass for implementations of dynamic capabilities.
 * @param O The type of the capability's options.
 */
public abstract class AbstractDynamicCapability<O> {

    /**
     * A unique UUID used to register/unregister this capability. Should be unique.
     * @see https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#registrationParams
     */
    private final String id;

    /**
     * The name of the capability (as per LSP documentation). For example, `"textDocument/completion"`.
     * @see https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#registrationParams
     */
    private final String methodName;

    /**
     * Whether to prefer static registration (over dynamic).
     */
    private final boolean preferStaticRegistration;

    protected AbstractDynamicCapability(String methodName) {
        this(methodName, false);
    }

    protected AbstractDynamicCapability(String methodName, boolean preferStaticRegistration) {
        this.id = UUID.randomUUID().toString();
        this.methodName = methodName;
        this.preferStaticRegistration = preferStaticRegistration;
    }

    protected final String id() {
        return id;
    }

    protected final String methodName() {
        return methodName;
    }

    protected final boolean preferStaticRegistration() {
        return this.preferStaticRegistration;
    }

    /**
     * Computes the options this capability given language contributions.
     * @param contribs The {@link ILanguageContributions} that this capability reflects.
     * @return A future resolving to options
     */
    protected abstract CompletableFuture<@Nullable O> options(ILanguageContributions contribs);

    /**
     * Checks whether the givien language contributions contain a contribution that matches this capability.
     * @param contribs The {@link ILanguageContributions} that this capability reflects.
     * @return A future resolving to `true` if there is such a contribution, or `false` otherwise.
     */
    protected abstract CompletableFuture<Boolean> isProvidedBy(ILanguageContributions contribs);

    /**
     * Merges to option objects.
     * @param existingOpts The current options.
     * @param newOpts The new options to merge into the current ones.
     * @return Merged options.
     */
    protected abstract @Nullable O mergeOptions(@Nullable O existingOpts, @Nullable O newOpts);


    /**
     * Predicate that determines whether the client supports dynamic registration of this capability.
     * @param clientCapabilities The capabilities of the client.
     * @return `true` if it supports dynamic registration, `false` otherwise.
     */
    protected abstract boolean isDynamicallySupportedBy(ClientCapabilities clientCapabilities);

    /**
     * Sets this capability statically.
     * @param result The server capabilities to set.
     */
    protected abstract void registerStatically(ServerCapabilities result);

    /**
     * Check whether to set this capability dynamically.
     *
     * If this capability prefers static registration or the client does not support dynamic registration, set it statically instead.
     * @param client Client capabilities to determine dynamic registration support.
     * @param result Server capabilities to modify when registerting statically.
     * @return `true` if this capability should be registered dynamically, `false` otherwise.
     */
    protected final boolean shouldRegisterStatically(ClientCapabilities client) {
        return preferStaticRegistration() || !isDynamicallySupportedBy(client);
    }

}
