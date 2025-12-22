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

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.Registration;
import org.rascalmpl.vscode.lsp.parametric.ILanguageContributions;
import org.rascalmpl.vscode.lsp.util.Lists;
import org.rascalmpl.vscode.lsp.util.concurrent.CompletableFutureUtils;

/**
 * Superclass of dynamic capabilities.
 *
 * Dynamic capabilities are LSP server capabilities that can be registered/unregistered dynamically, during the lifetime of the server, with the client.
 * This is in contrast to 'static' capabilities that can only be registered at initialization.
 * This class provides a common interface and implementation for dynamic capabilities.
 *
 * @see https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#client_registerCapability
 * @param Options The type of the capability's options.
 */
public abstract class AbstractDynamicCapability<Options> extends AbstractDynamicRegistration<Options> {

    private final Executor exec;

    protected AbstractDynamicCapability(String name, boolean preferStaticRegistration, Executor exec) {
        super(name, preferStaticRegistration);
        this.exec = exec;
    }

    /**
     * Computes the options for this capability, which might be `null`.
     * Only called when {@link isProvidedBy} resolves to `true` (for the same contributions).
     * @param contribs The {@link ILanguageContributions} that this capability reflects.
     * @return A future resolving to the options.
     */
    protected abstract CompletableFuture<@Nullable Options> options(ILanguageContributions contribs);

    /**
     * Checks whether the given language contributions contain a contribution that provides this capability.
     * @param contribs The {@link ILanguageContributions} that this capability reflects.
     * @return A future resolving to `true` if there is such a contribution, or `false` otherwise.
     */
    protected abstract CompletableFuture<Boolean> isProvidedBy(ILanguageContributions contribs);

    @Override
    protected final CompletableFuture<@Nullable Registration> tryBuildRegistration(StableCapabilityParams params) {
        // Copy the contributions so we know we are looking at a stable set of elements.
        // If the contributions change, we expect our caller to call again.
        var contribs = params.contributions();

        if (contribs.isEmpty()) {
            return CompletableFutureUtils.completedFuture(null, exec);
        }

        // Filter contributions by providing this capability
        return CompletableFutureUtils.<List<ILanguageContributions>>flatten(
            contribs.stream().map(c -> isProvidedBy(c).thenApply(b -> b.booleanValue() ? List.of(c) : List.of())),
            CompletableFutureUtils.completedFuture(Collections.emptyList(), exec),
            Lists::union
        ).<@Nullable Registration>thenCompose(cs -> {
            if (cs.isEmpty()) {
                return CompletableFutureUtils.completedFuture(null, exec);
            }

            var allOpts = cs.stream()
                .<CompletableFuture<@Nullable Options>>map(this::options)
                .collect(Collectors.toList());
            return CompletableFutureUtils.reduce(allOpts, this::mergeNullableOptions) // non-empty, so no need to provide a reduction identity
                .thenApply(opts -> new Registration(id(), name(), opts));
        });
    }

}
