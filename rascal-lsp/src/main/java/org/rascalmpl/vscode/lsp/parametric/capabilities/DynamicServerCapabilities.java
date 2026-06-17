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

import org.eclipse.lsp4j.CallHierarchyRegistrationOptions;
import org.eclipse.lsp4j.CodeActionRegistrationOptions;
import org.eclipse.lsp4j.CodeLensOptions;
import org.eclipse.lsp4j.CodeLensRegistrationOptions;
import org.eclipse.lsp4j.DefinitionRegistrationOptions;
import org.eclipse.lsp4j.DocumentSymbolRegistrationOptions;
import org.eclipse.lsp4j.FoldingRangeProviderOptions;
import org.eclipse.lsp4j.HoverRegistrationOptions;
import org.eclipse.lsp4j.ImplementationRegistrationOptions;
import org.eclipse.lsp4j.InlayHintRegistrationOptions;
import org.eclipse.lsp4j.ReferenceRegistrationOptions;
import org.eclipse.lsp4j.RenameOptions;
import org.eclipse.lsp4j.SelectionRangeRegistrationOptions;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.rascalmpl.vscode.lsp.parametric.ILanguageContributions;
import org.rascalmpl.vscode.lsp.rascal.conversion.SemanticTokenizer;

/**
 * Lists dynamic capabilities of a language server.
 */
public class DynamicServerCapabilities {

    private DynamicServerCapabilities() { /* hide implicit constructor */ }

    /*package*/ public static AbstractDynamicCapability<?>[] parametric(String rascalMetaCommandName) {
        return new AbstractDynamicCapability<?>[] {
            // Text document capabilities
            new TextDocumentCapabilityWithConstantOptions<>("prepareCallHierarchy",
                CallHierarchyRegistrationOptions::new,
                TextDocumentClientCapabilities::getCallHierarchy,
                ILanguageContributions::providesCallHierarchy,
                c -> c.setCallHierarchyProvider(true)
            ),
            new TextDocumentCapabilityWithConstantOptions<>("codeAction",
                CodeActionRegistrationOptions::new,
                TextDocumentClientCapabilities::getCodeAction,
                ILanguageContributions::providesCodeAction,
                c -> c.setCodeActionProvider(true)
            ),
            new TextDocumentCapabilityWithConstantOptions<>("codeLens",
                CodeLensRegistrationOptions::new,
                TextDocumentClientCapabilities::getCodeLens,
                ILanguageContributions::providesCodeLens,
                c -> c.setCodeLensProvider(new CodeLensOptions(false))
            ),
            new CompletionCapability(),
            new TextDocumentCapabilityWithConstantOptions<>("definition",
                DefinitionRegistrationOptions::new,
                TextDocumentClientCapabilities::getDefinition,
                ILanguageContributions::providesDefinition,
                c -> c.setDefinitionProvider(true)
            ),
            new TextDocumentCapabilityWithConstantOptions<>("documentSymbol",
                DocumentSymbolRegistrationOptions::new,
                TextDocumentClientCapabilities::getDocumentSymbol,
                ILanguageContributions::providesDocumentSymbol,
                c -> c.setDocumentSymbolProvider(true)
            ),
            new TextDocumentCapabilityWithConstantOptions<>("foldingRange",
                FoldingRangeProviderOptions::new,
                TextDocumentClientCapabilities::getFoldingRange,
                c -> CompletableFuture.completedFuture(true), // contributions always provide a parser, and thus folding ranges
                c -> c.setFoldingRangeProvider(true)
            ),
            new TextDocumentCapabilityWithConstantOptions<>("hover",
                HoverRegistrationOptions::new,
                TextDocumentClientCapabilities::getHover,
                ILanguageContributions::providesHover,
                c -> c.setHoverProvider(true)
            ),
            new TextDocumentCapabilityWithConstantOptions<>("implementation",
                ImplementationRegistrationOptions::new,
                TextDocumentClientCapabilities::getImplementation,
                ILanguageContributions::providesImplementation,
                c -> c.setImplementationProvider(true)
            ),
            new TextDocumentCapabilityWithConstantOptions<>("inlayHint",
                InlayHintRegistrationOptions::new,
                TextDocumentClientCapabilities::getInlayHint,
                ILanguageContributions::providesInlayHint,
                c -> c.setInlayHintProvider(true)
            ),
            new TextDocumentCapabilityWithConstantOptions<>("references",
                ReferenceRegistrationOptions::new,
                TextDocumentClientCapabilities::getReferences,
                ILanguageContributions::providesReferences,
                c -> c.setReferencesProvider(true)
            ),
            new TextDocumentCapabilityWithConstantOptions<>("rename",
                () -> new RenameOptions(true),
                TextDocumentClientCapabilities::getRename,
                ILanguageContributions::providesRename,
                c -> c.setRenameProvider(new RenameOptions(true))
            ),
            new TextDocumentCapabilityWithConstantOptions<>("selectionRange",
                SelectionRangeRegistrationOptions::new,
                TextDocumentClientCapabilities::getSelectionRange,
                ILanguageContributions::providesSelectionRange,
                c -> c.setSelectionRangeProvider(true)
            ),
            new TextDocumentCapabilityWithConstantOptions<>("semanticTokens",
                SemanticTokenizer::options,
                TextDocumentClientCapabilities::getSemanticTokens,
                c -> CompletableFuture.completedFuture(true),
                c -> c.setSemanticTokensProvider(SemanticTokenizer.options())
            ),

            // Workspace capabilities
            new ExecuteCommandCapability(rascalMetaCommandName),
            /* new FileOperationCapability.DidCreateFiles(), */
            new FileOperationCapability.DidRenameFiles(),
            new FileOperationCapability.DidDeleteFiles()
        };
    }

}
