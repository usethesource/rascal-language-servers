/**
 * Copyright (c) 2018, Davy Landman, SWAT.engineering BV, 2020 Jurgen J. Vinju, NWO-I CWI
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.rascalmpl.vscode.lsp;

import java.net.URISyntaxException;
import java.util.AbstractMap;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
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
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

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
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
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
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.values.parsetrees.TreeAdapter;
import org.rascalmpl.vscode.lsp.util.Diagnostics;
import org.rascalmpl.vscode.lsp.util.ErrorReporter;
import org.rascalmpl.vscode.lsp.util.FileState;

import io.usethesource.vallang.ICollection;
import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.IMap;
import io.usethesource.vallang.INode;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IWithKeywordParameters;

public class RascalTextDocumentService implements TextDocumentService, LanguageClientAware, ErrorReporter {
    private final ExecutorService ownExcecutor = Executors.newCachedThreadPool();
    private final RascalLanguageServices rascalServices = RascalLanguageServices.getInstance();
    private final IRascalValueFactory vf = IRascalValueFactory.getInstance();

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
        client.publishDiagnostics(new PublishDiagnosticsParams(file.getURI().toString(), Collections.emptyList()));
        currentDiagnostics.replace(file, Collections.emptyList());
    }

    @Override
    public void report(ICollection<?> msgs) {
        appendDiagnostics(msgs.stream().map(d -> (IConstructor) d).map(
                d -> new SimpleEntry<>(((ISourceLocation) d.get("at")).top(), Diagnostics.translateDiagnostic(d))));
    }

    @Override
    public void report(ISourceLocation file, ISet messages) {
        replaceDiagnostics(file,
                messages.stream().map(d -> (IConstructor) d)
                        .map(d -> new AbstractMap.SimpleEntry<>(((ISourceLocation) d.get("at")).top(),
                                Diagnostics.translateDiagnostic(d))));
    }

    @Override
    public void report(ParseError e) {
        replaceDiagnostics(e.getLocation(), Stream.of(e)
                .map(e1 -> new AbstractMap.SimpleEntry<>(e.getLocation(), Diagnostics.translateDiagnostic(e1))));
    }

    // LSP interface methods

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        open(params.getTextDocument());
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        String text = last(params.getContentChanges()).getText();
        ISourceLocation src = toLoc(params.getTextDocument());

        getFile(src).update(text);

        /*
        // CompletableFuture.runAsync(() -> {
        //     // TODO: temporarily here because didSave is not called
        //     try {
        //         IList files = vf.list(src);
        //         PathConfig pcfg = PathConfig.fromSourceProjectMemberRascalManifest(src);
        // IList messages = rascalServices.compileFileList(new NullRascalMonitor(),
        // files, pcfg);
        //         report(messages);
        //     } catch (IOException e) {
        //         Logger.getGlobal().log(Level.INFO,
        // "compilation/typechecking of " + params.getTextDocument().getUri() + "
        // failed", e);
        //     }
        // });
        */

    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        if (files.remove(toLoc(params.getTextDocument())) == null) {
            throw new ResponseErrorException(new ResponseError(ResponseErrorCode.InternalError,
                    "Unknown file: " + toLoc(params.getTextDocument()), params));
        }
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        // TODO didSave never happens. Perhaps because file sync is on FULL instead of
        // INCREMENTAL?
        // try {
        // Summary summary =
        // getFile(toLoc(params.getTextDocument())).getSummary().get();
        //     report(summary.getMessages());
        // } catch (InterruptedException | ExecutionException e) {
        // Logger.getGlobal().log(Level.INFO, "compilation/typechecking of " +
        // params.getTextDocument().getUri() + " failed", e);
        // }
    }

    @Override
    public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(
            DefinitionParams params) {
        FileState file = getFile(toLoc(params.getTextDocument()));

        final int column = params.getPosition().getCharacter();
        final int line = params.getPosition().getLine();

        return file.getSummary().thenApply(s -> {
                final ITree tree = file.getCurrentTree();
                ITree lexical = TreeAdapter.locateLexical(tree, line, column);

                if (lexical == null) {
                    throw new RuntimeException("no lexical found");
                }

                return toLSPLocation(s.definition(TreeAdapter.getLocation(lexical)));
        }).thenApply(l -> locList(l)).exceptionally(e -> locList());
    }

    @Override
    public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(
            DocumentSymbolParams params) {
        return getFile(toLoc(params.getTextDocument())).getCurrentTreeAsync().thenApply(rascalServices::getOutline)
                .thenApply(RascalTextDocumentService::buildOutlineTree);
    }

    // Private utility methods

    private static List<Either<SymbolInformation, DocumentSymbol>> buildOutlineTree(INode outline) {

        List<Either<SymbolInformation, DocumentSymbol>> result = new ArrayList<>();
        for (IValue g : outline) {
            INode group = (INode) g;
            SymbolKind kind = translateKind(group.getName());
            if (kind == null) {
                continue;
            }
            IValue arg = group.get(0);
            if (arg instanceof IList) {
                for (IValue e : (IList) arg) {
                    result.add(buildOutlineEntry(kind, (INode) e));
                }
            } else if (arg instanceof IMap) {
                ((IMap)arg).valueIterator().forEachRemaining(v -> {
                    for (IValue e: (IList)v) {
                        result.add(buildOutlineEntry(kind, (INode) e));
                    }
                });
            }
        }
        return result;
    }

    private static Either<SymbolInformation, DocumentSymbol> buildOutlineEntry( SymbolKind kind, INode element) {
        IWithKeywordParameters<? extends IValue> kwParams = element.asWithKeywordParameters();
        ISourceLocation loc = (ISourceLocation) kwParams.getParameter("loc");
        if (loc == null) {
            loc =  URIUtil.invalidLocation();
        }
        Range target = loc != null ? toRange(loc) : new Range(new Position(0,0), new Position(0,0));
        IString details = (IString) kwParams.getParameter("label");
        DocumentSymbol result;
        if (details == null) {
            result = new DocumentSymbol(element.getName(), kind, target, target);
        }
        else {
            result = new DocumentSymbol(element.getName(), kind, target, target, details.getValue());
        }
        return Either.forRight(result);
    }

    private static SymbolKind translateKind(String name) {
        switch (name) {
            case "Functions": return SymbolKind.Function;
            case "Tests": return SymbolKind.Method;
            case "Variables": return SymbolKind.Variable;
            case "Aliases": return SymbolKind.Class;
            case "Data": return SymbolKind.Struct;
            case "Tags": return SymbolKind.Property;
            case "Imports": return null;
            case "Syntax": return SymbolKind.Interface;
        }
        return null;
    }

    private static Either<List<? extends Location>, List<? extends LocationLink>> locList(Location... l) {
        return Either.<List<? extends Location>, List<? extends LocationLink>>forLeft(Arrays.asList(l));
    }

    private static Location toLSPLocation(ISourceLocation sloc) {
        return new Location(sloc.getURI().toString(), toRange(sloc));
    }

    private static Range toRange(ISourceLocation sloc) {
        return new Range(new Position(sloc.getBeginLine() - 1, sloc.getBeginColumn()), new Position(sloc.getEndLine() - 1, sloc.getEndColumn()));
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
        Diagnostics.groupByKey(diagnostics).forEach((file, msgs) -> {
            List<Diagnostic> currentMessages = currentDiagnostics.get(file);
            currentMessages.addAll(msgs);
            client.publishDiagnostics(new PublishDiagnosticsParams(file.getURI().toString(), currentMessages));
        });
    }

    private static ISourceLocation toLoc(TextDocumentItem doc) {
        return toLoc(doc.getUri());
    }

    private static ISourceLocation toLoc(TextDocumentIdentifier doc) {
        return toLoc(doc.getUri());
    }

    private static ISourceLocation toLoc(String uri) {
        try {
            return URIUtil.createFromURI(uri);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private static <T> T last(List<T> l) {
        return l.get(l.size() - 1);
    }

    private FileState open(TextDocumentItem doc) {
        return files.computeIfAbsent(toLoc(doc), l -> new FileState(rascalServices, this, ownExcecutor, l, doc.getText()));
    }

    private FileState getFile(ISourceLocation loc) {
        FileState file = files.get(loc);
        if (file == null) {
            throw new ResponseErrorException(new ResponseError(-1, "Unknown file: " + loc, loc));
        }
        return file;
    }
}
