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

import java.io.IOException;
import java.io.Reader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.util.IOUtils;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.CallHierarchyIncomingCall;
import org.eclipse.lsp4j.CallHierarchyIncomingCallsParams;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.eclipse.lsp4j.CallHierarchyOutgoingCall;
import org.eclipse.lsp4j.CallHierarchyOutgoingCallsParams;
import org.eclipse.lsp4j.CallHierarchyPrepareParams;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensOptions;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CreateFilesParams;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.DeleteFilesParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.ExecuteCommandOptions;
import org.eclipse.lsp4j.FileRename;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeRequestParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.ImplementationParams;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintKind;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PrepareRenameDefaultBehavior;
import org.eclipse.lsp4j.PrepareRenameParams;
import org.eclipse.lsp4j.PrepareRenameResult;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
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
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.Either3;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.util.locations.ColumnMaps;
import org.rascalmpl.util.locations.LineColumnOffsetMap;
import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.values.parsetrees.TreeAdapter;
import org.rascalmpl.vscode.lsp.BaseWorkspaceService;
import org.rascalmpl.vscode.lsp.IBaseLanguageClient;
import org.rascalmpl.vscode.lsp.IBaseTextDocumentService;
import org.rascalmpl.vscode.lsp.TextDocumentState;
import org.rascalmpl.vscode.lsp.parametric.model.ParametricFileFacts;
import org.rascalmpl.vscode.lsp.parametric.model.ParametricSummary;
import org.rascalmpl.vscode.lsp.parametric.model.ParametricSummary.SummaryLookup;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.LanguageParameter;
import org.rascalmpl.vscode.lsp.uri.FallbackResolver;
import org.rascalmpl.vscode.lsp.util.CallHierarchy;
import org.rascalmpl.vscode.lsp.util.CodeActions;
import org.rascalmpl.vscode.lsp.util.Diagnostics;
import org.rascalmpl.vscode.lsp.util.DocumentChanges;
import org.rascalmpl.vscode.lsp.util.DocumentSymbols;
import org.rascalmpl.vscode.lsp.util.FoldingRanges;
import org.rascalmpl.vscode.lsp.util.Lists;
import org.rascalmpl.vscode.lsp.util.SelectionRanges;
import org.rascalmpl.vscode.lsp.util.SemanticTokenizer;
import org.rascalmpl.vscode.lsp.util.Versioned;
import org.rascalmpl.vscode.lsp.util.concurrent.CompletableFutureUtils;
import org.rascalmpl.vscode.lsp.util.concurrent.InterruptibleFuture;
import org.rascalmpl.vscode.lsp.util.locations.Locations;
import org.rascalmpl.vscode.lsp.util.locations.impl.TreeSearch;

import io.usethesource.vallang.IBool;
import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.ITuple;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IValueFactory;
import io.usethesource.vallang.exceptions.FactParseError;
import io.usethesource.vallang.type.Type;
import io.usethesource.vallang.type.TypeFactory;
import io.usethesource.vallang.type.TypeStore;

public class ParametricTextDocumentService implements IBaseTextDocumentService, LanguageClientAware {
    private static final IValueFactory VF = IRascalValueFactory.getInstance();
    private static final Logger logger = LogManager.getLogger(ParametricTextDocumentService.class);

    private final ExecutorService ownExecuter;

    private final String dedicatedLanguageName;
    private final SemanticTokenizer tokenizer = new SemanticTokenizer();
    private @MonotonicNonNull LanguageClient client;
    private @MonotonicNonNull BaseWorkspaceService workspaceService;

    private final Map<ISourceLocation, TextDocumentState> files;
    private final ColumnMaps columns;

    /** extension to language */
    private final Map<String, String> registeredExtensions = new ConcurrentHashMap<>();
    /** language to facts */
    private final Map<String, ParametricFileFacts> facts = new ConcurrentHashMap<>();
    /** language to contribution */
    private final Map<String, LanguageContributionsMultiplexer> contributions = new ConcurrentHashMap<>();

    private final @Nullable LanguageParameter dedicatedLanguage;

    // Create "renamed" constructor of "FileSystemChange" so we can build a list of DocumentEdit objects for didRenameFiles
    private final TypeStore typeStore = new TypeStore();
    private final TypeFactory tf = TypeFactory.getInstance();
    private final Type renamedConstructor = tf.constructor(typeStore,
            tf.abstractDataType(typeStore, "FileSystemChange"), "renamed", tf.sourceLocationType(), "from",
            tf.sourceLocationType(), "to");

