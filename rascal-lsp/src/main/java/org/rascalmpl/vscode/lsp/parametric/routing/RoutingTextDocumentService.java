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
package org.rascalmpl.vscode.lsp.parametric.routing;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.CallHierarchyIncomingCall;
import org.eclipse.lsp4j.CallHierarchyIncomingCallsParams;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.eclipse.lsp4j.CallHierarchyOutgoingCall;
import org.eclipse.lsp4j.CallHierarchyOutgoingCallsParams;
import org.eclipse.lsp4j.CallHierarchyPrepareParams;
import org.eclipse.lsp4j.ClientCapabilities;
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
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
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
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.Either3;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.rascalmpl.util.locations.ColumnMaps;
import org.rascalmpl.util.locations.LineColumnOffsetMap;
import org.rascalmpl.vscode.lsp.BaseWorkspaceService;
import org.rascalmpl.vscode.lsp.IBaseLanguageServerExtensions;
import org.rascalmpl.vscode.lsp.IBaseTextDocumentService;
import org.rascalmpl.vscode.lsp.LanguageServerRouter;
import org.rascalmpl.vscode.lsp.TextDocumentState;
import org.rascalmpl.vscode.lsp.parametric.LanguageRegistry.LanguageParameter;
import org.rascalmpl.vscode.lsp.parametric.ParametricTextDocumentService;
import org.rascalmpl.vscode.lsp.util.Caller;
import org.rascalmpl.vscode.lsp.util.Lists;
import org.rascalmpl.vscode.lsp.util.Router;
import org.rascalmpl.vscode.lsp.util.locations.Locations;

import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValue;

public class RoutingTextDocumentService implements IBaseTextDocumentService, Caller<IBaseTextDocumentService>, Router<CompletableFuture<IBaseTextDocumentService>> {

    private static final Logger logger = LogManager.getLogger(RoutingTextDocumentService.class);

    private @MonotonicNonNull LanguageClient client;
    private @MonotonicNonNull BaseWorkspaceService wsService;
    private @MonotonicNonNull LanguageServerRouter server;

    private final ExecutorService exec;

    public RoutingTextDocumentService(ExecutorService exec) {
        this.exec = exec;
    }

    public void setServer(LanguageServerRouter server) {
        this.server = server;
    }

    @Override
    public CompletableFuture<IBaseTextDocumentService> route(ISourceLocation loc) {
        return availableServer()
            .route(loc)
            .thenApply(IBaseLanguageServerExtensions::getIBaseTextDocumentService);
    }

    private LanguageClient availableClient() {
        if (client == null) {
            throw new IllegalStateException("Client not connected yet.");
        }
        return client;
    }

    private LanguageServerRouter availableServer() {
        if (server == null) {
            throw new IllegalStateException("Server not connected yet.");
        }
        return server;
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        call(route(params.getTextDocument()), TextDocumentService::didOpen, params);
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        call(route(params.getTextDocument()), TextDocumentService::didChange, params);
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'didClose'");
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'didSave'");
    }

    @Override
    public void initializeServerCapabilities(ClientCapabilities clientCapabilities, ServerCapabilities result) {
        ParametricTextDocumentService.setStaticServerCapabilities(result);
    }

    @Override
    public void shutdown() {
        availableServer().shutdown();
    }

    @Override
    public void connect(LanguageClient client) {
        logger.debug("Connecting client {}", client);
        this.client = client;
    }

    @Override
    public void pair(BaseWorkspaceService workspaceService) {
        this.wsService = workspaceService;
    }

    @Override
    public void initialized() {
        // reserved for future use
    }

    @Override
    public void registerLanguage(LanguageParameter lang) {
        logger.debug("textDocument/registerLanguage({}, {})", lang.getName(), lang.getMainFunction());
        availableServer().languageByName(lang.getName()).thenApply(s -> s.sendRegisterLanguage(lang));
    }

    @Override
    public void unregisterLanguage(LanguageParameter lang) {
        logger.debug("textDocument/unregisterLanguage({})", lang.getName());
        availableServer().languageByName(lang.getName()).thenApply(s -> s.sendUnregisterLanguage(lang));
    }

