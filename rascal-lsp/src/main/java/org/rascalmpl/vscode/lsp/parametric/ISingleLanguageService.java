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
package org.rascalmpl.vscode.lsp.parametric;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.CallHierarchyIncomingCall;
import org.eclipse.lsp4j.CallHierarchyIncomingCallsParams;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.eclipse.lsp4j.CallHierarchyOutgoingCall;
import org.eclipse.lsp4j.CallHierarchyOutgoingCallsParams;
import org.eclipse.lsp4j.CallHierarchyPrepareParams;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.ColorInformation;
import org.eclipse.lsp4j.ColorPresentation;
import org.eclipse.lsp4j.ColorPresentationParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.CreateFilesParams;
import org.eclipse.lsp4j.DeclarationParams;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.DeleteFilesParams;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentColorParams;
import org.eclipse.lsp4j.DocumentDiagnosticParams;
import org.eclipse.lsp4j.DocumentDiagnosticReport;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightParams;
import org.eclipse.lsp4j.DocumentLink;
import org.eclipse.lsp4j.DocumentLinkParams;
import org.eclipse.lsp4j.DocumentOnTypeFormattingParams;
import org.eclipse.lsp4j.DocumentRangeFormattingParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeRequestParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.ImplementationParams;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.InlineValue;
import org.eclipse.lsp4j.InlineValueParams;
import org.eclipse.lsp4j.LinkedEditingRangeParams;
import org.eclipse.lsp4j.LinkedEditingRanges;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Moniker;
import org.eclipse.lsp4j.MonikerParams;
import org.eclipse.lsp4j.PrepareRenameDefaultBehavior;
import org.eclipse.lsp4j.PrepareRenameParams;
import org.eclipse.lsp4j.PrepareRenameResult;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.RenameFilesParams;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.SelectionRange;
import org.eclipse.lsp4j.SelectionRangeParams;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensDelta;
import org.eclipse.lsp4j.SemanticTokensDeltaParams;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.SemanticTokensRangeParams;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureHelpParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.TypeDefinitionParams;
import org.eclipse.lsp4j.TypeHierarchyItem;
import org.eclipse.lsp4j.TypeHierarchyPrepareParams;
import org.eclipse.lsp4j.TypeHierarchySubtypesParams;
import org.eclipse.lsp4j.TypeHierarchySupertypesParams;
import org.eclipse.lsp4j.WillSaveTextDocumentParams;
import org.eclipse.lsp4j.WorkspaceDiagnosticParams;
import org.eclipse.lsp4j.WorkspaceDiagnosticReport;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.Either3;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