    public ParametricTextDocumentService(ExecutorService exec, @Nullable LanguageParameter dedicatedLanguage) {
        // The following call ensures that URIResolverRegistry is initialized before FallbackResolver is accessed
        URIResolverRegistry.getInstance();

        this.ownExecuter = exec;
        this.files = new ConcurrentHashMap<>();
        this.columns = new ColumnMaps(this::getContents);
        if (dedicatedLanguage == null) {
            this.dedicatedLanguageName = "";
            this.dedicatedLanguage = null;
        }
        else {
            this.dedicatedLanguageName = dedicatedLanguage.getName();
            this.dedicatedLanguage = dedicatedLanguage;
        }
        FallbackResolver.getInstance().registerTextDocumentService(this);
    }

    @Override
    public ColumnMaps getColumnMaps() {
        return columns;
    }

    @Override
    public LineColumnOffsetMap getColumnMap(ISourceLocation file) {
        return columns.get(file);
    }

    public String getContents(ISourceLocation file) {
        file = file.top();
        TextDocumentState ideState = files.get(file);
        if (ideState != null) {
            return ideState.getCurrentContent().get();
        }
        try (Reader src = URIResolverRegistry.getInstance().getCharacterReader(file)) {
            return IOUtils.toString(src);
        }
        catch (IOException e) {
            logger.error("Error opening file {} to get contents", file, e);
            return "";
        }
    }

    public void initializeServerCapabilities(ServerCapabilities result) {
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
        result.setExecuteCommandProvider(new ExecuteCommandOptions(Collections.singletonList(getRascalMetaCommandName())));
        result.setInlayHintProvider(true);
        result.setSelectionRangeProvider(true);
        result.setFoldingRangeProvider(true);
        result.setCallHierarchyProvider(true);
    }

    private String getRascalMetaCommandName() {
        // if we run in dedicated mode, we prefix the commands with our language name
        // to avoid ambiguity with other dedicated languages and the generic rascal plugin
        if (!dedicatedLanguageName.isEmpty()) {
            return BaseWorkspaceService.RASCAL_META_COMMAND + "-" + dedicatedLanguageName;
        }
        return BaseWorkspaceService.RASCAL_META_COMMAND;
    }

    private BaseWorkspaceService availableWorkspaceService() {
        if (workspaceService == null) {
            throw new IllegalStateException("Workspace Service has not been paired");
        }
        return workspaceService;
    }

    @Override
    public void pair(BaseWorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    private LanguageClient availableClient() {
        if (client == null) {
            throw new IllegalStateException("Language Client has not been connected yet");
        }
        return client;
    }

    @Override
    public void connect(LanguageClient client) {
        this.client = client;
        facts.values().forEach(v -> v.setClient(client));
        if (dedicatedLanguage != null) {
            // if there was one scheduled, we now start it up, since the connection has been made
            this.registerLanguage(dedicatedLanguage);
        }
    }

    @Override
    public void initialized() {
        // reserved for future use
        // e.g. dynamic registration of capabilities
    }

    // LSP interface methods

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        var timestamp = System.currentTimeMillis();
        logger.debug("Did Open file: {}", params.getTextDocument());
        TextDocumentState file = open(params.getTextDocument(), timestamp);
        handleParsingErrors(file, file.getCurrentDiagnosticsAsync());
        triggerAnalyzer(params.getTextDocument(), NORMAL_DEBOUNCE);
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        var timestamp = System.currentTimeMillis();
        logger.debug("Did Change file: {}", params.getTextDocument().getUri());
        updateContents(params.getTextDocument(), last(params.getContentChanges()).getText(), timestamp);
        triggerAnalyzer(params.getTextDocument(), NORMAL_DEBOUNCE);
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        logger.debug("Did Save file: {}", params.getTextDocument());
        // on save we don't get new file contents, that already came in via didChange
        // but we do trigger the builder on save (if a builder exists)
        triggerBuilder(params.getTextDocument());
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        logger.debug("Did Close file: {}", params.getTextDocument());
        var loc = Locations.toLoc(params.getTextDocument());
        if (files.remove(loc) == null) {
            throw new ResponseErrorException(unknownFileError(loc, params));
        }
        facts(loc).close(loc);
    }

    @Override
    public void didDeleteFiles(DeleteFilesParams params) {
        ownExecuter.submit(() -> {
            // if a file is deleted, and we were tracking it, we remove our diagnostics
            for (var f : params.getFiles()) {
                if (isLanguageRegistered(URIUtil.assumeCorrectLocation(f.getUri()))) {
                    availableClient().publishDiagnostics(new PublishDiagnosticsParams(f.getUri(), List.of()));
                }
            }
        });
    }


    private void triggerAnalyzer(TextDocumentItem doc, Duration delay) {
        triggerAnalyzer(new VersionedTextDocumentIdentifier(doc.getUri(), doc.getVersion()), delay);
    }

    private void triggerAnalyzer(VersionedTextDocumentIdentifier doc, Duration delay) {
        var location = Locations.toLoc(doc);
        triggerAnalyzer(location, doc.getVersion(), delay);
    }

