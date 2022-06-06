/*
 * Copyright (c) 2018-2021, NWO-I CWI and Swat.engineering
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
package org.rascalmpl.vscode.lsp.parametric.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.services.LanguageClient;
import org.rascalmpl.vscode.lsp.TextDocumentState;
import org.rascalmpl.vscode.lsp.parametric.ILanguageContributions;
import org.rascalmpl.vscode.lsp.util.concurrent.ReplaceableFuture;
import org.rascalmpl.vscode.lsp.util.locations.ColumnMaps;

import io.usethesource.vallang.ISourceLocation;

public class ParametricFileFacts {
    private static final Logger logger = LogManager.getLogger(ParametricFileFacts.class);
    private final Executor exec;
    private volatile @MonotonicNonNull LanguageClient client;
    private final Map<ISourceLocation, FileFact> files = new ConcurrentHashMap<>();
    private final ILanguageContributions contrib;
    private final Function<ISourceLocation, TextDocumentState> lookupState;
    private final ColumnMaps columns;

    public ParametricFileFacts(ILanguageContributions contrib, Function<ISourceLocation, TextDocumentState> lookupState,
        ColumnMaps columns, Executor exec) {
        this.contrib = contrib;
        this.lookupState = lookupState;
        this.columns = columns;
        this.exec = exec;
    }

    public void setClient(LanguageClient client) {
        this.client = client;
    }

    public void reportParseErrors(ISourceLocation file, List<Diagnostic> msgs) {
        getFile(file).reportParseErrors(msgs);
    }

    private FileFact getFile(ISourceLocation l) {
        return files.computeIfAbsent(l, FileFact::new);
    }

    public CompletableFuture<ParametricSummaryBridge> getSummary(ISourceLocation file) {
        return getFile(file).getSummary();
    }

    public CompletableFuture<ParametricSummaryBridge> calcSummaryIfNeeded(ISourceLocation file) {
        return getFile(file).calcSummaryIfNeedd();
    }

    public void invalidate(ISourceLocation file) {
        var current = files.get(file);
        if (current != null) {
            current.invalidate();
        }
    }

    public void calculate(ISourceLocation file) {
        getFile(file).calculate();
    }

    public void close(ISourceLocation loc) {
        var present = files.remove(loc);
        if (present != null) {
            present.invalidate();
        }
    }

    private class FileFact {
        private final ISourceLocation file;
        private volatile List<Diagnostic> parseMessages = Collections.emptyList();
        private volatile List<Diagnostic> typeCheckerMessages = Collections.emptyList();
        private final ReplaceableFuture<ParametricSummaryBridge> summary;
        private final CompletableFuture<ParametricSummaryBridge> INVALID_SUMMARY_MARKER;
        private volatile boolean validSummary = false;

        public FileFact(ISourceLocation file) {
            this.file = file;
            INVALID_SUMMARY_MARKER = CompletableFuture.completedFuture(new ParametricSummaryBridge(file));
            summary = new ReplaceableFuture<>(INVALID_SUMMARY_MARKER);
        }

        private CompletableFuture<ParametricSummaryBridge> updateSummary(ISourceLocation file) {
            CompletableFuture<ParametricSummaryBridge> result = lookupState.apply(file)
                    .getCurrentTreeAsync()
                    .thenComposeAsync(t -> contrib.summarize(file, t), exec)
                    .thenApply(cons -> new ParametricSummaryBridge(cons, columns));
            // also schedule update of error messages
            result.thenAccept(p -> reportTypeCheckerMessages(p.getMessages()));
            return result;
        }

        private void reportTypeCheckerMessages(List<Diagnostic> messages) {
            typeCheckerMessages = messages;
            sendDiagnostics();
        }

        public void invalidate() {
            validSummary = false;
            summary.replace(INVALID_SUMMARY_MARKER);
        }

        public CompletableFuture<ParametricSummaryBridge> calculate() {
            var result = summary.replace(updateSummary(file));
            validSummary = true;
            return result;
        }

        public CompletableFuture<ParametricSummaryBridge> getSummary() {
            return summary.get();
        }

        public CompletableFuture<ParametricSummaryBridge> calcSummaryIfNeedd() {
            var result = summary.get();
            if (!validSummary) {
                return calculate();
            }
            return result;
        }

        public void reportParseErrors(List<Diagnostic> msgs) {
            parseMessages = msgs;
            sendDiagnostics();
        }

        private void sendDiagnostics() {
            if (client == null) {
                logger.debug("Cannot send diagnostics since the client hasn't been registered yet");
                return;
            }
            logger.trace("Sending diagnostics for {}. {} messages", file, typeCheckerMessages.size());
            client.publishDiagnostics(new PublishDiagnosticsParams(
                file.getURI().toString(),
                union(parseMessages, typeCheckerMessages)));
        }
    }

    private static <T> List<T> union(List<T> a, List<T> b) {
        if (a.isEmpty()) {
            return b;
        }
        if (b.isEmpty()) {
            return a;
        }
        List<T> result = new ArrayList<>(a);
        result.addAll(b);
        return result;
    }

}
