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

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.checkerframework.checker.nullness.qual.NonNull;
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

    @Override
    protected String methodName() {
        return "textDocument/completion";
    }

    @Override
    protected CompletableFuture<CompletionRegistrationOptions> options(ILanguageContributions contribs) {
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
    protected CompletableFuture<Boolean> hasContribution(ILanguageContributions contribs) {
        return contribs.hasCompletion();
    }

    @Override
    protected CompletionRegistrationOptions mergeOptions(Object existingObj, Object newObj) {
        var newOpts = (CompletionRegistrationOptions) newObj;
        var existingOpts = (CompletionRegistrationOptions) existingObj;
        return new CompletionRegistrationOptions(union(existingOpts.getTriggerCharacters(), newOpts.getTriggerCharacters()), false);
    }

    private <T> List<@NonNull T> union(List<@NonNull T> left, List<@NonNull T> right) {
        if (right.isEmpty()) {
            return left;
        }
        if (left.isEmpty()) {
            return right;
        }
        List<@NonNull T> merged = new LinkedList<>(left);
        for (T t : right) {
            if (!left.contains(t)) {
                merged.add(t);
            }
        }

        return merged;
    }

    @Override
    protected boolean hasDynamicCapability(ClientCapabilities client) {
        return client.getTextDocument().getCompletion().getDynamicRegistration();
    }

    @Override
    protected void setStaticCapability(ServerCapabilities serverCapabilities) {
        serverCapabilities.setCompletionProvider(new CompletionOptions(false, List.of("")));
    }

}
