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
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.DynamicRegistrationCapabilities;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.rascalmpl.vscode.lsp.parametric.ILanguageContributions;

/**
 * Capability with contribution-independent options in the `textDocument/` namespace.
 */
public class TextDocumentCapabilityWithConstantOptions<O> extends TextDocumentCapability<O> {

    private final Supplier<O> options;
    private final Function<TextDocumentClientCapabilities, @Nullable DynamicRegistrationCapabilities> getClientCapabilities;
    private final Consumer<ServerCapabilities> setServerCapabilities;
    private final Function<ILanguageContributions, CompletableFuture<Boolean>> contributionsProvide;

    /**
     * @param capabilityName The name of the capability.
     * @param options The options object.
     * @param getClientCapabilities A function that retrieves the {@link DynamicRegistrationCapabilities} for the capability.
     * @param contributionsProvide A function that returns whether language contributions support this capability.
     * @param setServerCapabilities A function that sets this capability statically.
     */
    protected TextDocumentCapabilityWithConstantOptions(String capabilityName, Supplier<O> options, Function<TextDocumentClientCapabilities, @Nullable DynamicRegistrationCapabilities> getClientCapabilities, Function<ILanguageContributions, CompletableFuture<Boolean>> contributionsProvide, Consumer<ServerCapabilities> setServerCapabilities) {
        super(capabilityName);
        this.options = options;
        this.getClientCapabilities = getClientCapabilities;
        this.setServerCapabilities = setServerCapabilities;
        this.contributionsProvide = contributionsProvide;
    }

    @Override
    protected final CompletableFuture<Boolean> isProvidedBy(ICapabilityParams language) {
        return contributionsProvide.apply(language.contributions());
    }

    @Override
    protected final O mergeOptions(O o1, O o2) {
        return options.get(); // since options are constant, no need to merge here
    }

    @Override
    protected final void registerStatically(ServerCapabilities result) {
        setServerCapabilities.accept(result);
    }

    @Override
    protected final CompletableFuture<@Nullable O> options(ICapabilityParams language) {
        return CompletableFuture.completedFuture(options.get());
    }

    @Override
    protected final @Nullable DynamicRegistrationCapabilities getCapabilities(TextDocumentClientCapabilities caps) {
        return getClientCapabilities.apply(caps);
    }

    @Override
    public int hashCode() {
        return Objects.hash(options, getClientCapabilities, setServerCapabilities, contributionsProvide, super.hashCode());
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (!(obj instanceof TextDocumentCapabilityWithConstantOptions)) {
            return false;
        }
        var other = (TextDocumentCapabilityWithConstantOptions<?>) obj;
        return Objects.equals(options, other.options)
            && Objects.equals(getClientCapabilities, other.getClientCapabilities)
            && Objects.equals(setServerCapabilities, other.setServerCapabilities)
            && Objects.equals(contributionsProvide, other.contributionsProvide)
            && super.equals(other);
    }

}
