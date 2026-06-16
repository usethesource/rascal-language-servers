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
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.SemanticTokensCapabilities;
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.rascalmpl.vscode.lsp.rascal.conversion.SemanticTokenizer;
import org.rascalmpl.vscode.lsp.util.Nullables;

public class SemanticTokensCapability extends AbstractDynamicCapability<SemanticTokensWithRegistrationOptions> {

    public SemanticTokensCapability() {
        super("textDocument/semanticTokens");
    }

    @Override
    protected CompletableFuture<@Nullable SemanticTokensWithRegistrationOptions> options(ICapabilityParams language) {
        return CompletableFuture.completedFuture(SemanticTokenizer.options());
    }

    @Override
    protected CompletableFuture<Boolean> isProvidedBy(ICapabilityParams params) {
        return CompletableFuture.completedFuture(true);
    }

    @Override
    protected SemanticTokensWithRegistrationOptions mergeOptions(SemanticTokensWithRegistrationOptions o1,
            SemanticTokensWithRegistrationOptions o2) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'mergeOptions'");
    }

    @Override
    protected boolean isDynamicallySupportedBy(ClientCapabilities clientCapabilities) {
        return Nullables.has(clientCapabilities.getTextDocument(), TextDocumentClientCapabilities::getSemanticTokens, SemanticTokensCapabilities::getDynamicRegistration);
    }

    @Override
    protected void registerStatically(ServerCapabilities capabilities) {
        capabilities.setSemanticTokensProvider(SemanticTokenizer.options());
    }

}


