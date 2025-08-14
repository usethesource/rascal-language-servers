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
package org.rascalmpl.vscode.lsp.rascal;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensOptions;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.ExecuteCommandOptions;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeRequestParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PrepareRenameDefaultBehavior;
import org.eclipse.lsp4j.PrepareRenameParams;
import org.eclipse.lsp4j.PrepareRenameResult;
import org.eclipse.lsp4j.Range;
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
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.Either3;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.rascalmpl.library.Prelude;
import org.rascalmpl.library.util.PathConfig;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.values.parsetrees.ProductionAdapter;
import org.rascalmpl.values.parsetrees.TreeAdapter;
import org.rascalmpl.vscode.lsp.BaseWorkspaceService;
import org.rascalmpl.vscode.lsp.IBaseLanguageClient;
import org.rascalmpl.vscode.lsp.IBaseTextDocumentService;
import org.rascalmpl.vscode.lsp.TextDocumentState;
import org.rascalmpl.vscode.lsp.rascal.RascalLanguageServices.CodeLensSuggestion;
import org.rascalmpl.vscode.lsp.rascal.model.FileFacts;
import org.rascalmpl.vscode.lsp.rascal.model.SummaryBridge;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.LanguageParameter;
import org.rascalmpl.vscode.lsp.uri.FallbackResolver;
import org.rascalmpl.vscode.lsp.util.CodeActions;
import org.rascalmpl.vscode.lsp.util.Diagnostics;
import org.rascalmpl.vscode.lsp.util.DocumentChanges;
import org.rascalmpl.vscode.lsp.util.DocumentSymbols;
import org.rascalmpl.vscode.lsp.util.FoldingRanges;
import org.rascalmpl.vscode.lsp.util.SelectionRanges;
import org.rascalmpl.vscode.lsp.util.SemanticTokenizer;
import org.rascalmpl.vscode.lsp.util.Versioned;
import org.rascalmpl.vscode.lsp.util.locations.ColumnMaps;
import org.rascalmpl.vscode.lsp.util.locations.LineColumnOffsetMap;
import org.rascalmpl.vscode.lsp.util.locations.Locations;
import org.rascalmpl.vscode.lsp.util.locations.impl.TreeSearch;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.IValue;

public class RascalTextDocumentService implements IBaseTextDocumentService, LanguageClientAware {
    private static final Logger logger = LogManager.getLogger(RascalTextDocumentService.class);
    private final ExecutorService ownExecuter;
    private @MonotonicNonNull RascalLanguageServices rascalServices;

    private final SemanticTokenizer tokenizer = new SemanticTokenizer(true);
    private @MonotonicNonNull LanguageClient client;

    private final Map<ISourceLocation, TextDocumentState> documents;
    private final ColumnMaps columns;
    private @MonotonicNonNull FileFacts facts;
    private @MonotonicNonNull BaseWorkspaceService workspaceService;

    public RascalTextDocumentService(ExecutorService exec) {
        // The following call ensures that URIResolverRegistry is initialized before FallbackResolver is accessed
        URIResolverRegistry.getInstance();

        this.ownExecuter = exec;
        this.documents = new ConcurrentHashMap<>();
        this.columns = new ColumnMaps(this::getContents);
        FallbackResolver.getInstance().registerTextDocumentService(this);
    }

    @Override
    public LineColumnOffsetMap getColumnMap(ISourceLocation file) {
        return columns.get(file);
    }

    public String getContents(ISourceLocation file) {
        file = file.top();
        TextDocumentState ideState = documents.get(file);
        if (ideState != null) {
            return ideState.getCurrentContent().get();
        }

        if (!URIResolverRegistry.getInstance().isFile(file)) {
            logger.error("Trying to get the contents of a directory: {}", file);
            return "";
        }

        try (Reader src = URIResolverRegistry.getInstance().getCharacterReader(file)) {
            return Prelude.consumeInputStream(src);
        }
        catch (IOException e) {
            logger.error("Error opening file {} to get contents", file, e);
            return "";
        }
    }

