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
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.CreateFilesParams;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.DeleteFilesParams;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentFormattingParams;
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
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
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
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.Either3;
import org.rascalmpl.vscode.lsp.parametric.LanguageRegistry.LanguageParameter;
import org.rascalmpl.vscode.lsp.util.NamedThreadPool;

public class SingleLanguageServer implements ISingleLanguageService {

    private final ParametricTextDocumentService docService;
    private final ParametricWorkspaceService wsService;

    /*package*/ SingleLanguageServer(String langName) {
        var exec = NamedThreadPool.cached(langName);
        this.docService = new ParametricTextDocumentService(exec);
        this.wsService = new ParametricWorkspaceService(exec);
        this.docService.pair(wsService);
        this.wsService.pair(docService);
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        docService.didOpen(params);
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        docService.didChange(params);
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        docService.didClose(params);
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        docService.didSave(params);
    }

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams params) {
        wsService.didChangeConfiguration(params);
    }

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
        wsService.didChangeWatchedFiles(params);
    }

    @Override
    public CompletableFuture<List<CallHierarchyIncomingCall>> callHierarchyIncomingCalls(
            CallHierarchyIncomingCallsParams params) {
        return docService.callHierarchyIncomingCalls(params);
    }

    @Override
    public CompletableFuture<List<CallHierarchyOutgoingCall>> callHierarchyOutgoingCalls(
            CallHierarchyOutgoingCallsParams params) {
        return docService.callHierarchyOutgoingCalls(params);
    }

    @Override
    public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params) {
        return docService.codeAction(params);
    }

    @Override
    public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params) {
        return docService.codeLens(params);
    }

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams position) {
        return docService.completion(position);
    }

    @Override
    public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(
            DefinitionParams params) {
        return docService.definition(params);
    }

    @Override
    public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(
            DocumentSymbolParams params) {
        return docService.documentSymbol(params);
    }

    @Override
    public CompletableFuture<List<FoldingRange>> foldingRange(FoldingRangeRequestParams params) {
        return docService.foldingRange(params);
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
        return docService.formatting(params);
    }

    @Override
    public CompletableFuture<Hover> hover(HoverParams params) {
        return docService.hover(params);
    }

    @Override
    public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> implementation(
            ImplementationParams params) {
        return docService.implementation(params);
    }

    @Override
    public CompletableFuture<List<InlayHint>> inlayHint(InlayHintParams params) {
        return docService.inlayHint(params);
    }

    @Override
    public CompletableFuture<List<CallHierarchyItem>> prepareCallHierarchy(CallHierarchyPrepareParams params) {
        return docService.prepareCallHierarchy(params);
    }

    @Override
    public CompletableFuture<Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>> prepareRename(
            PrepareRenameParams params) {
        return docService.prepareRename(params);
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> rangeFormatting(DocumentRangeFormattingParams params) {
        return docService.rangeFormatting(params);
    }

    @Override
    public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
        return docService.references(params);
    }

    @Override
    public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
        return docService.rename(params);
    }

    @Override
    public CompletableFuture<List<SelectionRange>> selectionRange(SelectionRangeParams params) {
        return docService.selectionRange(params);
    }

    @Override
    public CompletableFuture<SemanticTokens> semanticTokensFull(SemanticTokensParams params) {
        return docService.semanticTokensFull(params);
    }

    @Override
    public CompletableFuture<Either<SemanticTokens, SemanticTokensDelta>> semanticTokensFullDelta(
            SemanticTokensDeltaParams params) {
        return docService.semanticTokensFullDelta(params);
    }

    @Override
    public CompletableFuture<SemanticTokens> semanticTokensRange(SemanticTokensRangeParams params) {
        return docService.semanticTokensRange(params);
    }

    @Override
    public void didChangeWorkspaceFolders(DidChangeWorkspaceFoldersParams params) {
        wsService.didChangeWorkspaceFolders(params);
    }

    @Override
    public void didCreateFiles(CreateFilesParams params) {
        docService.didCreateFiles(params);
    }

    @Override
    public void didDeleteFiles(DeleteFilesParams params) {
        docService.didDeleteFiles(params);
    }

    @Override
    public void didRenameFiles(RenameFilesParams params) {
        wsService.didRenameFiles(params);
    }

    @Override
    public CompletableFuture<Object> executeCommand(ExecuteCommandParams params) {
        return wsService.executeCommand(params);
    }

    @Override
    public CompletableFuture<WorkspaceEdit> willCreateFiles(CreateFilesParams params) {
        return wsService.willCreateFiles(params);
    }

    @Override
    public CompletableFuture<WorkspaceEdit> willDeleteFiles(DeleteFilesParams params) {
        return wsService.willDeleteFiles(params);
    }

    @Override
    public CompletableFuture<WorkspaceEdit> willRenameFiles(RenameFilesParams params) {
        return wsService.willRenameFiles(params);
    }

    @Override
    public void cancelProgress(String progressId) {
        docService.cancelProgress(progressId);
    }

    @Override
    public void registerLanguage(LanguageParameter lang) {
        docService.registerLanguage(lang);
    }

    @Override
    public void unregisterLanguage(LanguageParameter lang) {
        docService.unregisterLanguage(lang);
    }

}
