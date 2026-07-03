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

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.stream.Stream;
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
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.Either3;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.vscode.lsp.BaseWorkspaceService;
import org.rascalmpl.vscode.lsp.IBaseLanguageServerExtensions;
import org.rascalmpl.vscode.lsp.IBaseTextDocumentService;
import org.rascalmpl.vscode.lsp.TextDocumentStateManager;
import org.rascalmpl.vscode.lsp.model.DiagnosticsReporter;
import org.rascalmpl.vscode.lsp.parametric.LanguageRegistry.LanguageParameter;
import org.rascalmpl.vscode.lsp.parametric.ParametricTextDocumentService;
import org.rascalmpl.vscode.lsp.uri.LSPOpenFileRedirector;
import org.rascalmpl.vscode.lsp.util.DocumentRouter;
import org.rascalmpl.vscode.lsp.util.concurrent.CompletableFutureUtils;
import org.rascalmpl.vscode.lsp.util.locations.Locations;

import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValue;

/**
 * A language-parametric text document service that routes incoming requests to remote dedicated language servers.
 */
public class RoutingTextDocumentService extends TextDocumentStateManager implements IBaseTextDocumentService, DocumentRouter<CompletableFuture<TextDocumentService>> {

    private static final Logger logger = LogManager.getLogger(RoutingTextDocumentService.class);

    private final ExecutorService exec;
    private @MonotonicNonNull LanguageClient client;
    private @MonotonicNonNull DocumentRouter<CompletableFuture<IBaseLanguageServerExtensions>> serverRouter;

    @SuppressWarnings("unused")
    /*package*/ RoutingTextDocumentService(ExecutorService exec) {
        this.exec = exec;

        LSPOpenFileRedirector.getInstance().registerTextDocumentService(this);
    }

    /*package*/ void setServerRouter(DocumentRouter<CompletableFuture<IBaseLanguageServerExtensions>> serverRouter) {
        this.serverRouter = serverRouter;
    }

    private DocumentRouter<CompletableFuture<IBaseLanguageServerExtensions>> availableServerRouter() {
        if (serverRouter == null) {
            // This should only happen if we forgot to call `setServerRouter` before finishing initialization
            throw new IllegalStateException("No server router available");
        }
        return serverRouter;
    }

    @Override
    public Stream<CompletableFuture<TextDocumentService>> allRoutes() {
        return availableServerRouter().allRoutes(server -> server.thenApply(LanguageServer::getTextDocumentService));
    }