    public void initializeServerCapabilities(ServerCapabilities result) {
        result.setDefinitionProvider(true);
        result.setTextDocumentSync(TextDocumentSyncKind.Full);
        result.setDocumentSymbolProvider(true);
        result.setHoverProvider(true);
        result.setSemanticTokensProvider(tokenizer.options());
        result.setCodeLensProvider(new CodeLensOptions(false));
        result.setFoldingRangeProvider(true);
        result.setRenameProvider(new RenameOptions(true));
        result.setCodeActionProvider(true);
        result.setExecuteCommandProvider(new ExecuteCommandOptions(Collections.singletonList(BaseWorkspaceService.RASCAL_COMMAND)));
        result.setSelectionRangeProvider(true);
    }

    @Override
    public void pair(BaseWorkspaceService workspaceService) {
        this.workspaceService = workspaceService;

    }

    @Override
    public void connect(LanguageClient client) {
        this.client = client;
        this.rascalServices = new RascalLanguageServices(this, workspaceService, (IBaseLanguageClient) client, ownExecuter);
        this.facts = new FileFacts(ownExecuter, rascalServices, columns);
        facts.setClient(client);
    }

    // LSP interface methods

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        var timestamp = System.currentTimeMillis();
        logger.debug("Open: {}", params.getTextDocument());
        TextDocumentState file = open(params.getTextDocument(), timestamp);
        handleParsingErrors(file, file.getCurrentDiagnosticsAsync());
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        var timestamp = System.currentTimeMillis();
        logger.trace("Change: {}", params.getTextDocument());
        updateContents(params.getTextDocument(), last(params.getContentChanges()).getText(), timestamp);
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        logger.debug("Close: {}", params.getTextDocument());
        if (documents.remove(Locations.toLoc(params.getTextDocument())) == null) {
            throw new ResponseErrorException(new ResponseError(ResponseErrorCode.InternalError,
                "Unknown file: " + Locations.toLoc(params.getTextDocument()), params));
        }
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        logger.debug("Save: {}", params.getTextDocument());
        // on save we don't get new file contents, that comes in via change
        // but we do trigger the type checker on save
        if (facts != null) {
            facts.invalidate(Locations.toLoc(params.getTextDocument()));
        }
    }

    private TextDocumentState updateContents(VersionedTextDocumentIdentifier doc, String newContents, long timestamp) {
        TextDocumentState file = getFile(doc);
        logger.trace("New contents for {}", doc);
        handleParsingErrors(file, file.update(doc.getVersion(), newContents, timestamp));
        return file;
    }

    private void handleParsingErrors(TextDocumentState file, CompletableFuture<Versioned<List<Diagnostics.Template>>> diagnosticsAsync) {
        diagnosticsAsync.thenAccept(diagnostics -> {
            List<Diagnostic> parseErrors = diagnostics.get().stream()
                .map(diagnostic -> diagnostic.instantiate(columns))
                .collect(Collectors.toList());

            logger.trace("Finished parsing tree, reporting new parse errors: {} for: {}", parseErrors, file.getLocation());
            if (facts != null) {
                facts.reportParseErrors(file.getLocation(), parseErrors);
            }
        });
    }

    @Override
    public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(DefinitionParams params) {
        logger.debug("textDocument/definition: {} at {}", params.getTextDocument(), params.getPosition());

        if (facts != null) {
            return facts.getSummary(Locations.toLoc(params.getTextDocument()))
                .thenApply(s -> s == null ? Collections.<Location>emptyList() : s.getDefinition(params.getPosition()))
                .thenApply(Either::forLeft)
                ;
        }
        else {
            return CompletableFuture.completedFuture(Either.forLeft(Collections.emptyList()));
        }
    }

    @Override
    public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>>
        documentSymbol(DocumentSymbolParams params) {
        logger.debug("textDocument/documentSymbol: {}", params.getTextDocument());
        TextDocumentState file = getFile(params.getTextDocument());
        return file.getCurrentTreeAsync()
            .thenApply(Versioned::get)
            .handle((t, r) -> (t == null ? (file.getLastTree().get()) : t))
            .thenCompose(tr -> rascalServices.getDocumentSymbols(tr).get())
            .thenApply(documentSymbols -> DocumentSymbols.toLSP(documentSymbols, columns.get(file.getLocation())))
            ;
    }

    private ITree findQualifiedNameUnderCursor(IList focusList) {
        List<String> sortNames = focusList.stream()
            .map(ITree.class::cast)
            .map(TreeAdapter::getProduction)
            .map(ProductionAdapter::getSortName)
            .collect(Collectors.toList());

        int qNameIdx = sortNames.indexOf("QualifiedName");
        if (qNameIdx != -1) {
            // Cursor is at a qualified name
            ITree qualifiedName = (ITree) focusList.get(qNameIdx);

            // If the qualified name is in a header, but not in module parameters or a syntax defintion, it is a full module path
            if (sortNames.contains("Header") && !(sortNames.contains("ModuleParameters") || sortNames.contains("SyntaxDefinition"))) {
                return qualifiedName;
            }

            // Since the cursor is not in a header, the qualified name consists of a declaration name on the right, and an optional module path prefix.
            IList names = TreeAdapter.getListASTArgs(TreeAdapter.getArg(qualifiedName, "names"));

            // Even if the cursor is on the module prefix, we steer towards renaming the declaration
            return (ITree) names.get(names.size() - 1);
        }

        switch (sortNames.get(0)) {
            case "Name": // intentional fall-through
            case "Nonterminal": // intentional fall-through
            case "NonterminalLabel": {
                // Return name location
                return (ITree) focusList.get(0);
            }
            default: return null;
        }
    }

    @Override
    public CompletableFuture<Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>> prepareRename(PrepareRenameParams params) {
        logger.debug("textDocument/prepareRename: {} at {}", params.getTextDocument(), params.getPosition());
        TextDocumentState file = getFile(params.getTextDocument());

        return file.getCurrentTreeAsync()
            .thenApply(Versioned::get)
            .handle((t, r) -> (t == null ? file.getLastTreeWithoutErrors().get() : t))
            .thenApply(tr -> {
                Position rascalCursorPos = Locations.toRascalPosition(file.getLocation(), params.getPosition(), columns);
                IList focus = TreeSearch.computeFocusList(tr, rascalCursorPos.getLine(), rascalCursorPos.getCharacter());
                return findQualifiedNameUnderCursor(focus);
            })
            .thenApply(cur -> DocumentChanges.locationToRange(this, TreeAdapter.getLocation(cur)))
            .thenApply(Either3::forFirst);
    }

    @Override
    public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
        logger.debug("textDocument/rename: {} at {} to {}", params.getTextDocument(), params.getPosition(), params.getNewName());

        TextDocumentState file = getFile(params.getTextDocument());
        Set<ISourceLocation> workspaceFolders = workspaceService.workspaceFolders()
            .stream()
            .map(f -> Locations.toLoc(f.getUri()))
            .collect(Collectors.toSet());

        return file.getCurrentTreeAsync()
            .thenApply(Versioned::get)
            .handle((t, r) -> (t == null ? file.getLastTreeWithoutErrors().get() : t))
            .thenCompose(tr -> {
                Position rascalCursorPos = Locations.toRascalPosition(file.getLocation(), params.getPosition(), columns);
                var focus = TreeSearch.computeFocusList(tr, rascalCursorPos.getLine(), rascalCursorPos.getCharacter());
                var cursorTree = findQualifiedNameUnderCursor(focus);
                return rascalServices.getRename(TreeAdapter.getLocation(cursorTree), focus, workspaceFolders, facts::getPathConfig, params.getNewName()).get();
            })
            .thenApply(t -> {
                showMessages((ISet) t.get(1));
                return DocumentChanges.translateDocumentChanges(this, (IList) t.get(0));
            });
    }

    private void showMessages(ISet messages) {
        for (var msg : messages) {
            client.showMessage(setMessageParams((IConstructor) msg));
        }
    }

    private MessageParams setMessageParams(IConstructor message) {
        var params = new MessageParams();
        switch (message.getName()) {
            case "error": {
                params.setType(MessageType.Error);
                break;
            }
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
    public CompletableFuture<Hover> hover(HoverParams params) {
        logger.debug("textDocument/hover: {} at {}", params.getTextDocument(), params.getPosition());
        if (facts != null) {
            return facts.getSummary(Locations.toLoc(params.getTextDocument()))
                .handle((t, r) -> (t == null ? (new SummaryBridge()) : t))
                .thenApply(s -> s.getTypeName(params.getPosition()))
                .thenApply(n -> new Hover(new MarkupContent("plaintext", n)));
        }
        else {
            return CompletableFuture.completedFuture(null);
        }
    }

    @Override
    public CompletableFuture<List<FoldingRange>> foldingRange(FoldingRangeRequestParams params) {
        logger.debug("textDocument/foldingRange: {}", params.getTextDocument());
        TextDocumentState file = getFile(params.getTextDocument());
        return file.getCurrentTreeAsync().thenApply(Versioned::get).thenApplyAsync(FoldingRanges::getFoldingRanges)
            .exceptionally(e -> {
                logger.error("Tokenization failed", e);
                return new ArrayList<>();
            })
            .whenComplete((r, e) ->
                logger.trace("Folding regions success, reporting {} regions back", r == null ? 0 : r.size())
            );
    }

    @Override
    public void didRenameFiles(RenameFilesParams params, Set<ISourceLocation> workspaceFolders) {
        logger.debug("workspace/didRenameFiles: {}", params.getFiles());

        rascalServices.getModuleRenames(params.getFiles(), workspaceFolders, facts::getPathConfig)
            .thenAccept(res -> {
                var edits = (IList) res.get(0);
                var messages = (ISet) res.get(1);
                showMessages(messages);

                if (edits.size() == 0) {
                    return;
                }

                var changes = DocumentChanges.translateDocumentChanges(this, edits);
                client.applyEdit(new ApplyWorkspaceEditParams(changes)).thenAccept(editResponse -> {
                    if (!editResponse.isApplied()) {
                        throw new RuntimeException("Applying module rename failed" + (editResponse.getFailureReason() != null ? (": " + editResponse.getFailureReason()) : ""));
                    }
                });
            })
            .exceptionally(e -> {
                logger.catching(Level.ERROR, e.getCause());
                client.showMessage(new MessageParams(MessageType.Error, e.getCause().getMessage()));
                return null; // Return of type `Void` is unused, but required
            });
    }

    // Private utility methods

    private static <T> T last(List<T> l) {
        return l.get(l.size() - 1);
    }

    private TextDocumentState open(TextDocumentItem doc, long timestamp) {
        return documents.computeIfAbsent(Locations.toLoc(doc),
            l -> new TextDocumentState((loc, input) -> rascalServices.parseSourceFile(loc, input), l, doc.getVersion(), doc.getText(), timestamp));
    }

    private TextDocumentState getFile(TextDocumentIdentifier doc) {
        return getFile(Locations.toLoc(doc));
    }

    protected TextDocumentState getFile(ISourceLocation loc) {
        TextDocumentState file = documents.get(loc);
        if (file == null) {
            throw new ResponseErrorException(new ResponseError(-1, "Unknown file: " + loc, loc));
        }
        return file;
    }

    public void shutdown() {
        ownExecuter.shutdown();
    }

    private CompletableFuture<SemanticTokens> getSemanticTokens(TextDocumentIdentifier doc) {
        var specialCaseHighlighting = CompletableFuture.completedFuture(false);
        return getFile(doc).getCurrentTreeAsync()
                .thenApply(Versioned::get)
                .thenCombineAsync(specialCaseHighlighting, tokenizer::semanticTokensFull, ownExecuter)
                .exceptionally(e -> {
                    logger.error("Tokenization failed", e);
                    return new SemanticTokens(Collections.emptyList());
                })
                .whenComplete((r, e) ->
                    logger.trace("Semantic tokens success, reporting {} tokens back", r == null ? 0 : r.getData().size())
                );
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
    public CompletableFuture<List<SelectionRange>> selectionRange(SelectionRangeParams params) {
        logger.debug("textDocument/selectionRange: {}", params);
        TextDocumentState file = getFile(params.getTextDocument());
        return file.getCurrentTreeAsync()
            .thenApply(Versioned::get)
            .handle((t, r) -> (t == null ? file.getLastTreeWithoutErrors().get() : t))
            .thenApply(tr -> params.getPositions().stream()
                .map(p -> Locations.toRascalPosition(file.getLocation(), p, columns))
                .map(p -> TreeSearch.computeFocusList(tr, p.getLine(), p.getCharacter()))
                .map(focus -> rascalServices.getSelectionsForFocus(focus))
                .map(l -> SelectionRanges.toSelectionRange(l, columns))
                .collect(Collectors.toList()));
    }

    @Override
    public void registerLanguage(LanguageParameter lang) {
        throw new UnsupportedOperationException("registering language is a feature of the language parametric server, not of the Rascal server");
    }

    @Override
    public void unregisterLanguage(LanguageParameter lang) {
        throw new UnsupportedOperationException("registering language is a feature of the language parametric server, not of the Rascal server");
    }

    @Override
    public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params) {
        TextDocumentState f = getFile(params.getTextDocument());
        return f.getCurrentTreeAsync()
            .handle((r, e) -> {
                // fallback to tree if a parsing error occurred.
                if (r == null) {
                    r = f.getLastTreeWithoutErrors();
                }
                if (r == null) {
                    throw new RuntimeException(e);
                }
                return r;
            })
            // Replace with the last tree without errors, might be the same as `tree` if the parse succeeded without any error recovery
            .thenApply(tree -> f.getLastTreeWithoutErrors())
            .thenApply(Versioned::get)
            .thenApplyAsync(rascalServices::locateCodeLenses, ownExecuter)
            .thenApply(List::stream)
            .thenApply(res -> res.map(this::makeRunCodeLens))
            .thenApply(s -> s.collect(Collectors.toList()))
            .exceptionally(e -> {
                logger.trace("Code lens failed", e);
                return null;
            })
            .thenApply(c -> c) // work around for compiler with generics
            ;
    }

    @Override
    public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params) {
        logger.debug("codeAction: {}", params);

        var range = Locations.toRascalRange(params.getTextDocument(), params.getRange(), columns);
        var loc = Locations.toLoc(params.getTextDocument());


        // first we make a future stream for filtering out the "fixes" that were optionally sent along with earlier diagnostics
        // and which came back with the codeAction's list of relevant (in scope) diagnostics:
        // CompletableFuture<Stream<IValue>>
        CompletableFuture<Stream<IValue>> quickfixes
            = CodeActions.extractActionsFromDiagnostics(params, rascalServices::parseCodeActions);

        // here we dynamically ask the contributions for more actions,
        // based on the cursor position in the file and the current parse tree
        CompletableFuture<Stream<IValue>> codeActions = recoverExceptions(
            getFile(params.getTextDocument())
                .getCurrentTreeAsync()
                .thenApply(Versioned::get)
                .thenCompose((ITree tree) -> computeCodeActions(range.getStart().getLine(), range.getStart().getCharacter(), tree, facts.getPathConfig(loc)))
                .thenApply(IList::stream)
            , Stream::empty)
            ;

        // final merging the two streams of commmands, and their conversion to LSP Command data-type
        return CodeActions.mergeAndConvertCodeActions(this, "", BaseWorkspaceService.RASCAL_LANGUAGE, quickfixes, codeActions);
    }

    private CompletableFuture<IList> computeCodeActions(final int startLine, final int startColumn, ITree tree, PathConfig pcfg) {
        return CompletableFuture.<IList>supplyAsync(() -> TreeSearch.computeFocusList(tree, startLine, startColumn))
            .thenCompose(focus -> focus.isEmpty()
                ? CompletableFuture.completedFuture(focus /* an empty list */)
                : rascalServices.codeActions(focus, pcfg).get());
    }

    private CodeLens makeRunCodeLens(CodeLensSuggestion detected) {
        return new CodeLens(
            Locations.toRange(detected.getLine(), columns),
            new Command(detected.getShortName(), detected.getCommandName(), detected.getArguments()),
            null
        );
    }

    @Override
    public CompletableFuture<IValue> executeCommand(String extension, String command) {
        return rascalServices.executeCommand(command).get();
    }

    private static <T> CompletableFuture<T> recoverExceptions(CompletableFuture<T> future, Supplier<T> defaultValue) {
        return future
                .exceptionally(e -> {
                    logger.error("Operation failed with", e);
                    return defaultValue.get();
                });
    }

    @Override
    public boolean isManagingFile(ISourceLocation file) {
        return documents.containsKey(file.top());
    }

    @Override
    public TextDocumentState getDocumentState(ISourceLocation file) {
        return documents.get(file.top());
    }

    public @MonotonicNonNull FileFacts getFileFacts() {
        return facts;
    }

    @Override
    public void cancelProgress(String progressId) {
        this.rascalServices.cancelProgress(progressId);
    }
}
