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
package org.rascalmpl.vscode.lsp;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiFunction;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.rascalmpl.library.util.ParseErrorRecovery;
import org.rascalmpl.parser.gtd.exception.ParseError;
import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.vscode.lsp.util.Diagnostics;
import org.rascalmpl.vscode.lsp.util.Versioned;

import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValue;

/**
 * TextDocumentState encapsulates the current contents of every open file editor,
 * and the corresponding latest parse tree that belongs to it.
 * It is parametrized by the parser that must be used to map the string
 * contents to a tree. All other TextDocumentServices depend on this information.
 *
 * Objects of this class are used by the implementations of RascalTextDocumentService
 * and ParametricTextDocumentService.
 */
public class TextDocumentState {
    private static final Logger logger = LogManager.getLogger(TextDocumentState.class);
    private static final ParseErrorRecovery RECOVERY = new ParseErrorRecovery(IRascalValueFactory.getInstance());

    private final BiFunction<ISourceLocation, String, CompletableFuture<ITree>> parser;
    private final ISourceLocation location;

    private final AtomicReference<@MonotonicNonNull Versioned<Update>> current;
    private final AtomicReference<@MonotonicNonNull Versioned<ITree>> lastWithoutErrors;
    private final AtomicReference<@MonotonicNonNull Versioned<ITree>> last;

    public TextDocumentState(
            BiFunction<ISourceLocation, String, CompletableFuture<ITree>> parser,
            ISourceLocation location,
            int initialVersion, String initialContent, long initialTimestamp) {

        this.parser = parser;
        this.location = location;

        var u = new Update(initialVersion, initialContent, initialTimestamp);
        this.current = new AtomicReference<>(new Versioned<>(initialVersion, u));
        this.lastWithoutErrors = new AtomicReference<>();
        this.last = new AtomicReference<>();
    }

    public ISourceLocation getLocation() {
        return location;
    }

    public CompletableFuture<Versioned<List<Diagnostics.Template>>> update(int version, String content, long timestamp) {
        var u = new Update(version, content, timestamp);
        Versioned.replaceIfNewer(current, new Versioned<>(version, u));
        return u.getDiagnosticsAsync();
    }

    public Versioned<String> getCurrentContent() {
        return unpackCurrent().getContent();
    }

    public CompletableFuture<Versioned<ITree>> getCurrentTreeAsync() {
        return unpackCurrent().getTreeAsync();
    }

    public CompletableFuture<Versioned<List<Diagnostics.Template>>> getCurrentDiagnosticsAsync() {
        return unpackCurrent().getDiagnosticsAsync();
    }

    private Update unpackCurrent() {
        return current.get().get();
    }

    public @MonotonicNonNull Versioned<ITree> getLastTree() {
        return last.get();
    }

    public @MonotonicNonNull Versioned<ITree> getLastTreeWithoutErrors() {
        return lastWithoutErrors.get();
    }

    /**
     * An update of a text document, characterized in terms of its
     * {@link #version} (typically provied by the client), its {@link #content}
     * (typically provided by the client), and a {@link #timestamp} (typically
     * provided by the server).
     */
    private class Update {
        private final int version;
        private final String content;
        private final long timestamp;
        private final CompletableFuture<Versioned<ITree>> treeAsync;
        private final CompletableFuture<Versioned<List<Diagnostics.Template>>> diagnosticsAsync;

        public Update(int version, String content, long timestamp) {
            this.version = version;
            this.content = content;
            this.timestamp = timestamp;
            this.treeAsync = new CompletableFuture<>();
            this.diagnosticsAsync = new CompletableFuture<>();
            parse();
        }

        public Versioned<String> getContent() {
            return new Versioned<>(version, content, timestamp);
        }

        public long getTimestamp() {
            return timestamp;
        }

        public CompletableFuture<Versioned<ITree>> getTreeAsync() {
            return treeAsync;
        }

        public CompletableFuture<Versioned<List<Diagnostics.Template>>> getDiagnosticsAsync() {
            return diagnosticsAsync;
        }

        private void parse() {
            parser.apply(location, content)
                .whenComplete((t, e) -> {
                    var diagnosticsList = toDiagnosticsList(t, e); // `t` and `e` are nullable

                    // Complete future to get the tree
                    if (t == null) {
                        treeAsync.completeExceptionally(e);
                    } else {
                        var tree = new Versioned<>(version, t, timestamp);
                        Versioned.replaceIfNewer(last, tree);
                        if (diagnosticsList.isEmpty()) {
                            Versioned.replaceIfNewer(lastWithoutErrors, tree);
                        }
                        treeAsync.complete(tree);
                    }

                    // Complete future to get diagnostics
                    var diagnostics = new Versioned<>(version, diagnosticsList);
                    diagnosticsAsync.complete(diagnostics);
                });
        }

        private List<Diagnostics.Template> toDiagnosticsList(ITree tree, Throwable excp) {
            List<Diagnostics.Template> diagnostics = new ArrayList<>();

            if (excp instanceof CompletionException) {
                excp = excp.getCause();
            }

            if (excp instanceof ParseError) {
                var parseError = (ParseError) excp;
                diagnostics.add(Diagnostics.generateParseErrorDiagnostic(parseError));
            } else if (excp != null) {
                logger.error("Parsing crashed", excp);
                var diagnostic = new Diagnostic(
                    new Range(new Position(0,0), new Position(0,1)),
                    "Parsing failed: " + excp.getMessage(),
                    DiagnosticSeverity.Error,
                    "parser");
                diagnostics.add(columns -> diagnostic);
            }

            if (tree != null) {
                for (IValue error : RECOVERY.findAllParseErrors(tree)) {
                    diagnostics.addAll(Diagnostics.generateParseErrorDiagnostics((ITree) error));
                }
            }

            return diagnostics;
        }
    }

    public long getLastModified() {
        return unpackCurrent().getTimestamp();
    }
}