    @Override
    public void projectAdded(String name, ISourceLocation projectRoot) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'projectAdded'");
    }

    @Override
    public void projectRemoved(String name, ISourceLocation projectRoot) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'projectRemoved'");
    }

    @Override
    public CompletableFuture<IValue> executeCommand(String languageName, String command) {
        return availableServer().languageByName(languageName)
            .thenCompose(s -> s.getIBaseTextDocumentService().executeCommand(languageName, command));
    }

    @Override
    public LineColumnOffsetMap getColumnMap(ISourceLocation file) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getColumnMap'");
    }

    @Override
    public ColumnMaps getColumnMaps() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getColumnMaps'");
    }

    @Override
    public @Nullable TextDocumentState getDocumentState(ISourceLocation file) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getDocumentState'");
    }

    @Override
    public boolean isManagingFile(ISourceLocation file) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isManagingFile'");
    }

    @Override
    public void didCreateFiles(CreateFilesParams params) {
        // TODO Mimick VS given certain file operation filters (capabilities)
        var filesByExt = params.getFiles().stream()
            .collect(Collectors.toMap(f -> LanguageServerRouter.extension(Locations.toLoc(f.getUri())), List::of, Lists::union));

        for (var e : filesByExt.entrySet()) {
            availableServer().route(e.getKey()).thenAccept(server -> {
                server.getWorkspaceService().didCreateFiles(new CreateFilesParams(e.getValue()));
            });
        }
    }

    @Override
    public void didRenameFiles(RenameFilesParams params, List<WorkspaceFolder> workspaceFolders) {
        // TODO Mimick VS given certain file operation filters (capabilities)
    }

    @Override
    public void didDeleteFiles(DeleteFilesParams params) {
        // TODO Mimick VS given certain file operation filters (capabilities)
    }

    @Override
    public void cancelProgress(String progressId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'cancelProgress'");
    }

    @Override
    public CompletableFuture<List<CallHierarchyIncomingCall>> callHierarchyIncomingCalls(
            CallHierarchyIncomingCallsParams params) {
        return callC(route(Locations.toLoc(params.getItem().getUri())), TextDocumentService::callHierarchyIncomingCalls, params);
    }

    @Override
    public CompletableFuture<List<CallHierarchyOutgoingCall>> callHierarchyOutgoingCalls(
            CallHierarchyOutgoingCallsParams params) {
        return callC(route(Locations.toLoc(params.getItem().getUri())), TextDocumentService::callHierarchyOutgoingCalls, params);
    }

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams position) {
        return callC(route(position.getTextDocument()), TextDocumentService::completion, position);
    }

    @Override
    public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(
            DefinitionParams params) {
        return callC(route(params.getTextDocument()), TextDocumentService::definition, params);
    }

    @Override
    public CompletableFuture<List<CallHierarchyItem>> prepareCallHierarchy(CallHierarchyPrepareParams params) {
        return callC(route(params.getTextDocument()), TextDocumentService::prepareCallHierarchy, params);
    }

    @Override
    public CompletableFuture<SemanticTokens> semanticTokensFull(SemanticTokensParams params) {
        return callC(route(params.getTextDocument()), TextDocumentService::semanticTokensFull, params);
    }

    @Override
    public CompletableFuture<Either<SemanticTokens, SemanticTokensDelta>> semanticTokensFullDelta(
            SemanticTokensDeltaParams params) {
        return callC(route(params.getTextDocument()), TextDocumentService::semanticTokensFullDelta, params);
    }

    @Override
    public CompletableFuture<SemanticTokens> semanticTokensRange(SemanticTokensRangeParams params) {
        return callC(route(params.getTextDocument()), TextDocumentService::semanticTokensRange, params);
    }

    @Override
    public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params) {
        return callC(route(params.getTextDocument()), TextDocumentService::codeLens, params);
    }

    @Override
    public CompletableFuture<Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>> prepareRename(
            PrepareRenameParams params) {
        return callC(route(params.getTextDocument()), TextDocumentService::prepareRename, params);
    }

    @Override
    public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
        return callC(route(params.getTextDocument()), TextDocumentService::rename, params);
    }

    @Override
    public CompletableFuture<List<InlayHint>> inlayHint(InlayHintParams params) {
        return callC(route(params.getTextDocument()), TextDocumentService::inlayHint, params);
    }

    @Override
    public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params) {
        return callC(route(params.getTextDocument()), TextDocumentService::codeAction, params);
    }

    @Override
    public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(
            DocumentSymbolParams params) {
        return callC(route(params.getTextDocument()), TextDocumentService::documentSymbol, params);
    }

    @Override
    public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> implementation(
            ImplementationParams params) {
        return callC(route(params.getTextDocument()), TextDocumentService::implementation, params);
    }

    @Override
    public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
        return callC(route(params.getTextDocument()), TextDocumentService::references, params);
    }

    @Override
    public CompletableFuture<List<FoldingRange>> foldingRange(FoldingRangeRequestParams params) {
        return callC(route(params.getTextDocument()), TextDocumentService::foldingRange, params);
    }

    @Override
    public CompletableFuture<@Nullable Hover> hover(HoverParams params) {
        return this.<HoverParams, @Nullable Hover>callC(route(params.getTextDocument()), TextDocumentService::hover, params);
    }

    @Override
    public CompletableFuture<List<SelectionRange>> selectionRange(SelectionRangeParams params) {
        return callC(route(params.getTextDocument()), TextDocumentService::selectionRange, params);
    }



}
