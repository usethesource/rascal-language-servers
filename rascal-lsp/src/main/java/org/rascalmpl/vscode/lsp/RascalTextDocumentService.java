/**
 * Copyright (c) 2018, Davy Landman, SWAT.engineering BV, 2020 Jurgen J. Vinju, NWO-I CWI All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 * following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.rascalmpl.vscode.lsp;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.rascalmpl.parser.gtd.exception.ParseError;
import org.rascalmpl.values.ValueFactoryFactory;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.values.parsetrees.TreeAdapter;
import org.rascalmpl.vscode.lsp.model.Summary;
import org.rascalmpl.vscode.lsp.util.Diagnostics;
import org.rascalmpl.vscode.lsp.util.ErrorReporter;
import org.rascalmpl.vscode.lsp.util.FileState;
import org.rascalmpl.vscode.lsp.util.Locations;
import org.rascalmpl.vscode.lsp.util.Outline;

import io.usethesource.vallang.ICollection;
import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValueFactory;

public class RascalTextDocumentService implements TextDocumentService, LanguageClientAware, ErrorReporter {
    private static final Logger logger = LogManager.getLogger(RascalTextDocumentService.class);
    private final ExecutorService ownExecuter = Executors.newCachedThreadPool();
    private final RascalLanguageServices rascalServices = RascalLanguageServices.getInstance();
    private final IValueFactory VF = ValueFactoryFactory.getValueFactory();

    private LanguageClient client;
    private final Map<ISourceLocation, FileState> files;

    private ConcurrentMap<ISourceLocation, List<Diagnostic>> currentDiagnostics = new ConcurrentHashMap<>();

    public RascalTextDocumentService() {
        this.files = new ConcurrentHashMap<>();
    }

    public void initializeServerCapabilities(ServerCapabilities result) {
        result.setDefinitionProvider(true);
        result.setTextDocumentSync(TextDocumentSyncKind.Full);
        result.setDocumentSymbolProvider(true);
    }

    @Override
    public void connect(LanguageClient client) {
        this.client = client;
    }

    // Error reporting API

    @Override
    public void clearReports(ISourceLocation file) {
        logger.trace("Clear reports: {}", file);
        client.publishDiagnostics(new PublishDiagnosticsParams(file.getURI().toString(), Collections.emptyList()));
        currentDiagnostics.remove(file);
    }

    @Override
    public void report(ICollection<?> msgs) {
        logger.trace("Reports: {}", msgs);
        appendDiagnostics(msgs.stream()
            .map(d -> (IConstructor) d)
            .map(d -> new SimpleEntry<>(((ISourceLocation) d.get("at")).top(), Diagnostics.translateDiagnostic(d))));
    }

    @Override
    public void report(ISourceLocation file, ISet messages) {
        logger.trace("Report: {} : {}", file, messages);
        replaceDiagnostics(file,
            messages.stream().map(d -> (IConstructor) d)
                .map(d -> new AbstractMap.SimpleEntry<>(((ISourceLocation) d.get("at")).top(),
                    Diagnostics.translateDiagnostic(d))));
    }

    @Override
    public void report(ParseError e) {
        ISourceLocation loc = e.getLocation();
        logger.trace("Report parse error: {}", loc);
        replaceDiagnostics(e.getLocation(), Stream.of(e)
            .map(e1 -> new AbstractMap.SimpleEntry<>(loc, Diagnostics.translateDiagnostic(e1))));
    }

    // LSP interface methods

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        logger.debug("Open file: {}", params.getTextDocument());
        open(params.getTextDocument());
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        logger.trace("Change contents: {}", params.getTextDocument());
        getFile(params.getTextDocument()).update(last(params.getContentChanges()).getText());
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        logger.debug("Close: {}", params.getTextDocument());
        if (files.remove(Locations.toLoc(params.getTextDocument())) == null) {
            throw new ResponseErrorException(new ResponseError(ResponseErrorCode.InternalError,
                "Unknown file: " + Locations.toLoc(params.getTextDocument()), params));
        }
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        logger.debug("Save: {}", params.getTextDocument());
        FileState file = getFile(params.getTextDocument());
        file.update(params.getText());

        CompletableFuture
            .supplyAsync(() -> rascalServices.compileFile(file.getLocation(), file.getPathConfig()), ownExecuter)
            .thenApply(l -> l.stream()
                .map(p -> {
                    IConstructor program = (IConstructor) p;
                    if (program.has("main_module")) {
                        program = (IConstructor) program.get("main_module");
                    }

                    if (!program.has("src")) {
                        logger.debug("Could not get src for errors: {}", program);
                        return VF.set();
                    }
                    if (!program.has("messages")) {
                        logger.debug("Could not get messages for errors: {}", program);
                        return VF.set();
                    }
                    return (ISet) program.get("messages");
                })
                .reduce(ISet::union)
                .orElse(VF.set()))
            .thenAccept(this::report);
    }

    private CompletableFuture<Summary> getSummary(FileState file) {
        return CompletableFuture.supplyAsync(
            () -> new Summary(rascalServices.getSummary(file.getLocation(), file.getPathConfig())), ownExecuter);
    }

    @Override
    public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(
        DefinitionParams params) {
        logger.debug("Definition: {} at {}", params.getTextDocument(), params.getPosition());
        FileState file = getFile(Locations.toLoc(params.getTextDocument()));

        final int column = params.getPosition().getCharacter();
        final int line = params.getPosition().getLine();

        return getSummary(file).thenApply(s -> {
            final ITree tree = file.getMostRecentTree();
            ITree lexical = TreeAdapter.locateLexical(tree, line, column);

            if (lexical == null) {
                throw new RuntimeException("no lexical found");
            }

            return Locations.toLSPLocation(s.definition(TreeAdapter.getLocation(lexical)));
        }).thenApply(l -> locList(l)).exceptionally(e -> locList());
    }

    @Override
    public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(
        DocumentSymbolParams params) {
        logger.debug("Outline/documentSymbols: {}", params.getTextDocument());
        FileState file = getFile(params.getTextDocument());
        return file.getCurrentTreeAsync()
            .handle((t, r) -> (t == null ? (file.getMostRecentTree()) : t))
            .thenApplyAsync(rascalServices::getOutline, ownExecuter)
            .thenApply(Outline::buildOutlineTree);
    }

    // Private utility methods

    private static Either<List<? extends Location>, List<? extends LocationLink>> locList(Location... l) {
        return Either.<List<? extends Location>, List<? extends LocationLink>>forLeft(Arrays.asList(l));
    }

    private void replaceDiagnostics(ISourceLocation clearFor, Stream<Entry<ISourceLocation, Diagnostic>> diagnostics) {
        Map<ISourceLocation, List<Diagnostic>> grouped = Diagnostics.groupByKey(diagnostics);
        grouped.putIfAbsent(clearFor, Collections.emptyList());

        grouped.forEach((file, msgs) -> {
            client.publishDiagnostics(new PublishDiagnosticsParams(file.getURI().toString(), msgs));
            currentDiagnostics.replace(file, msgs);
        });
    }

    private void appendDiagnostics(Stream<Entry<ISourceLocation, Diagnostic>> diagnostics) {
        Map<ISourceLocation, List<Diagnostic>> perFile = Diagnostics.groupByKey(diagnostics);
        perFile.forEach((file, msgs) -> {
            List<Diagnostic> currentMessages = currentDiagnostics.computeIfAbsent(file, f -> new ArrayList<>());
            logger.trace("Adding new diagnostic messages {}", file);
            currentMessages.addAll(msgs);
            logger.trace("Current messages {}", currentMessages.size());
            client.publishDiagnostics(new PublishDiagnosticsParams(file.getURI().toString(), currentMessages));
        });
    }

    private static <T> T last(List<T> l) {
        return l.get(l.size() - 1);
    }

    private FileState open(TextDocumentItem doc) {
        return files.computeIfAbsent(Locations.toLoc(doc),
            l -> new FileState(rascalServices, this, ownExecuter, l, doc.getText()));
    }

    private FileState getFile(TextDocumentIdentifier doc) {
        return getFile(Locations.toLoc(doc));
    }

    private FileState getFile(ISourceLocation loc) {
        FileState file = files.get(loc);
        if (file == null) {
            throw new ResponseErrorException(new ResponseError(-1, "Unknown file: " + loc, loc));
        }
        return file;
    }

    public void shutdown() {
        ownExecuter.shutdown();
    }
}