    /**
     * Notify all remote servers and wait for their response.
     */
    private <P> void notifyAllRemotes(BiConsumer<TextDocumentService, P> notify, P params) {
        try {
            CompletableFutureUtils.reduce(allRoutes(r -> {
                try {
                    return r.thenAccept(s -> notify.accept(s, params));
                } catch (Exception e) {
                    logger.error("Unexpected error while notifying all remote servers", e);
                    return CompletableFutureUtils.<Void>completedFuture(null, exec);
                }
            }), exec).get(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            logger.catching(e);
        }
    }

    @Override
    public CompletableFuture<TextDocumentService> route(ISourceLocation loc) {
        return availableServerRouter().route(loc).thenApply(LanguageServer::getTextDocumentService);
    }

    @Override
    public CompletableFuture<TextDocumentService> route(String language) {
        return availableServerRouter().route(language).thenApply(LanguageServer::getTextDocumentService);
    }

    private <P, R> CompletableFuture<R> route(TextDocumentIdentifier doc, BiFunction<TextDocumentService, P, CompletableFuture<R>> endpoint, P params) {
        return routeCompose(route(doc), endpoint, params);
    }

    private <P, R> CompletableFuture<R> route(ISourceLocation loc, BiFunction<TextDocumentService, P, CompletableFuture<R>> endpoint, P params) {
        return routeCompose(route(loc), endpoint, params);
    }

    private <P, R> CompletableFuture<R> routeCompose(CompletableFuture<TextDocumentService> sf, BiFunction<TextDocumentService, P, CompletableFuture<R>> endpoint, P params) {
        return sf.thenCompose(s -> endpoint.apply(s, params));
    }

    @Override
    public Collection<String> extensions() {
        throw new UnsupportedOperationException("extensions() should not be called on the routing server, but only on delegate servers.");
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        var timestamp = System.currentTimeMillis();
        openFile(params.getTextDocument(), l -> (loc, contents) -> CompletableFutureUtils.completedFuture(IRascalValueFactory.getInstance().character(0), exec), timestamp, exec);

        // Inform all remote servers about this file, so they can maintain its state.
        notifyAllRemotes(TextDocumentService::didOpen, params);
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        var timestamp = System.currentTimeMillis();
        updateContents(params, timestamp);

        // Inform all remote servers about this file, so they can maintain its state.
        notifyAllRemotes(TextDocumentService::didChange, params);
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        closeFile(Locations.toLoc(params.getTextDocument()));

        // Inform all remote servers about this file, so they can maintain its state.
        notifyAllRemotes(TextDocumentService::didClose, params);
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        // Inform only the remote server for this language, since this does not change file state
        // Note: floating future
        route(params.getTextDocument()).thenAccept(s -> s.didSave(params));
    }

    @Override
    public void initializeServerCapabilities(ClientCapabilities clientCapabilities, ServerCapabilities result) {
        // Only register static capabilities here; we leave dynamic capability registration to the remote servers
        ParametricTextDocumentService.initializeStaticServerCapabilities(result);
    }

    @Override
    public void connect(LanguageClient client) {
        this.client = client;
    }

    @Override
    public void pair(BaseWorkspaceService workspaceService) {
        // reserved for future use
    }

    @Override
    public void initialized() {
        // reserved for future use
    }

    @Override
    public void registerLanguage(LanguageParameter lang) {
        // Nothing to do here
    }

    @Override
    public void unregisterLanguage(LanguageParameter lang) {
        // Nothing to do here
    }

    @Override
    public void cancelProgress(String progressId) {
        // Nothing to do here
    }

    @Override
    public CompletableFuture<IValue> executeCommand(String languageName, String command) {
        throw new UnsupportedOperationException("Call RoutingWorkspaceService::executeCommand instead");
    }

    @Override
    protected DiagnosticsReporter getDiagnosticsReporter(ISourceLocation file) {
        return (l, msgs) -> {
            // NOP; we delegate diagnostic reporting to our dedicated remote servers
        };
    }

    @Override
    public void didCreateFiles(CreateFilesParams params) {
        // TODO Mimick VS given certain file operation filters (capabilities)
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
    public CompletableFuture<List<CallHierarchyIncomingCall>> callHierarchyIncomingCalls(
            CallHierarchyIncomingCallsParams params) {
        return route(Locations.toLoc(params.getItem().getUri()), TextDocumentService::callHierarchyIncomingCalls, params);
    }

    @Override
    public CompletableFuture<List<CallHierarchyOutgoingCall>> callHierarchyOutgoingCalls(
            CallHierarchyOutgoingCallsParams params) {
        return route(Locations.toLoc(params.getItem().getUri()), TextDocumentService::callHierarchyOutgoingCalls, params);
    }

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams position) {
        return route(position.getTextDocument(), TextDocumentService::completion, position);
    }

    @Override
    public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(
            DefinitionParams params) {
        return route(params.getTextDocument(), TextDocumentService::definition, params);
    }

    @Override
    public CompletableFuture<List<CallHierarchyItem>> prepareCallHierarchy(CallHierarchyPrepareParams params) {
        return route(params.getTextDocument(), TextDocumentService::prepareCallHierarchy, params);
    }

    @Override
    public CompletableFuture<SemanticTokens> semanticTokensFull(SemanticTokensParams params) {
        return route(params.getTextDocument(), TextDocumentService::semanticTokensFull, params);
    }

    @Override
    public CompletableFuture<Either<SemanticTokens, SemanticTokensDelta>> semanticTokensFullDelta(
            SemanticTokensDeltaParams params) {
        return route(params.getTextDocument(), TextDocumentService::semanticTokensFullDelta, params);
    }

    @Override
    public CompletableFuture<SemanticTokens> semanticTokensRange(SemanticTokensRangeParams params) {
        return route(params.getTextDocument(), TextDocumentService::semanticTokensRange, params);
    }

    @Override
    public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params) {
        return route(params.getTextDocument(), TextDocumentService::codeLens, params);
    }

    @Override
    public CompletableFuture<Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>> prepareRename(
            PrepareRenameParams params) {
        return route(params.getTextDocument(), TextDocumentService::prepareRename, params);
    }

    @Override
    public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
        return route(params.getTextDocument(), TextDocumentService::rename, params);
    }

    @Override
    public CompletableFuture<List<InlayHint>> inlayHint(InlayHintParams params) {
        return route(params.getTextDocument(), TextDocumentService::inlayHint, params);
    }

    @Override
    public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params) {
        return route(params.getTextDocument(), TextDocumentService::codeAction, params);
    }

    @Override
    public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(
            DocumentSymbolParams params) {
        return route(params.getTextDocument(), TextDocumentService::documentSymbol, params);
    }

    @Override
    public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> implementation(
            ImplementationParams params) {
        return route(params.getTextDocument(), TextDocumentService::implementation, params);
    }

    @Override
    public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
        return route(params.getTextDocument(), TextDocumentService::references, params);
    }

    @Override
    public CompletableFuture<List<FoldingRange>> foldingRange(FoldingRangeRequestParams params) {
        return route(params.getTextDocument(), TextDocumentService::foldingRange, params);
    }

    @Override
    public CompletableFuture<@Nullable Hover> hover(HoverParams params) {
        return this.<HoverParams, @Nullable Hover>route(params.getTextDocument(), TextDocumentService::hover, params);
    }

    @Override
    public CompletableFuture<List<SelectionRange>> selectionRange(SelectionRangeParams params) {
        return route(params.getTextDocument(), TextDocumentService::selectionRange, params);
    }

}
