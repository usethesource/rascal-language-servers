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

import com.google.gson.JsonPrimitive;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.PolyNull;
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
import org.eclipse.lsp4j.CodeLensOptions;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.DeleteFilesParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentRangeFormattingParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.ExecuteCommandOptions;
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
import org.eclipse.lsp4j.RenameOptions;
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
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.Either3;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.util.locations.ColumnMaps;
import org.rascalmpl.util.locations.LineColumnOffsetMap;
import org.rascalmpl.vscode.lsp.BaseWorkspaceService;
import org.rascalmpl.vscode.lsp.IBaseTextDocumentService;
import org.rascalmpl.vscode.lsp.TextDocumentState;
import org.rascalmpl.vscode.lsp.parametric.LanguageRegistry.LanguageParameter;
import org.rascalmpl.vscode.lsp.parametric.capabilities.CapabilityRegistration;
import org.rascalmpl.vscode.lsp.parametric.capabilities.CompletionCapability;
import org.rascalmpl.vscode.lsp.parametric.capabilities.FileOperationCapability;
import org.rascalmpl.vscode.lsp.rascal.conversion.CodeActions;
import org.rascalmpl.vscode.lsp.rascal.conversion.SemanticTokenizer;
import org.rascalmpl.vscode.lsp.util.Lists;
import org.rascalmpl.vscode.lsp.util.locations.Locations;

import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValue;

public class ParametricLanguageRouter extends BaseWorkspaceService implements IBaseTextDocumentService {

    private static final Logger logger = LogManager.getLogger(ParametricLanguageRouter.class);

    //// ROUTING

    // Map language name to remote service
    private final Map<String, ISingleLanguageService> languageServices = new ConcurrentHashMap<>();
    // Map file extension to language name
    private final Map<String, String> languagesByExtension = new ConcurrentHashMap<>();


    // Server stuff
    private final @Nullable LanguageParameter dedicatedLanguage;
    private final String dedicatedLanguageName;

    private @MonotonicNonNull CapabilityRegistration dynamicCapabilities;

    private final TextDocumentStateManager files = new TextDocumentStateManager();

    protected ParametricLanguageRouter(ExecutorService exec, @Nullable LanguageParameter dedicatedLanguage) {
        super(CodeActions.RASCAL_META_COMMAND, exec);

        this.dedicatedLanguage = dedicatedLanguage;
        if (dedicatedLanguage == null) {
            this.dedicatedLanguageName = "";
        }
        else {
            this.dedicatedLanguageName = dedicatedLanguage.getName();
        }
    }

    //// LANGUAGE MANAGEMENT

    private ISingleLanguageService language(TextDocumentItem textDocument) {
        return language(Locations.toLoc(textDocument.getUri()));
    }

    private TextDocumentService language(VersionedTextDocumentIdentifier textDocument) {
        return language(Locations.toLoc(textDocument.getUri()));
    }

    private TextDocumentService language(TextDocumentIdentifier textDocument) {
        return language(Locations.toLoc(textDocument.getUri()));
    }

    private ISingleLanguageService language(ISourceLocation uri) {
        var lang = safeLanguage(uri).orElseThrow(() ->
            new UnsupportedOperationException(String.format("Rascal Parametric LSP has no support for this file, since no language is registered for extension '%s': %s", extension(uri), uri))
        );
        return languageByName(lang);
    }

    private ISingleLanguageService languageByName(String lang) {
        var service = languageServices.get(lang);
        if (service == null) {
            throw new UnsupportedOperationException(String.format("Rascal Parametric LSP has no support for this file, since no language is registered with name '%s'", lang));
        }
        return service;
    }

    private Optional<String> safeLanguage(ISourceLocation loc) {
        var ext = extension(loc);
        if ("".equals(ext)) {
            var languages = new HashSet<>(languagesByExtension.values());
            if (languages.size() == 1) {
                logger.trace("File was opened without an extension; falling back to the single registered language for: {}", loc);
                return languages.stream().findFirst();
            } else {
                logger.error("File was opened without an extension and there are multiple languages registered, so we cannot pick a fallback for: {}", loc);
                return Optional.empty();
            }
        }
        return Optional.ofNullable(languagesByExtension.get(ext));
    }

    private static String extension(ISourceLocation doc) {
        return URIUtil.getExtension(doc);
    }

    private <R, P> @PolyNull R route(TextDocumentIdentifier file, BiFunction<TextDocumentService, P, @PolyNull R> func, P param) {
        return route(Locations.toLoc(file.getUri()), func, param);
    }