public interface ISingleLanguageService extends TextDocumentService, WorkspaceService {
    @Override CompletableFuture<List<CallHierarchyIncomingCall>> callHierarchyIncomingCalls(CallHierarchyIncomingCallsParams params);
    @Override CompletableFuture<List<CallHierarchyOutgoingCall>> callHierarchyOutgoingCalls(CallHierarchyOutgoingCallsParams params);
    @Override CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params);
    @Override CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params);
    @Override CompletableFuture<List<ColorPresentation>> colorPresentation(ColorPresentationParams params);
    @Override CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams position);
    @Override CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> declaration(DeclarationParams params);
    @Override CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(DefinitionParams params);
    @Override CompletableFuture<DocumentDiagnosticReport> diagnostic(DocumentDiagnosticParams params);
    @Override void didChange(DidChangeTextDocumentParams params);
    @Override void didClose(DidCloseTextDocumentParams params);
    @Override void didOpen(DidOpenTextDocumentParams params);
    @Override void didSave(DidSaveTextDocumentParams params);
    @Override CompletableFuture<List<ColorInformation>> documentColor(DocumentColorParams params);
    @Override CompletableFuture<List<? extends DocumentHighlight>> documentHighlight(DocumentHighlightParams params);
    @Override CompletableFuture<List<DocumentLink>> documentLink(DocumentLinkParams params);
    @Override CompletableFuture<DocumentLink> documentLinkResolve(DocumentLink params);
    @Override CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(DocumentSymbolParams params);
    @Override CompletableFuture<List<FoldingRange>> foldingRange(FoldingRangeRequestParams params);
    @Override CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params);
    @Override CompletableFuture<Hover> hover(HoverParams params);
    @Override CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> implementation(ImplementationParams params);
    @Override CompletableFuture<List<InlayHint>> inlayHint(InlayHintParams params);
    @Override CompletableFuture<List<InlineValue>> inlineValue(InlineValueParams params);
    @Override CompletableFuture<LinkedEditingRanges> linkedEditingRange(LinkedEditingRangeParams params);
    @Override CompletableFuture<List<Moniker>> moniker(MonikerParams params);
    @Override CompletableFuture<List<? extends TextEdit>> onTypeFormatting(DocumentOnTypeFormattingParams params);
    @Override CompletableFuture<List<CallHierarchyItem>> prepareCallHierarchy(CallHierarchyPrepareParams params);
    @Override CompletableFuture<Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>> prepareRename(PrepareRenameParams params);
    @Override CompletableFuture<List<TypeHierarchyItem>> prepareTypeHierarchy(TypeHierarchyPrepareParams params);
    @Override CompletableFuture<List<? extends TextEdit>> rangeFormatting(DocumentRangeFormattingParams params);
    @Override CompletableFuture<List<? extends Location>> references(ReferenceParams params);
    @Override CompletableFuture<WorkspaceEdit> rename(RenameParams params);
    @Override CompletableFuture<CodeAction> resolveCodeAction(CodeAction unresolved);
    @Override CompletableFuture<CodeLens> resolveCodeLens(CodeLens unresolved);
    @Override CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem unresolved);
    @Override CompletableFuture<InlayHint> resolveInlayHint(InlayHint unresolved);
    @Override CompletableFuture<List<SelectionRange>> selectionRange(SelectionRangeParams params);
    @Override CompletableFuture<SemanticTokens> semanticTokensFull(SemanticTokensParams params);
    @Override CompletableFuture<Either<SemanticTokens, SemanticTokensDelta>> semanticTokensFullDelta(SemanticTokensDeltaParams params);
    @Override CompletableFuture<SemanticTokens> semanticTokensRange(SemanticTokensRangeParams params);
    @Override CompletableFuture<SignatureHelp> signatureHelp(SignatureHelpParams params);
    @Override CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> typeDefinition(TypeDefinitionParams params);
    @Override CompletableFuture<List<TypeHierarchyItem>> typeHierarchySubtypes(TypeHierarchySubtypesParams params);
    @Override CompletableFuture<List<TypeHierarchyItem>> typeHierarchySupertypes(TypeHierarchySupertypesParams params);
    @Override void willSave(WillSaveTextDocumentParams params);
    @Override CompletableFuture<List<TextEdit>> willSaveWaitUntil(WillSaveTextDocumentParams params);
    @Override CompletableFuture<WorkspaceDiagnosticReport> diagnostic(WorkspaceDiagnosticParams params);
    @Override void didChangeConfiguration(DidChangeConfigurationParams params);
    @Override void didChangeWatchedFiles(DidChangeWatchedFilesParams params);
    @Override void didChangeWorkspaceFolders(DidChangeWorkspaceFoldersParams params);
    @Override void didCreateFiles(CreateFilesParams params);
    @Override void didDeleteFiles(DeleteFilesParams params);
    @Override void didRenameFiles(RenameFilesParams params);
    @Override CompletableFuture<Object> executeCommand(ExecuteCommandParams params);
    @Override CompletableFuture<WorkspaceSymbol> resolveWorkspaceSymbol(WorkspaceSymbol workspaceSymbol);
    @Override CompletableFuture<Either<List<? extends SymbolInformation>, List<? extends WorkspaceSymbol>>> symbol(WorkspaceSymbolParams params);
    @Override CompletableFuture<WorkspaceEdit> willCreateFiles(CreateFilesParams params);
    @Override CompletableFuture<WorkspaceEdit> willDeleteFiles(DeleteFilesParams params);
    @Override CompletableFuture<WorkspaceEdit> willRenameFiles(RenameFilesParams params);
}
