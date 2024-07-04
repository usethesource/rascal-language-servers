/*
 * Copyright (c) 2018-2023, NWO-I CWI and Swat.engineering
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.util.IOUtils;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensOptions;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
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
import org.eclipse.lsp4j.ImplementationParams;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintKind;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ReferenceParams;
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
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.rascalmpl.exceptions.Throw;
import org.rascalmpl.parser.gtd.exception.ParseError;
import org.rascalmpl.uri.URIResolverRegistry;
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
import org.rascalmpl.vscode.lsp.util.Diagnostics;
import org.rascalmpl.vscode.lsp.util.FoldingRanges;
import org.rascalmpl.vscode.lsp.util.Outline;
import org.rascalmpl.vscode.lsp.util.SemanticTokenizer;
import org.rascalmpl.vscode.lsp.util.Versioned;
import org.rascalmpl.vscode.lsp.util.concurrent.InterruptibleFuture;
import org.rascalmpl.vscode.lsp.util.locations.ColumnMaps;
import org.rascalmpl.vscode.lsp.util.locations.LineColumnOffsetMap;
import org.rascalmpl.vscode.lsp.util.locations.Locations;

import com.google.gson.JsonPrimitive;

import io.usethesource.vallang.IBool;
import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.ITuple;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IWithKeywordParameters;
import io.usethesource.vallang.exceptions.FactParseError;

public class ParametricTextDocumentService implements IBaseTextDocumentService, LanguageClientAware {
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

    public ParametricTextDocumentService(ExecutorService exec, @Nullable LanguageParameter dedicatedLanguage) {
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
    }

    @Override
    public LineColumnOffsetMap getColumnMap(ISourceLocation file) {
        return columns.get(file);
    }

    private String getContents(ISourceLocation file) {
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
        result.setExecuteCommandProvider(new ExecuteCommandOptions(Collections.singletonList(getRascalMetaCommandName())));

        result.setFoldingRangeProvider(true);
        result.setInlayHintProvider(true);
    }

    private String getRascalMetaCommandName() {
        // if we run in dedicated mode, we prefix the commands with our language name
        // to avoid ambiguity with other dedicated languages and the generic rascal plugin
        if (!dedicatedLanguageName.isEmpty()) {
            return BaseWorkspaceService.RASCAL_META_COMMAND + "-" + dedicatedLanguageName;
        }
        return BaseWorkspaceService.RASCAL_META_COMMAND;
    }

    @Override
    public void pair(BaseWorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
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

    // LSP interface methods

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        logger.debug("Did Open file: {}", params.getTextDocument());
        handleParsingErrors(open(params.getTextDocument()));
        triggerAnalyzer(params.getTextDocument(), Duration.ofMillis(800));
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        logger.debug("Did Change file: {}", params.getTextDocument().getUri());
        updateContents(params.getTextDocument(), last(params.getContentChanges()).getText());
        triggerAnalyzer(params.getTextDocument(), Duration.ofMillis(800));
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
        if (files.remove(Locations.toLoc(params.getTextDocument())) == null) {
            throw new ResponseErrorException(new ResponseError(ResponseErrorCode.InternalError,
                "Unknown file: " + Locations.toLoc(params.getTextDocument()), params));
        }
        facts(params.getTextDocument()).close(Locations.toLoc(params.getTextDocument()));
    }

    private void triggerAnalyzer(TextDocumentItem doc, Duration delay) {
        triggerAnalyzer(new VersionedTextDocumentIdentifier(doc.getUri(), doc.getVersion()), delay);
    }
    private void triggerAnalyzer(VersionedTextDocumentIdentifier doc, Duration delay) {
        logger.trace("Triggering analyzer for {}", doc.getUri());
        var fileFacts = facts(doc);
        var location = Locations.toLoc(doc);
        fileFacts.invalidateAnalyzer(location);
        fileFacts.calculateAnalyzer(location, getFile(doc).getCurrentTreeAsync(), doc.getVersion(), delay);
    }

    private void triggerBuilder(TextDocumentIdentifier doc) {
        logger.trace("Triggering builder for {}", doc.getUri());
        var fileFacts = facts(doc);
        var location = Locations.toLoc(doc);
        fileFacts.invalidateBuilder(location);
        fileFacts.calculateBuilder(location, getFile(doc).getCurrentTreeAsync());
    }

    private TextDocumentState updateContents(VersionedTextDocumentIdentifier doc, String newContents) {
        TextDocumentState file = getFile(doc);
        logger.trace("New contents for {}", doc);
        handleParsingErrors(file, file.update(doc.getVersion(), newContents));
        return file;
    }

    private void handleParsingErrors(TextDocumentState file, CompletableFuture<Versioned<ITree>> futureTree) {
        var version = file.getCurrentContent().version();
        futureTree.handle((tree, excp) -> {
            Diagnostic newParseError = null;
            if (excp instanceof CompletionException) {
                excp = excp.getCause();
            }

            if (excp instanceof Throw) {
                Throw thrown = (Throw) excp;
                newParseError = Diagnostics.translateRascalParseError(thrown.getException(), columns);
            }
            else if (excp instanceof ParseError) {
                newParseError = Diagnostics.translateDiagnostic((ParseError)excp, columns);
            }
            else if (excp != null) {
                logger.error("Parsing crashed", excp);
                newParseError = new Diagnostic(
                    new Range(new Position(0,0), new Position(0,1)),
                    "Parsing failed: " + excp.getMessage(),
                    DiagnosticSeverity.Error,
                    "Rascal Parser");
            }
            logger.trace("Finished parsing tree, reporting new parse error: {} for: {}", newParseError, file.getLocation());
            facts(file.getLocation()).reportParseErrors(file.getLocation(), version,
                newParseError == null ? Collections.emptyList() : Collections.singletonList(newParseError));
            return null;
        });
    }

    @Override
    public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params) {
        logger.trace("codeLens for: {}", params.getTextDocument().getUri());
        final TextDocumentState file = getFile(params.getTextDocument());
        final ILanguageContributions contrib = contributions(params.getTextDocument());

        return recoverExceptions(file.getCurrentTreeAsync()
            .thenApply(Versioned::get)
            .thenApply(contrib::lenses)
            .thenCompose(InterruptibleFuture::get)
            .thenApply(s -> s.stream()
                .map(e -> locCommandTupleToCodeLense(contrib.getName(), e))
                .collect(Collectors.toList())
            ), () -> null);
    }

    @Override
    public CompletableFuture<List<InlayHint>> inlayHint(InlayHintParams params) {
        logger.trace("inlayHint for: {}", params.getTextDocument().getUri());
        final TextDocumentState file = getFile(params.getTextDocument());
        final ILanguageContributions contrib = contributions(params.getTextDocument());
        return recoverExceptions(
                recoverExceptions(file.getCurrentTreeAsync(), file::getMostRecentTree)
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
        var toolTip = (IString)t.asWithKeywordParameters().getParameter("toolTip");
        var atEnd = (IBool)t.asWithKeywordParameters().getParameter("atEnd");


        // translate to lsp
        var result = new InlayHint(Locations.toPosition(loc, columns, atEnd.getValue()), Either.forLeft(label.trim()));
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

        return new CodeLens(Locations.toRange(loc, columns), constructorToCommand(languageName, command), null);
    }
    
    private Command constructorToCommand(String languageName, IConstructor command) {
        IWithKeywordParameters<?> kw = command.asWithKeywordParameters();

        return new Command(kw.hasParameter("title") ? ((IString) kw.getParameter("title")).getValue() : command.toString(), getRascalMetaCommandName(), Arrays.asList(languageName, command.toString()));
    }

    private void handleParsingErrors(TextDocumentState file) {
        handleParsingErrors(file, file.getCurrentTreeAsync());
    }

    private static <T> T last(List<T> l) {
        return l.get(l.size() - 1);
    }

    private ILanguageContributions contributions(TextDocumentIdentifier doc) {
        return contributions(doc.getUri());
    }

    private ILanguageContributions contributions(TextDocumentItem doc) {
        return contributions(doc.getUri());
    }

    private ILanguageContributions contributions(String doc) {
        String language = registeredExtensions.get(extension(doc));
        if (language != null) {
            ILanguageContributions contrib = contributions.get(language);

            if (contrib != null) {
                return contrib;
            }
        }

        throw new UnsupportedOperationException("Rascal Parametric LSP has no support for this file: " + doc);
    }

    private static String extension(String file) {
        int index = file.lastIndexOf(".");
        if (index != -1) {
            return file.substring(index + 1);
        }
        return "";
    }

    private ParametricFileFacts facts(TextDocumentIdentifier doc) {
        return facts(doc.getUri());
    }

    private ParametricFileFacts facts(ISourceLocation doc) {
        return facts(doc.getPath());
    }

    private ParametricFileFacts facts(String doc) {
        String language = registeredExtensions.get(extension(doc));
        if (language != null) {
            ParametricFileFacts fact = facts.get(language);
            if (fact != null) {
                return fact;
            }
        }
        throw new UnsupportedOperationException("Rascal Parametric LSP has no support for this file: " + doc);
    }

    private TextDocumentState open(TextDocumentItem doc) {
        return files.computeIfAbsent(Locations.toLoc(doc),
            l -> new TextDocumentState(contributions(doc)::parseSourceFile, l, doc.getVersion(), doc.getText())
        );
    }

    private TextDocumentState getFile(TextDocumentIdentifier doc) {
        return getFile(Locations.toLoc(doc));
    }

    private TextDocumentState getFile(ISourceLocation loc) {
        TextDocumentState file = files.get(loc);
        if (file == null) {
            throw new ResponseErrorException(new ResponseError(-1, "Unknown file: " + loc, loc));
        }
        return file;
    }

    public void shutdown() {
        ownExecuter.shutdown();
    }

    private CompletableFuture<SemanticTokens> getSemanticTokens(TextDocumentIdentifier doc) {
        return recoverExceptions(getFile(doc).getCurrentTreeAsync()
                .thenApply(Versioned::get)
                .thenApplyAsync(tokenizer::semanticTokensFull, ownExecuter)
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
    public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>>documentSymbol(DocumentSymbolParams params) {
        logger.debug("Outline/documentSymbols: {}", params.getTextDocument());

        final TextDocumentState file = getFile(params.getTextDocument());
        ILanguageContributions contrib = contributions(params.getTextDocument());
        return recoverExceptions(file.getCurrentTreeAsync()
            .thenApply(Versioned::get)
            .thenApply(contrib::outline)
            .thenCompose(InterruptibleFuture::get)
            .thenApply(c -> Outline.buildOutline(c, columns.get(file.getLocation())))
            , Collections::emptyList);
    }

    @Override
    public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params) {
         logger.error("codeActions: {}", params);

        // TODO: clean this up a bit; (this is an experiment to see how to wire it all up_.
        final ILanguageContributions contribs = contributions(params.getTextDocument());
        final var loc = Locations.toLoc(params.getTextDocument());
        final var start = params.getRange().getStart();
        final var startLine = params.getRange().getStart().getLine();
        final var startColumn = columns.get(loc).translateInverseColumn(startLine, start.getCharacter(), false);

        if (contribs != null) {
            // first we filter out the quickfixes that were sent along with diagnostics
            // and which came back with the codeAction's list of relevant diagnostics:
            Stream<Either<Command,CodeAction>> quickfixes = params.getContext().getDiagnostics()
                .stream()
                .map(Diagnostic::getData)
                .filter(Objects::nonNull)
                .filter(JsonPrimitive.class::isInstance)
                .map(JsonPrimitive.class::cast)
                .map(JsonPrimitive::getAsString)
                .map(contribs::parseCommands)
                .map(handler(CompletableFuture::get))
                .flatMap(IList::stream)
                .map(cons -> constructorToCommand(contribs.getName(), (IConstructor) cons))
                .map(cmd -> Either.<Command,CodeAction>forLeft(cmd))
                ;
                

            // here we dynamically ask the contributions for more actions:
            return getFile(params.getTextDocument())
                .getCurrentTreeAsync()
                .thenApply(Versioned::get) // latest version
                .thenApply(tree -> {
                    ITree focus = (ITree) TreeAdapter.locateDeepestContextFreeNode(tree, offset);
                    logger.info(focus);
                    return contribs.codeActions(IRascalValueFactory.getInstance().sourceLocation(loc, offset, 1), tree, focus).get();
                })
                .thenCompose(Function.identity()) // flatten TODO: @davylandman do you have tips?
                .thenApply(actions -> Stream.concat(quickfixes, actions.stream() // TODO: quickfixes are merged in front, should that be?
                    .map(IConstructor.class::cast)
                    .map(cons -> constructorToCommand(contribs.getName(), cons))
                    .map(cmd  -> Either.<Command,CodeAction>forLeft(cmd))
                    ).collect(Collectors.toList())
                );
        }
        else {
            logger.warn("ignoring command execution (no code actions configured for this language): {}", language);
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * A type of higher-order function which throws exceptions, for wrapping by the `handler` method
     * below.
     * 
     * TODO: find a handy place for this
     */
    @FunctionalInterface
    private interface CheckedExceptionFunction<T, R, E extends Exception> {
        R apply(T t) throws E;
    }

    /**
     * Exception handler function for use in streams.
     * 
     * TODO: find a handy place for this
     */
    private <T, R, E extends Exception> Function<T, R> handler(CheckedExceptionFunction<T, R, E> fe) {
        return arg -> {
            try {
                return fe.apply(arg);
            } catch (Exception e) {
                logger.debug(e.getMessage(), e);
                throw new RuntimeException(e);
            }
        };
    }


    private <T> CompletableFuture<List<T>> lookup(SummaryLookup<T> lookup, TextDocumentIdentifier doc, Position cursor) {
        return getFile(doc)
            .getCurrentTreeAsync()
            .thenApply(tree -> facts(doc).lookupInSummaries(lookup, Locations.toLoc(doc), tree, cursor))
            .thenCompose(Function.identity());
    }

    @Override
    public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(DefinitionParams params) {
        logger.debug("Definition: {} at {}", params.getTextDocument(), params.getPosition());
        return recoverExceptions(
            lookup(ParametricSummary::definitions, params.getTextDocument(), params.getPosition())
            .thenApply(d -> { logger.debug("Definitions: {}", d); return d;})
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
        logger.debug("Implementation: {} at {}", params.getTextDocument(), params.getPosition());
        return recoverExceptions(
            lookup(ParametricSummary::references, params.getTextDocument(), params.getPosition())
            .thenApply(l -> l) // hack to help compiler see type
            , Collections::emptyList);
    }

    @Override
    public CompletableFuture<Hover> hover(HoverParams params) {
        logger.debug("Hover: {} at {}", params.getTextDocument(), params.getPosition());
        return recoverExceptions(
            lookup(ParametricSummary::documentation, params.getTextDocument(), params.getPosition())
            .thenApply(Hover::new)
            , () -> null);
    }

    @Override
    public CompletableFuture<List<FoldingRange>> foldingRange(FoldingRangeRequestParams params) {
        logger.debug("textDocument/foldingRange: {}", params.getTextDocument());
        TextDocumentState file = getFile(params.getTextDocument());
        return recoverExceptions(file.getCurrentTreeAsync().thenApply(Versioned::get).thenApplyAsync(FoldingRanges::getFoldingRanges)
            .whenComplete((r, e) ->
                logger.trace("Folding regions success, reporting {} regions back", r == null ? 0 : r.size())
            ), Collections::emptyList);
    }

    @Override
    public void registerLanguage(LanguageParameter lang) {
        logger.info("registerLanguage({})", lang.getName());

        for (var extension: lang.getExtensions()) {
            this.registeredExtensions.put(extension, lang.getName());
        }

        var multiplexer = contributions.computeIfAbsent(lang.getName(),
            t -> new LanguageContributionsMultiplexer(lang.getName(), ownExecuter)
        );
        var fact = facts.computeIfAbsent(lang.getName(), t ->
            new ParametricFileFacts(ownExecuter, columns, multiplexer)
        );

        if (lang.getPrecompiledParser() != null) {
            try {
                var location = lang.getPrecompiledParser().getParserLocation();
                if (URIResolverRegistry.getInstance().exists(location)) {
                    logger.debug("Got precompiled definition: {}", lang.getPrecompiledParser());
                    multiplexer.addContributor(buildContributionKey(lang) + "$parser", new ParserOnlyContribution(lang.getName(), lang.getPrecompiledParser()));
                }
                else {
                    logger.error("Defined precompiled parser ({}) does not exist", lang.getPrecompiledParser());
                }
            }
            catch (FactParseError e) {
                logger.error("Error parsing location in precompiled parser specification (we expect a rascal loc)", e);
            }
        }

        multiplexer.addContributor(buildContributionKey(lang),
            new InterpretedLanguageContributions(lang, this, workspaceService, (IBaseLanguageClient) client, ownExecuter));

        fact.reloadContributions();
        if (client != null) {
            fact.setClient(client);
        }
    }

    private static String buildContributionKey(LanguageParameter lang) {
        return lang.getMainFunction() + "::" + lang.getMainFunction();
    }

    @Override
    public void unregisterLanguage(LanguageParameter lang) {
        boolean removeAll = lang.getMainModule() == null || lang.getMainModule().isEmpty();
        if (!removeAll) {
            if (!contributions.get(lang.getName()).removeContributor(buildContributionKey(lang))) {
                logger.error("unregisterLanguage cleared everything, so removing all");
                // ok, so it was a clear after all
                removeAll = true;
            }
            else {
                facts.get(lang.getName()).reloadContributions();
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
    }

    @Override
    public CompletableFuture<IValue> executeCommand(String languageName, String command) {
        ILanguageContributions contribs = contributions.get(languageName);

        if (contribs != null) {
            return contribs.executeCommand(command).get();
        }
        else {
            logger.warn("ignoring command execution (no contributor configured for this language): {}, {} ", languageName, command);
            return CompletableFuture.completedFuture(null);
        }
    }

}