    private <R, P> @PolyNull R route(ISourceLocation file, BiFunction<TextDocumentService, P, @PolyNull R> func, P param) {
        var lang = language(file);
        return route(lang, func, param);
    }

    private <R, P> @PolyNull R route(ISingleLanguageService lang, BiFunction<TextDocumentService, P, @PolyNull R> func, P param) {
        return func.apply(lang, param);
    }

    private <R, P> @PolyNull R routeWs(ISingleLanguageService lang, BiFunction<WorkspaceService, P, @PolyNull R> func, P param) {
        return func.apply(lang, param);
    }

    //// WORKSPACE REQUESTS

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        language(params.getTextDocument()).didOpen(params);
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        files.updateFile(Locations.toLoc(params.getTextDocument()));
        language(params.getTextDocument()).didChange(params);
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        language(params.getTextDocument()).didSave(params);
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        var loc = Locations.toLoc(params.getTextDocument());
        files.removeFile(loc);
        language(loc).didClose(params);
    }

    @Override
    public void didDeleteFiles(DeleteFilesParams params) {
        // Parameters contain a list of files.
        // Since these files can belong to different languages, we map them to their respective language and
        // delegate to several services.
        params.getFiles().stream()
            .collect(Collectors.toMap(f -> language(Locations.toLoc(f.getUri())), List::of, Lists::union))
            .entrySet()
            .forEach(e -> e.getKey().didDeleteFiles(new DeleteFilesParams(e.getValue())));

        // TODO Clear column maps for these files? (Parametric did not do this)
    }

    @Override
    public void didRenameFiles(RenameFilesParams params, List<WorkspaceFolder> _workspaceFolders) {
        // Parameters contain a list of files.
        // Since these files can belong to different languages, we map them to their respective language and
        // delegate to several services.
        params.getFiles().stream()
            .collect(Collectors.toMap(f -> language(Locations.toLoc(f.getOldUri())), List::of, Lists::union))
            .entrySet()
            .forEach(e -> e.getKey().didRenameFiles(new RenameFilesParams(e.getValue())));

        // TODO Move column maps for these files? (Parametric did not do this)
    }

    //// GLOBAL SERVER STUFF

    private CapabilityRegistration availableCapabilities() {
        if (dynamicCapabilities == null) {
            throw new IllegalStateException("Dynamic capabilities are `null` - the document service did not yet connect to a client.");
        }
        return dynamicCapabilities;
    }

    @Override
    public void initializeServerCapabilities(ClientCapabilities clientCapabilities, ServerCapabilities result) {
        // Since the initialize request is the very first request after connecting, we can initialize the capabilities here
        // https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#initialize
        dynamicCapabilities = new CapabilityRegistration(availableClient(), exec, clientCapabilities
            , new CompletionCapability()
            , /* new FileOperationCapability.DidCreateFiles(exec), */ new FileOperationCapability.DidRenameFiles(exec), new FileOperationCapability.DidDeleteFiles(exec)
        );
        dynamicCapabilities.registerStaticCapabilities(result);

        var tokenizer = new SemanticTokenizer();

        result.setDefinitionProvider(true);
        result.setTextDocumentSync(TextDocumentSyncKind.Full);
        result.setHoverProvider(true);
        result.setReferencesProvider(true);
        result.setDocumentSymbolProvider(true);
        result.setImplementationProvider(true);
        result.setSemanticTokensProvider(tokenizer.options());
        result.setCodeActionProvider(true);
        result.setCodeLensProvider(new CodeLensOptions(false));
        result.setRenameProvider(new RenameOptions(true));
        result.setExecuteCommandProvider(new ExecuteCommandOptions(Collections.singletonList(CodeActions.getRascalMetaCommandName("", dedicatedLanguageName))));
        result.setInlayHintProvider(true);
        result.setSelectionRangeProvider(true);
        result.setFoldingRangeProvider(true);
        result.setCallHierarchyProvider(true);
    }

    @Override
    public void shutdown() {
        // TODO Kill all delegate processes
        exec.shutdown();
    }

    @Override
    public void pair(BaseWorkspaceService workspaceService) {
        // Nothing to do; no need to pair with ourselves
    }

    @Override
    public void initialized() {
        if (dedicatedLanguage != null) {
            // if there was one scheduled, we now start it up, since the connection has been made
            // and the client and capabilities are initialized
            this.registerLanguage(dedicatedLanguage);
        }
    }

    private ISingleLanguageService getOrBuildLanguageService(LanguageParameter lang) {
        return languageServices.computeIfAbsent(lang.getName(), l -> {
            // TODO Start a delegate process with the right versions on the classpath for this language
            var s = new SingleLanguageServer(l);
            s.connect(availableClient());
            return s;
        });
    }

    @Override
    public synchronized void registerLanguage(LanguageParameter lang) {
        logger.info("registerLanguage({})", lang.getName());

        var langService = getOrBuildLanguageService(lang);
        langService.registerLanguage(lang);

        for (var extension: lang.getExtensions()) {
            this.languagesByExtension.put(extension, lang.getName());
        }

        // `CapabilityRegistration::update` should never be called asynchronously, since that might re-order incoming updates.
        // Since `registerLanguage` is called from a single-threaded pool, calling it here is safe.
        // Note: `CapabilityRegistration::update` returns a void future, which we do not have to wait on.
        // TODO Dynamic registration of capabilities
        // availableCapabilities().update(buildLanguageParams());
    }

    @Override
    public synchronized void unregisterLanguage(LanguageParameter lang) {
        logger.info("unregisterLanguage({})", lang.getName());
        var removedLang = languageServices.remove(lang.getName());
        if (removedLang != null) {
            removedLang.unregisterLanguage(lang);
            // TODO Kill the delegate process for this language and clean up maps
        }
    }

    @Override
    public void projectAdded(String name, ISourceLocation projectRoot) {
        // No need to do anything
    }

    @Override
    public void projectRemoved(String name, ISourceLocation projectRoot) {
        // No need to do anything
    }

    @Override
    public LineColumnOffsetMap getColumnMap(ISourceLocation file) {
        return files.getColumnMap(file);
    }

    @Override
    public ColumnMaps getColumnMaps() {
        return files.getColumnMaps();
    }

    @Override
    public @Nullable TextDocumentState getDocumentState(ISourceLocation file) {
        return files.getDocumentState(file);
    }

    @Override
    public boolean isManagingFile(ISourceLocation file) {
        return files.isManagingFile(file);
    }

    @Override
    public void cancelProgress(String progressId) {
        languageServices.values().stream().forEach(l -> l.cancelProgress(progressId));
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
    public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params) {
        return route(params.getTextDocument(), TextDocumentService::codeAction, params);
    }

    @Override
    public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params) {
        return route(params.getTextDocument(), TextDocumentService::codeLens, params);
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
    public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(
            DocumentSymbolParams params) {
        return route(params.getTextDocument(), TextDocumentService::documentSymbol, params);
    }

    @Override
    public CompletableFuture<List<FoldingRange>> foldingRange(FoldingRangeRequestParams params) {
        return route(params.getTextDocument(), TextDocumentService::foldingRange, params);
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
        return route(params.getTextDocument(), TextDocumentService::formatting, params);
    }

    @Override
    public CompletableFuture<Hover> hover(HoverParams params) {
        return route(params.getTextDocument(), TextDocumentService::hover, params);
    }

    @Override
    public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> implementation(
            ImplementationParams params) {
        return route(params.getTextDocument(), TextDocumentService::implementation, params);
    }

    @Override
    public CompletableFuture<List<InlayHint>> inlayHint(InlayHintParams params) {
        return route(params.getTextDocument(), TextDocumentService::inlayHint, params);
    }

    @Override
    public CompletableFuture<List<CallHierarchyItem>> prepareCallHierarchy(CallHierarchyPrepareParams params) {
        return route(params.getTextDocument(), TextDocumentService::prepareCallHierarchy, params);
    }

    @Override
    public CompletableFuture<Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>> prepareRename(
            PrepareRenameParams params) {
        return route(params.getTextDocument(), TextDocumentService::prepareRename, params);
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> rangeFormatting(DocumentRangeFormattingParams params) {
        return route(params.getTextDocument(), TextDocumentService::rangeFormatting, params);
    }

    @Override
    public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
        return route(params.getTextDocument(), TextDocumentService::references, params);
    }

    @Override
    public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
        return route(params.getTextDocument(), TextDocumentService::rename, params);
    }

    @Override
    public CompletableFuture<List<SelectionRange>> selectionRange(SelectionRangeParams params) {
        return route(params.getTextDocument(), TextDocumentService::selectionRange, params);
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
    public CompletableFuture<Object> executeCommand(ExecuteCommandParams commandParams) {
        // TODO Use helper class for param types here and on creation?
        var language = ((JsonPrimitive) commandParams.getArguments().get(0)).getAsString();
        var command = ((JsonPrimitive) commandParams.getArguments().get(1)).getAsString();
        return this.executeCommand(language, command).thenApply(Function.identity());
    }

    @Override
    public CompletableFuture<IValue> executeCommand(String languageName, String command) {
        return languageByName(languageName).executeCommand(languageName, command);
    }

}