    private void triggerAnalyzer(ISourceLocation location, int version, Duration delay) {
        if (isLanguageRegistered(location)) {
            logger.trace("Triggering analyzer for {}", location);
            var fileFacts = facts(location);
            fileFacts.invalidateAnalyzer(location);
            fileFacts.calculateAnalyzer(location, getFile(location).getCurrentTreeAsync(true), version, delay);
        } else {
            logger.debug("Not triggering analyzer, since no language is registered for {}", location);
        }
    }

    private void triggerBuilder(TextDocumentIdentifier doc) {
        logger.trace("Triggering builder for {}", doc.getUri());
        var location = Locations.toLoc(doc);
        var fileFacts = facts(location);
        fileFacts.invalidateBuilder(location);
        fileFacts.calculateBuilder(location, getFile(location).getCurrentTreeAsync(true));
    }

    private void updateContents(VersionedTextDocumentIdentifier doc, String newContents, long timestamp) {
        logger.trace("New contents for {}", doc);
        TextDocumentState file = getFile(Locations.toLoc(doc));
        columns.clear(file.getLocation());
        handleParsingErrors(file, file.update(doc.getVersion(), newContents, timestamp));
    }

    private void handleParsingErrors(TextDocumentState file, CompletableFuture<Versioned<List<Diagnostics.Template>>> diagnosticsAsync) {
        diagnosticsAsync.thenAccept(diagnostics -> {
            List<Diagnostic> parseErrors = diagnostics.get().stream()
                .map(diagnostic -> diagnostic.instantiate(columns))
                .collect(Collectors.toList());

            logger.trace("Finished parsing tree, reporting new parse errors: {} for: {}", parseErrors, file.getLocation());
            facts(file.getLocation()).reportParseErrors(file.getLocation(), diagnostics.version(), parseErrors);
        });
    }

