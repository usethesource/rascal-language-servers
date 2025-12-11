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
import java.util.stream.Stream;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.CompletionRegistrationOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.rascalmpl.vscode.lsp.parametric.ILanguageContributions;

import io.usethesource.vallang.IString;

/**
 * Dynamic completion capability.
 *
 * @see https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_completion
 */
public class CompletionCapability extends AbstractDynamicCapability<CompletionRegistrationOptions> {

    public CompletionCapability() {
        this(false);
    }

    public CompletionCapability(boolean preferStaticRegistration) {
        super("textDocument/completion", preferStaticRegistration);
    }

    @Override
    protected CompletableFuture<@Nullable CompletionRegistrationOptions> options(ILanguageContributions contribs) {
        return contribs.completionTriggerCharacters()
            .thenApply(triggers -> {
                var trigList = triggers.stream()
                    .map(IString.class::cast)
                    .map(IString::getValue)
                    .collect(Collectors.toList());
                return new CompletionRegistrationOptions(trigList, false);
            });
    }

    @Override
    protected CompletableFuture<Boolean> isProvidedBy(ILanguageContributions contribs) {
        return contribs.hasCompletion();
    }

    @Override
    protected CompletionRegistrationOptions mergeOptions(CompletionRegistrationOptions left, CompletionRegistrationOptions right) {
        return new CompletionRegistrationOptions(Stream.concat(left.getTriggerCharacters().stream(), right.getTriggerCharacters().stream()).distinct().collect(Collectors.toList()), false);
    }

    @Override
    protected boolean isDynamicallySupportedBy(ClientCapabilities client) {
        return client.getTextDocument().getCompletion().getDynamicRegistration();
    }

    @Override
    protected void registerStatically(ServerCapabilities serverCapabilities) {
        serverCapabilities.setCompletionProvider(new CompletionOptions());
    }

}
