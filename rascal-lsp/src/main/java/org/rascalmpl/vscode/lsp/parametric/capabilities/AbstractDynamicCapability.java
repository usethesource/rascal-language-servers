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

import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.ServerCapabilities;
import org.rascalmpl.vscode.lsp.parametric.ILanguageContributions;
import org.rascalmpl.vscode.lsp.util.concurrent.CompletableFutureUtils;

/**
 * Abstract superclass for implementations of dynamic capabilities.
 * @param O The type of the capability's options.
 */
public abstract class AbstractDynamicCapability<Options> extends AbstractDynamicRegistration<Options, ServerCapabilities> {

    /**
     * Computes the options this capability given language contributions.
     * @param contribs The {@link ILanguageContributions} that this capability reflects.
     * @return A future resolving to options
     */
    protected abstract CompletableFuture<@Nullable Options> options(ILanguageContributions contribs);

    /**
     * Checks whether the givien language contributions contain a contribution that matches this capability.
     * @param contribs The {@link ILanguageContributions} that this capability reflects.
     * @return A future resolving to `true` if there is such a contribution, or `false` otherwise.
     */
    protected abstract CompletableFuture<Boolean> hasContribution(ILanguageContributions contribs);

    protected final CompletableFuture<@Nullable Registration> registration(ICapabilityParams params) {
        var supportingContribs = params.contributions().stream().filter(c -> hasContribution(c).join()).collect(Collectors.toList()); // join() is fine, since we should only be called inside a promise
        if (supportingContribs.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        var allOpts = supportingContribs.stream()
            .<CompletableFuture<@Nullable Options>>map(this::options)
            .collect(Collectors.toList());
        var mergedOpts = CompletableFutureUtils.reduce(allOpts, this::mergeOptions); // non-empty, so fine
        return mergedOpts.thenApply(opts -> new Registration(id(), name(), opts));
    }

}