    @Override
    public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params) {
        logger.trace("codeLens for: {}", params.getTextDocument().getUri());
        ISourceLocation loc = Locations.toLoc(params.getTextDocument());
        TextDocumentState file = getFile(loc);
        ILanguageContributions contrib = contributions(loc);

        return recoverExceptions(file.getCurrentTreeAsync(true)
            .thenApply(Versioned::get)
            .thenApply(contrib::codeLens)
            .thenCompose(InterruptibleFuture::get)
            .thenApply(s -> s.stream()
                .map(e -> locCommandTupleToCodeLense(contrib.getName(), e))
                .collect(Collectors.toList())
            ), () -> null);
    }


    @Override
    public CompletableFuture<Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>> prepareRename(
            PrepareRenameParams params) {
        logger.trace("prepareRename for: {}", params.getTextDocument().getUri());
        ISourceLocation location = Locations.toLoc(params.getTextDocument());
        ILanguageContributions contribs = contributions(location);
        Position pos = params.getPosition();
        return getFile(location)
            .getCurrentTreeAsync(true) // It is the responsibility of the language contribution to handle the case where the tree contains parse errors
            .thenApply(Versioned::get)
            .thenCompose(tree -> computeRenameRange(contribs, pos.getLine(), pos.getCharacter(), tree))
            .thenApply(loc -> {
                if (loc.equals(URIUtil.unknownLocation())) {
                    throw new ResponseErrorException(new ResponseError(ResponseErrorCode.RequestFailed, "Rename not possible", pos));
                }
                return Either3.forFirst(Locations.toRange(loc, columns));
            });
    }

    private CompletableFuture<ISourceLocation> computeRenameRange(final ILanguageContributions contribs, final int startLine,
            final int startColumn, ITree tree) {
        IList focus = TreeSearch.computeFocusList(tree, startLine+1, startColumn);
        if (focus.isEmpty()) {
            throw new ResponseErrorException(new ResponseError(ResponseErrorCode.RequestFailed, "No focus found at " + startLine + ":" + startColumn,
                    TreeAdapter.getLocation(tree)));
        }
        return contribs.prepareRename(focus).get();
    }

    @Override
    public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
        logger.trace("rename for: {}, new name: {}", params.getTextDocument().getUri(), params.getNewName());
        ISourceLocation loc = Locations.toLoc(params.getTextDocument());
        ILanguageContributions contribs = contributions(loc);
        Position rascalPos = Locations.toRascalPosition(loc, params.getPosition(), columns);
        return getFile(loc)
                .getCurrentTreeAsync(true)
                .thenApply(Versioned::get)
                .thenCompose(tree -> computeRename(contribs,
                        rascalPos.getLine(), rascalPos.getCharacter(), params.getNewName(), tree));
    }

    private CompletableFuture<WorkspaceEdit> computeRename(final ILanguageContributions contribs, final int startLine,
            final int startColumn, String newName, ITree tree) {
        IList focus = TreeSearch.computeFocusList(tree, startLine, startColumn);
        if (focus.isEmpty()) {
            throw new ResponseErrorException(new ResponseError(ResponseErrorCode.RequestFailed, "No focus found at " + startLine + ":" + startColumn,
                    TreeAdapter.getLocation(tree)));
        }
        return contribs.rename(focus, newName)
                .thenApply(tuple -> {
                    IList documentEdits = (IList) tuple.get(0);
                    showMessages(availableClient(), (ISet) tuple.get(1));
                    return DocumentChanges.translateDocumentChanges(documentEdits, columns);
                })
                .get();
    }

    private static void showMessages(LanguageClient client, ISet messages) {
        // If any message is an error, throw a ResponseErrorException
        for (var msg : messages) {
            var message = (IConstructor) msg;
            if (message.getName().equals("error")) {
                throw new ResponseErrorException(
                    new ResponseError(ResponseErrorCode.RequestFailed, "Rename failed: " + ((IString) message.get("msg")).getValue(), null));
            }
        }

        for (var msg : messages) {
            client.showMessage(setMessageParams((IConstructor) msg));
        }
    }

    private static MessageParams setMessageParams(IConstructor message) {
        var params = new MessageParams();
        switch (message.getName()) {
            case "warning": {
                params.setType(MessageType.Warning);
                break;
            }
            case "info": {
                params.setType(MessageType.Info);
                break;
            }
            default: params.setType(MessageType.Log);
        }

        var msgText = ((IString) message.get("msg")).getValue();
        if (message.has("at")) {
            var at = ((ISourceLocation) message.get("at")).getURI();
            params.setMessage(String.format("%s (at %s)", msgText, at));
        } else {
            params.setMessage(msgText);
        }

        return params;
    }

    @Override
    public void didCreateFiles(CreateFilesParams params) {
        // This is fine, as long as `ParametricWorkspaceService` doet not claim to support the `didCreateFiles` capability.
        throw new UnsupportedOperationException("Unimplemented method 'didCreateFiles'");
    }

    @Override
    public void didRenameFiles(RenameFilesParams params, List<WorkspaceFolder> workspaceFolders) {
        Map<ILanguageContributions, List<FileRename>> byContrib =  bundleRenamesByContribution(params.getFiles());
        for (var entry : byContrib.entrySet()) {
            ILanguageContributions contrib = entry.getKey();
            List<FileRename> renames = entry.getValue();

            IList renameDocumentEdits = renames.stream().map(rename -> fileRenameToDocumentEdit(rename)).collect(VF.listWriter());

            contrib.didRenameFiles(renameDocumentEdits)
                .thenAccept(res -> {
                    var edits = (IList) res.get(0);
                    var messages = (ISet) res.get(1);
                    var client = availableClient();
                    showMessages(client, messages);

                    if (edits.isEmpty()) {
                        return;
                    }

                    WorkspaceEdit changes = DocumentChanges.translateDocumentChanges(edits, columns);
                    client.applyEdit(new ApplyWorkspaceEditParams(changes, "Rename files")).thenAccept(editResponse -> {
                        if (!editResponse.isApplied()) {
                            throw new RuntimeException("didRenameFiles resulted in a list of edits but applying them failed"
                                + (editResponse.getFailureReason() != null ? (": " + editResponse.getFailureReason()) : ""));
                        }
                    });
                })
                .get()
                .exceptionally(e -> {
                    var cause = e.getCause();
                    logger.catching(Level.ERROR, cause);
                    var message = "unknown error";
                    if (cause != null && cause.getMessage() != null) {
                        message = cause.getMessage();
                    }
                    availableClient().showMessage(new MessageParams(MessageType.Error, message));
                    return null; // Return of type `Void` is unused, but required
                });
        }
    }

    private Map<ILanguageContributions, List<FileRename>> bundleRenamesByContribution(List<FileRename> allRenames) {
        Map<ILanguageContributions, List<FileRename>> bundled = new HashMap<>();
        for (FileRename rename : allRenames) {
            var l = URIUtil.assumeCorrectLocation(rename.getNewUri());
            if (isLanguageRegistered(l)) {
                var language = language(l);
                ILanguageContributions contrib = contributions.get(language);
                if (contrib != null) {
                    bundled.computeIfAbsent(contrib, k -> new ArrayList<>()).add(rename);
                }
            }
        }

        return bundled;
    }

    private IConstructor fileRenameToDocumentEdit(FileRename rename) {
        ISourceLocation from = Locations.toLoc(rename.getOldUri());
        ISourceLocation to = Locations.toLoc(rename.getNewUri());
        return VF.constructor(renamedConstructor, from, to);
    }

    @Override
    public CompletableFuture<List<InlayHint>> inlayHint(InlayHintParams params) {
        logger.trace("inlayHint for: {}", params.getTextDocument().getUri());
        ISourceLocation loc = Locations.toLoc(params.getTextDocument());
        TextDocumentState file = getFile(loc);
        ILanguageContributions contrib = contributions(loc);
        return recoverExceptions(file.getLastTreeAsync(false)
                .thenApply(Versioned::get)
                .thenApply(contrib::inlayHint)
                .thenCompose(InterruptibleFuture::get)
                .thenApply(s -> s.stream()
                    .map(this::rowToInlayHint)
                    .collect(Collectors.toList())
            ), () -> null);
    }


    private static <T> CompletableFuture<T> recoverExceptions(CompletableFuture<T> future, Supplier<T> defaultValue) {
        return future
            .exceptionally(e -> {
                logger.error("Operation failed with", e);
                return defaultValue.get();
            });
    }

    private InlayHint rowToInlayHint(IValue v) {
        // unpack rascal value
        var t = (IConstructor) v;
        var loc =(ISourceLocation) t.get("position");
        var label = ((IString) t.get("label")).getValue();
        var kind = (IConstructor) t.get("kind");
        var tKW = t.asWithKeywordParameters();
        var toolTip = (IString)tKW.getParameter("toolTip");
        var atEnd = tKW.hasParameter("atEnd") && ((IBool)tKW.getParameter("atEnd")).getValue();

        // translate to lsp
        var result = new InlayHint(Locations.toPosition(loc, columns, atEnd), Either.forLeft(label.trim()));
        result.setKind(kind.getName().equals("type") ? InlayHintKind.Type : InlayHintKind.Parameter);
        result.setPaddingLeft(label.startsWith(" "));
        result.setPaddingRight(label.endsWith(" "));
        if (toolTip != null && toolTip.length() > 0) {
            result.setTooltip(toolTip.getValue());
        }
        return result;
    }

    private CodeLens locCommandTupleToCodeLense(String languageName, IValue v) {
        ITuple t = (ITuple) v;
        ISourceLocation loc = (ISourceLocation) t.get(0);
        IConstructor command = (IConstructor) t.get(1);

        return new CodeLens(Locations.toRange(loc, columns), CodeActions.constructorToCommand(dedicatedLanguageName, languageName, command), null);
    }

    private static <T> T last(List<T> l) {
        return l.get(l.size() - 1);
    }

    private boolean isLanguageRegistered(ISourceLocation loc) {
        return registeredExtensions.containsKey(extension(loc));
    }

    private Optional<String> safeLanguage(ISourceLocation loc) {
        return Optional.ofNullable(registeredExtensions.get(extension(loc)));
    }

    private String language(ISourceLocation loc) {
        return safeLanguage(loc).orElseThrow(() ->
            new UnsupportedOperationException(String.format("Rascal Parametric LSP has no support for this file, since no language is registered for extension '%s': %s", extension(loc), loc))
        );
    }

    private ILanguageContributions contributions(ISourceLocation doc) {
        return safeLanguage(doc)
            .map(contributions::get)
            .map(ILanguageContributions.class::cast)
            .flatMap(Optional::ofNullable)
            .orElseGet(() -> new NoContributions(extension(doc)));
    }

    private static String extension(ISourceLocation doc) {
        return URIUtil.getExtension(doc);
    }

    private ParametricFileFacts facts(ISourceLocation doc) {
        ParametricFileFacts fact = facts.get(language(doc));

        if (fact == null) {
            throw new ResponseErrorException(unknownFileError(doc, doc));
        }

        return fact;
    }

    private TextDocumentState open(TextDocumentItem doc, long timestamp) {
        return files.computeIfAbsent(Locations.toLoc(doc),
            l -> new TextDocumentState(contributions(l)::parsing, l, doc.getVersion(), doc.getText(), timestamp));
    }

    private TextDocumentState getFile(ISourceLocation loc) {
        TextDocumentState file = files.get(loc);
        if (file == null) {
            throw new ResponseErrorException(unknownFileError(loc, loc));
        }
        return file;
    }

    public void shutdown() {
        ownExecuter.shutdown();
    }

    private CompletableFuture<SemanticTokens> getSemanticTokens(TextDocumentIdentifier doc) {
        var loc = Locations.toLoc(doc);
        var specialCaseHighlighting = contributions(loc).specialCaseHighlighting();
        return recoverExceptions(getFile(loc).getCurrentTreeAsync(true)
                .thenApply(Versioned::get)
                .thenCombineAsync(specialCaseHighlighting, tokenizer::semanticTokensFull, ownExecuter)
                .whenComplete((r, e) ->
                    logger.trace("Semantic tokens success, reporting {} tokens back", r == null ? 0 : r.getData().size() / 5)
                )
            , () -> new SemanticTokens(Collections.emptyList()));
    }

    @Override
    public CompletableFuture<SemanticTokens> semanticTokensFull(SemanticTokensParams params) {
        logger.debug("semanticTokensFull: {}", params.getTextDocument());
        return getSemanticTokens(params.getTextDocument());
    }

    @Override
    public CompletableFuture<Either<SemanticTokens, SemanticTokensDelta>> semanticTokensFullDelta(
            SemanticTokensDeltaParams params) {
        logger.debug("semanticTokensFullDelta: {}", params.getTextDocument());
        return getSemanticTokens(params.getTextDocument()).thenApply(Either::forLeft);
    }

    @Override
    public CompletableFuture<SemanticTokens> semanticTokensRange(SemanticTokensRangeParams params) {
        logger.debug("semanticTokensRange: {}", params.getTextDocument());
        return getSemanticTokens(params.getTextDocument());
    }

    @Override
    public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(DocumentSymbolParams params) {
        logger.debug("Outline/documentSymbol: {}", params.getTextDocument());

        ISourceLocation location = Locations.toLoc(params.getTextDocument());
        TextDocumentState file = getFile(location);
        ILanguageContributions contrib = contributions(location);
        return recoverExceptions(file.getCurrentTreeAsync(true)
            .thenApply(Versioned::get)
            .thenApply(contrib::documentSymbol)
            .thenCompose(InterruptibleFuture::get)
            .thenApply(documentSymbols -> DocumentSymbols.toLSP(documentSymbols, columns.get(file.getLocation())))
            , Collections::emptyList);
    }

    @Override
    public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params) {
        logger.debug("codeAction: {}", params);

        var location = Locations.toLoc(params.getTextDocument());
        final ILanguageContributions contribs = contributions(location);

        var range = Locations.toRascalRange(location, params.getRange(), columns);

        // first we make a future stream for filtering out the "fixes" that were optionally sent along with earlier diagnostics
        // and which came back with the codeAction's list of relevant (in scope) diagnostics:
        // CompletableFuture<Stream<IValue>>
        var quickfixes = CodeActions.extractActionsFromDiagnostics(params, contribs::parseCodeActions);

        // here we dynamically ask the contributions for more actions,
        // based on the cursor position in the file and the current parse tree
        CompletableFuture<Stream<IValue>> codeActions = recoverExceptions(
            getFile(location)
                .getCurrentTreeAsync(true)
                .thenApply(Versioned::get)
                .thenCompose(tree -> computeCodeActions(contribs, range.getStart().getLine(), range.getStart().getCharacter(), tree))
                .thenApply(IList::stream)
            , Stream::empty)
            ;

        // final merging the two streams of commmands, and their conversion to LSP Command data-type
        return CodeActions.mergeAndConvertCodeActions(this, dedicatedLanguageName, contribs.getName(), quickfixes, codeActions);
    }

    private CompletableFuture<IList> computeCodeActions(final ILanguageContributions contribs, final int startLine, final int startColumn, ITree tree) {
        IList focus = TreeSearch.computeFocusList(tree, startLine, startColumn);

        if (!focus.isEmpty()) {
            return contribs.codeAction(focus).get();
        }
        else {
            logger.log(Level.DEBUG, "no tree focus found at {}:{}", startLine, startColumn);
            return CompletableFuture.completedFuture(VF.list());
        }
    }

    private <T> CompletableFuture<List<T>> lookup(SummaryLookup<T> lookup, TextDocumentIdentifier doc, Position cursor) {
        var loc = Locations.toLoc(doc);
        return getFile(loc)
            .getCurrentTreeAsync(true)
            .thenApply(tree -> facts(loc).lookupInSummaries(lookup, loc, tree, cursor))
            .thenCompose(Function.identity());
    }

    @Override
    public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(DefinitionParams params) {
        logger.debug("Definition: {} at {}", params.getTextDocument(), params.getPosition());
        return recoverExceptions(
            lookup(ParametricSummary::definitions, params.getTextDocument(), params.getPosition())
            .thenApply(d -> {
                logger.debug("Definitions: {}", d);
                return d;
            })
            .thenApply(Either::forLeft)
            , () -> Either.forLeft(Collections.emptyList()));
    }

    @Override
    public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> implementation(ImplementationParams params) {
        logger.debug("Implementation: {} at {}", params.getTextDocument(), params.getPosition());
        return recoverExceptions(
            lookup(ParametricSummary::implementations, params.getTextDocument(), params.getPosition())
            .thenApply(Either::forLeft)
            , () -> Either.forLeft(Collections.emptyList()));
    }

    @Override
    public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
        logger.debug("References: {} at {}", params.getTextDocument(), params.getPosition());
        return recoverExceptions(
            lookup(ParametricSummary::references, params.getTextDocument(), params.getPosition())
            .thenApply(l -> l) // hack to help compiler see type
            , Collections::emptyList);
    }

    @Override
    public CompletableFuture<@Nullable Hover> hover(HoverParams params) {
        logger.debug("Hover: {} at {}", params.getTextDocument(), params.getPosition());
        return recoverExceptions(
            lookup(ParametricSummary::hovers, params.getTextDocument(), params.getPosition())
            .thenApply(Hover::new)
            , () -> null);
    }

    @Override
    public CompletableFuture<List<FoldingRange>> foldingRange(FoldingRangeRequestParams params) {
        logger.debug("Folding range: {}", params.getTextDocument());
        TextDocumentState file = getFile(Locations.toLoc(params.getTextDocument()));
        return recoverExceptions(file.getCurrentTreeAsync(true).thenApply(Versioned::get).thenApply(FoldingRanges::getFoldingRanges)
            .whenComplete((r, e) ->
                logger.trace("Folding regions success, reporting {} regions back", r == null ? 0 : r.size())
            ), Collections::emptyList);
    }

    @Override
    public CompletableFuture<List<SelectionRange>> selectionRange(SelectionRangeParams params) {
        logger.debug("Selection range: {} at {}", params.getTextDocument(), params.getPositions());
        ISourceLocation loc = Locations.toLoc(params.getTextDocument());
        ILanguageContributions contrib = contributions(loc);
        TextDocumentState file = getFile(loc);

        CompletableFuture<Function<IList, CompletableFuture<IList>>> computeSelection = contrib.hasSelectionRange().thenApply(hasDef -> {
            if (!hasDef.booleanValue()) {
                logger.debug("Selection range not implemented; falling back to default implementation ({})", params.getTextDocument());
                return focus -> CompletableFuture.completedFuture(SelectionRanges.uniqueTreeLocations(focus));
            }
            return focus -> contrib.selectionRange(focus).get();
        });

        return recoverExceptions(file.getCurrentTreeAsync(true)
                .thenApply(Versioned::get)
                .thenCompose(t -> CompletableFutureUtils.reduce(params.getPositions().stream()
                    .map(p -> Locations.toRascalPosition(loc, p, columns))
                    .map(p -> computeSelection
                        .thenCompose(compute -> compute.apply(TreeSearch.computeFocusList(t, p.getLine(), p.getCharacter())))
                        .thenApply(selection -> SelectionRanges.toSelectionRange(p, selection, columns)))
                    .collect(Collectors.toUnmodifiableList()))),
            Collections::emptyList);
    }

    @Override
    public CompletableFuture<List<CallHierarchyItem>> prepareCallHierarchy(CallHierarchyPrepareParams params) {
        final var loc = Locations.toLoc(params.getTextDocument());
        final var contrib = contributions(loc);
        final var file = getFile(loc);

        return recoverExceptions(file.getCurrentTreeAsync(true)
            .thenApply(Versioned::get)
            .thenCompose(t -> {
                final var pos = Locations.toRascalPosition(loc, params.getPosition(), columns);
                return contrib.prepareCallHierarchy(TreeSearch.computeFocusList(t, pos.getLine(), pos.getCharacter()))
                    .get()
                    .thenApply(items -> {
                        var ch = new CallHierarchy();
                        return items.stream()
                            .map(IConstructor.class::cast)
                            .map(ci -> ch.toLSP(ci, columns))
                            .collect(Collectors.toList());
                    });
            }), Collections::emptyList);
    }

    private <T> CompletableFuture<List<T>> incomingOutgoingCalls(BiFunction<CallHierarchyItem, List<Range>, T> constructor, CallHierarchyItem source, CallHierarchy.Direction direction) {
        final var contrib = contributions(Locations.toLoc(source.getUri()));
        var ch = new CallHierarchy();
        return ch.toRascal(source, contrib::parseCallHierarchyData, columns)
            .thenCompose(sourceItem -> contrib.incomingOutgoingCalls(sourceItem, ch.direction(direction)).get())
            .thenApply(callRel -> callRel.stream()
                .map(ITuple.class::cast)
                // Collect call sites (value) by associated definition (key) as a map
                .collect(Collectors.toMap(
                    t -> ch.toLSP((IConstructor) t.get(0), columns),
                    t -> List.of(Locations.toRange((ISourceLocation) t.get(1), columns)),
                    Lists::union,
                    LinkedHashMap::new
                ))
                .entrySet().stream()
                .map(e -> constructor.apply(e.getKey(), e.getValue()))
                .collect(Collectors.toList()));
    }

    @Override
    public CompletableFuture<List<CallHierarchyIncomingCall>> callHierarchyIncomingCalls(CallHierarchyIncomingCallsParams params) {
        return recoverExceptions(incomingOutgoingCalls(CallHierarchyIncomingCall::new, params.getItem(), CallHierarchy.Direction.INCOMING), Collections::emptyList);
    }

    @Override
    public CompletableFuture<List<CallHierarchyOutgoingCall>> callHierarchyOutgoingCalls(CallHierarchyOutgoingCallsParams params) {
        return recoverExceptions(incomingOutgoingCalls(CallHierarchyOutgoingCall::new, params.getItem(), CallHierarchy.Direction.OUTGOING), Collections::emptyList);
    }


    @Override
    public synchronized void registerLanguage(LanguageParameter lang) {
        logger.info("registerLanguage({})/begin", lang.getName());

        var multiplexer = contributions.computeIfAbsent(lang.getName(),
            t -> new LanguageContributionsMultiplexer(lang.getName(), ownExecuter)
        );
        var fact = facts.computeIfAbsent(lang.getName(), t ->
            new ParametricFileFacts(ownExecuter, columns, multiplexer)
        );

        var parserConfig = lang.getPrecompiledParser();
        if (parserConfig != null) {
            try {
                var location = parserConfig.getParserLocation();
                if (URIResolverRegistry.getInstance().exists(location)) {
                    logger.debug("Got precompiled definition: {}", parserConfig);
                    multiplexer.addContributor(buildContributionKey(lang) + "$parser", new ParserOnlyContribution(lang.getName(), parserConfig, ownExecuter));
                }
                else {
                    logger.error("Defined precompiled parser ({}) does not exist", parserConfig);
                }
            }
            catch (FactParseError e) {
                logger.error("Error parsing location in precompiled parser specification (we expect a rascal loc)", e);
            }
        }

        var clientCopy = availableClient();
        multiplexer.addContributor(buildContributionKey(lang),
            new InterpretedLanguageContributions(lang, this, availableWorkspaceService(), (IBaseLanguageClient)clientCopy, ownExecuter));

        fact.reloadContributions();
        fact.setClient(clientCopy);

        for (var extension: lang.getExtensions()) {
            this.registeredExtensions.put(extension, lang.getName());
        }

        // If we opened any files with this extension before, now associate them with contributions
        var extensions = Arrays.asList(lang.getExtensions());
        for (var f : files.keySet()) {
            if (extensions.contains(extension(f))) {
                updateFileState(lang, f);
            }
        }

        logger.info("registerLanguage({})/end", lang.getName());
    }

    private void updateFileState(LanguageParameter lang, ISourceLocation f) {
        logger.trace("File of language {} - updating state: {}", lang.getName(), f);
        // Since we cannot know what happened to this file before we were called, we need to be careful about races.
        // It might have been closed in the meantime, so we compute the new value if the key still exists, based on the current value.
        var state = files.computeIfPresent(f, (loc, currentState) -> currentState.changeParser(contributions(loc)::parsing));
        if (state == null) {
            logger.debug("Updating the parser of {} failed, since it was closed.", f);
            return;
        }
        // Update open editor
        handleParsingErrors(state, state.getCurrentDiagnosticsAsync());
        triggerAnalyzer(f, state.getCurrentContent().version(), NORMAL_DEBOUNCE);
    }

    private static String buildContributionKey(LanguageParameter lang) {
        return lang.getMainFunction() + "::" + lang.getMainFunction();
    }

    @Override
    public synchronized void unregisterLanguage(LanguageParameter lang) {
        logger.info("unregisterLanguage({})/begin", lang.getName());
        boolean removeAll = lang.getMainModule() == null || lang.getMainModule().isEmpty();
        if (!removeAll) {
            var contrib = contributions.get(lang.getName());
            if (contrib != null && !contrib.removeContributor(buildContributionKey(lang))) {
                logger.error("unregisterLanguage cleared everything, so removing all");
                // ok, so it was a clear after all
                removeAll = true;
            }
            else {
                var fact = facts.get(lang.getName());
                if (fact != null) {
                    fact.reloadContributions();
                }
            }
        }
        if (removeAll) {
            // clear the whole language
            logger.trace("unregisterLanguage({}) completely", lang.getName());

            for (var extension : lang.getExtensions()) {
                this.registeredExtensions.remove(extension);
            }
            facts.remove(lang.getName());
            contributions.remove(lang.getName());
        }
        logger.info("unregisterLanguage({})/end", lang.getName());
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
    public CompletableFuture<IValue> executeCommand(String languageName, String command) {
        ILanguageContributions contribs = contributions.get(languageName);

        if (contribs != null) {
            return contribs.execution(command).get();
        }
        else {
            logger.warn("ignoring command execution (no contributor configured for this language): {}, {} ", languageName, command);
            return CompletableFuture.completedFuture(IRascalValueFactory.getInstance().string("No contributions configured for the language: " + languageName));
        }
    }

    @Override
    public boolean isManagingFile(ISourceLocation file) {
        return files.containsKey(file.top());
    }

    @Override
    public @Nullable TextDocumentState getDocumentState(ISourceLocation file) {
        return files.get(file.top());
    }

    @Override
    public void cancelProgress(String progressId) {
        contributions.values().forEach(plex ->
            plex.cancelProgress(progressId));
    }

    private ResponseError unknownFileError(ISourceLocation loc, Object data) {
        return new ResponseError(ResponseErrorCode.RequestFailed, "Unknown file: " + loc, data);
    }
}
