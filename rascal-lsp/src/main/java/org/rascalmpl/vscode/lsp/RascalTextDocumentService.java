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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextDocumentRegistrationOptions;
import org.eclipse.lsp4j.TextDocumentSaveRegistrationOptions;
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
import org.rascalmpl.vscode.lsp.model.FileFacts;
import org.rascalmpl.vscode.lsp.util.Diagnostics;
import org.rascalmpl.vscode.lsp.util.FileState;
import org.rascalmpl.vscode.lsp.util.Locations;
import org.rascalmpl.vscode.lsp.util.Outline;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValueFactory;

public class RascalTextDocumentService implements TextDocumentService, LanguageClientAware {
    private static final Logger logger = LogManager.getLogger(RascalTextDocumentService.class);
    private final ExecutorService ownExecuter = Executors.newCachedThreadPool();
    private final RascalLanguageServices rascalServices;
    private final IValueFactory VF = ValueFactoryFactory.getValueFactory();

    private @MonotonicNonNull LanguageClient client;

    private final Map<ISourceLocation, FileState> files;
    private final FileFacts facts;

    public RascalTextDocumentService(RascalLanguageServices rascal) {
        this.files = new ConcurrentHashMap<>();
        this.rascalServices = rascal;
        this.facts = new FileFacts(ownExecuter, rascal);
    }

    public void initializeServerCapabilities(ServerCapabilities result) {
        result.setDefinitionProvider(true);
        result.setTextDocumentSync(TextDocumentSyncKind.Full);
        result.setDocumentSymbolProvider(true);
    }

    @Override
    public void connect(LanguageClient client) {
        this.client = client;
        facts.setClient(client);
    }

    // LSP interface methods

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        logger.debug("Open file: {}", params.getTextDocument());
        FileState file = open(params.getTextDocument());
        handleParsingErrors(file);
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        logger.trace("Change contents: {}", params.getTextDocument());
        updateContents(params.getTextDocument(), last(params.getContentChanges()).getText());
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
        // on save we don't get new file contents, that comes in via change
        // but we do trigger the type checker on save
        facts.invalidate(Locations.toLoc(params.getTextDocument()));
    }

    private FileState updateContents(TextDocumentIdentifier doc, String newContents) {
        FileState file = getFile(doc);
        logger.trace("New contents: {} has: {}", doc, newContents);
        handleParsingErrors(file, file.update(newContents));
        return file;
    }

    private void handleParsingErrors(FileState file, CompletableFuture<ITree> futureTree) {
        futureTree.handle((tree, excp) -> {
            logger.trace("Finished parsing tree: {}", file.getLocation());
            Diagnostic newParseError = null;
            if (excp != null && excp instanceof CompletionException) {
                excp = excp.getCause();
            }
            if (excp instanceof ParseError) {
                newParseError = Diagnostics.translateDiagnostic((ParseError)excp);
            }
            else if (excp != null) {
                logger.error("Parsing crashed", excp);
                newParseError = new Diagnostic(
                    new Range(new Position(0,0), new Position(0,1)),
                    "Parsing failed: " + excp.getMessage(),
                    DiagnosticSeverity.Error,
                    "Rascal Parser");
            }
            logger.trace("Reporting new parse error: {} for: {}", newParseError, file.getLocation());
            facts.reportParseErrors(file.getLocation(),
                newParseError == null ? Collections.emptyList() : Collections.singletonList(newParseError));
            return null;
        });
    }

    private void handleParsingErrors(FileState file) {
        handleParsingErrors(file,file.getCurrentTreeAsync());
    }


    @Override
    public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(
        DefinitionParams params) {
        logger.debug("Definition: {} at {}", params.getTextDocument(), params.getPosition());

        return facts.getSummary(Locations.toLoc(params.getTextDocument()))
            .thenApply(s -> s.getDefinition(params.getPosition()))
            .thenApply(Either::forLeft)
            ;
    }

    @Override
    public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(
        DocumentSymbolParams params) {
        logger.debug("Outline/documentSymbols: {}", params.getTextDocument());
        FileState file = getFile(params.getTextDocument());
        return file.getCurrentTreeAsync()
            .handle((t, r) -> (t == null ? (file.getMostRecentTree()) : t))
            .thenCompose(tr -> rascalServices.getOutline(tr, ownExecuter).get())
            .thenApply(Outline::buildOutlineTree);
    }

    // Private utility methods

    private static <T> T last(List<T> l) {
        return l.get(l.size() - 1);
    }

    private FileState open(TextDocumentItem doc) {
        return files.computeIfAbsent(Locations.toLoc(doc),
            l -> new FileState(rascalServices, ownExecuter, l, doc.getText()));
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
