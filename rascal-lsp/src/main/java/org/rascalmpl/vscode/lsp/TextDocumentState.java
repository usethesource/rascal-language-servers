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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.rascalmpl.library.util.ParseErrorRecovery;
import org.rascalmpl.parser.gtd.exception.ParseError;
import org.rascalmpl.values.RascalValueFactory;
import org.rascalmpl.values.ValueFactoryFactory;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.vscode.lsp.util.Diagnostics;
import org.rascalmpl.vscode.lsp.util.Versioned;
import org.rascalmpl.vscode.lsp.util.concurrent.DebouncedSupplier;
import org.rascalmpl.vscode.lsp.util.locations.ColumnMaps;

import io.usethesource.vallang.IList;
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

    private final BiFunction<ISourceLocation, String, CompletableFuture<ITree>> parser;
    private final ISourceLocation location;
    private final ColumnMaps columns;

    @SuppressWarnings("java:S3077") // Visibility of writes is enough
    private volatile Update current;
    private final DebouncedSupplier<Update> parseAndGetCurrentDebouncer;

    private final AtomicReference<@MonotonicNonNull Versioned<ITree>> lastWithoutErrors;
    private final AtomicReference<@MonotonicNonNull Versioned<ITree>> last;

    public TextDocumentState(
            BiFunction<ISourceLocation, String, CompletableFuture<ITree>> parser,
            ISourceLocation location, ColumnMaps columns,
            int initialVersion, String initialContent, long initialTimestamp) {

        this.parser = parser;
        this.location = location;
        this.columns = columns;

        this.current = new Update(initialVersion, initialContent, initialTimestamp);
        this.parseAndGetCurrentDebouncer = new DebouncedSupplier<>(this::parseAndGetCurrent);
        this.lastWithoutErrors = new AtomicReference<>();
        this.last = new AtomicReference<>();
    }

    public ISourceLocation getLocation() {
        return location;
    }

    public void update(int version, String content, long timestamp) {
        current = new Update(version, content, timestamp);
        // The creation of the `Update` object doesn't trigger the parser yet.
        // This happens only when the tree or diagnostics are requested.
    }

    public Versioned<String> getCurrentContent() {
        return current.getContent();
    }

    public CompletableFuture<Versioned<ITree>> getCurrentTreeAsync() {
        return getCurrentTreeAsync(Duration.ZERO);
    }

    public CompletableFuture<Versioned<ITree>> getCurrentTreeAsync(Duration delay) {
        return parseAndGetCurrent(delay)
            .thenApply(Update::getTreeAsync)
            .thenCompose(Function.identity());
    }

    public CompletableFuture<Versioned<List<Diagnostic>>> getCurrentDiagnosticsAsync() {
        return getCurrentDiagnosticsAsync(Duration.ZERO);
    }

    public CompletableFuture<Versioned<List<Diagnostic>>> getCurrentDiagnosticsAsync(Duration delay) {
        return parseAndGetCurrent(delay)
            .thenApply(Update::getDiagnosticsAsync)
            .thenCompose(Function.identity());
    }

    public @MonotonicNonNull Versioned<ITree> getLastTree() {
        return last.get();
    }

    public @MonotonicNonNull Versioned<ITree> getLastTreeWithoutErrors() {
        return lastWithoutErrors.get();
    }

    private CompletableFuture<Update> parseAndGetCurrent() {
        var update = current;
        update.parseIfNotParsing();
        return CompletableFuture.completedFuture(update);
    }

    private CompletableFuture<Update> parseAndGetCurrent(Duration delay) {
        var update = current;
        if (update.isParsing()) {
            return CompletableFuture.completedFuture(update);
        } else {
            return parseAndGetCurrentDebouncer.get(delay);
        }
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
        private final CompletableFuture<Versioned<List<Diagnostic>>> diagnosticsAsync;
        private final AtomicBoolean parsing;

        public Update(int version, String content, long timestamp) {
            this.version = version;
            this.content = content;
            this.timestamp = timestamp;
            this.treeAsync = new CompletableFuture<>();
            this.diagnosticsAsync = new CompletableFuture<>();
            this.parsing = new AtomicBoolean(false);
        }

        public Versioned<String> getContent() {
            return new Versioned<>(version, content, timestamp);
        }

        public long getTimestamp() {
            return timestamp;
        }

        public CompletableFuture<Versioned<ITree>> getTreeAsync() {
            parseIfNotParsing();
            return treeAsync;
        }

        public CompletableFuture<Versioned<List<Diagnostic>>> getDiagnosticsAsync() {
            parseIfNotParsing();
            return diagnosticsAsync;
        }

        public boolean isParsing() {
            return parsing.get();
        }

        private void parseIfNotParsing() {
            if (parsing.compareAndSet(false, true)) {
                parser
                    .apply(location, content)
                    .whenComplete((t, e) -> {
                        // Prepare result values for futures
                        var tree = new Versioned<>(version, t, timestamp);
                        var diagnostics = new Versioned<>(version, toDiagnostics(t, e));

                        // Complete future to get the tree
                        if (t == null) {
                            treeAsync.completeExceptionally(e);
                        } else {
                            treeAsync.complete(tree);
                            Versioned.replaceIfNewer(last, tree);
                            if (diagnostics.get().isEmpty()) {
                                Versioned.replaceIfNewer(lastWithoutErrors, tree);
                            }
                        }

                        // Complete future to get diagnostics
                        diagnosticsAsync.complete(diagnostics);
                    });
            }
        }

        private List<Diagnostic> toDiagnostics(ITree tree, Throwable excp) {
            List<Diagnostic> parseErrors = new ArrayList<>();

            if (excp instanceof CompletionException) {
                excp = excp.getCause();
            }

            if (excp instanceof ParseError) {
                parseErrors.add(Diagnostics.translateDiagnostic((ParseError)excp, columns));
            } else if (excp != null) {
                logger.error("Parsing crashed", excp);
                parseErrors.add(new Diagnostic(
                    new Range(new Position(0,0), new Position(0,1)),
                    "Parsing failed: " + excp.getMessage(),
                    DiagnosticSeverity.Error,
                    "Rascal Parser"));
            }

            if (tree != null) {
                RascalValueFactory valueFactory = (RascalValueFactory) ValueFactoryFactory.getValueFactory();
                IList errors = new ParseErrorRecovery(valueFactory).findAllParseErrors(tree);
                for (IValue error : errors) {
                    ITree errorTree = (ITree) error;
                    parseErrors.addAll(Diagnostics.generateParseErrorDiagnostics(errorTree, columns));
                }
            }

            return parseErrors;
        }
    }

    public long getLastModified() {
        return current.getTimestamp();
    }
}
