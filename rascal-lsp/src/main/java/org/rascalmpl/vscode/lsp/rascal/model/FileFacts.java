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
package org.rascalmpl.vscode.lsp.rascal.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.services.LanguageClient;
import org.rascalmpl.library.util.PathConfig;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.util.locations.ColumnMaps;
import org.rascalmpl.vscode.lsp.rascal.RascalLanguageServices;
import org.rascalmpl.vscode.lsp.util.Diagnostics;
import org.rascalmpl.vscode.lsp.util.Lists;
import org.rascalmpl.vscode.lsp.util.concurrent.InterruptibleFuture;
import org.rascalmpl.vscode.lsp.util.concurrent.LazyUpdateableReference;
import org.rascalmpl.vscode.lsp.util.concurrent.ReplaceableFuture;
import org.rascalmpl.vscode.lsp.util.locations.Locations;

import io.usethesource.vallang.ISourceLocation;

public class FileFacts {
    private static final Logger logger = LogManager.getLogger(FileFacts.class);
    private final Executor exec;
    private final RascalLanguageServices rascal;
    private final LanguageClient client;
    private final Map<ISourceLocation, FileFact> files = new ConcurrentHashMap<>();
    private final ColumnMaps cm;
    private final PathConfigs confs;

    public FileFacts(Executor exec, RascalLanguageServices rascal, LanguageClient client, ColumnMaps cm) {
        this.exec = exec;
        this.rascal = rascal;
        this.client = client;
        this.cm = cm;
        this.confs = new PathConfigs(exec, new PathConfigDiagnostics(client, cm));
    }

    public void projectRemoved(ISourceLocation projectLocation) {
        confs.expungePathConfig(projectLocation);
    }

    public void invalidate(ISourceLocation file) {
        getFile(file).invalidate();
    }

    public CompletableFuture<@Nullable SummaryBridge> getSummary(ISourceLocation file) {
        return getFile(file).getSummary();
    }

    public void reportParseErrors(ISourceLocation file, List<Diagnostic> msgs) {
        getFile(file).reportParseErrors(msgs);
    }

    private FileFact getFile(ISourceLocation l) {
        l = l.top();
        ISourceLocation resolved = Locations.toClientLocation(l);
        if (resolved == null) {
            resolved = l;
        }
        var fact = files.get(resolved);
        if (fact == null) {
            if (URIResolverRegistry.getInstance().exists(resolved)) {
                fact = new ActualFileFact(resolved, exec);
                var existing = files.putIfAbsent(resolved, fact);
                if (existing != null) {
                    fact = existing;
                }
            } else {
                fact = new NopFileFact();
            }
        }
        return fact;
    }

    public PathConfig getPathConfig(ISourceLocation file) {
        return confs.lookupConfig(file);
    }

    public void close(ISourceLocation file) {
        getFile(file).close();
    }

    private @Nullable FileFact remove(ISourceLocation file) {
        var removed = files.remove(file.top());
        if (removed != null) {
            removed.clearDiagnostics();
        }
        return removed;
    }

    private interface FileFact {
        void reportParseErrors(List<Diagnostic> msgs);
        void reportTypeCheckerErrors(List<Diagnostic> msgs);
        CompletableFuture<@Nullable SummaryBridge> getSummary();
        void invalidate();
        void close();
        void clearDiagnostics();
    }

    private class ActualFileFact implements FileFact {
        private final ISourceLocation file;
        private final LazyUpdateableReference<InterruptibleFuture<@Nullable SummaryBridge>> summary;
        private volatile List<Diagnostic> parseMessages = Collections.emptyList();
        private volatile List<Diagnostic> typeCheckerMessages = Collections.emptyList();
        private final ReplaceableFuture<Map<ISourceLocation, List<Diagnostic>>> typeCheckResults;

        public ActualFileFact(ISourceLocation file, Executor exec) {
            this.file = file;
            this.typeCheckResults = ReplaceableFuture.completedFuture(Collections.emptyMap(), exec);
            this.summary = new LazyUpdateableReference<>(
                InterruptibleFuture.completedFuture(new SummaryBridge(), exec),
                r -> {
                    r.interrupt();
                    var summaryCalc = rascal.getSummary(file, confs.lookupConfig(file))
                        .<@Nullable SummaryBridge>thenApply(s -> s == null ? null : new SummaryBridge(file, s, cm));
                    // only run get summary after the typechecker for this file is done running
                    // (we cannot now global running type checkers, that is a different subject)
                    var mergedCalc = typeCheckResults.get().<@Nullable SummaryBridge>thenCompose(o -> summaryCalc.get());
                    return new InterruptibleFuture<>(mergedCalc, summaryCalc::interrupt);
                });
        }

        @Override
        public void reportParseErrors(List<Diagnostic> msgs) {
            parseMessages = msgs;
            sendDiagnostics();
        }
        
        @Override
        public void reportTypeCheckerErrors(List<Diagnostic> msgs) {
            typeCheckerMessages = msgs;
            sendDiagnostics();
        }

        private void sendDiagnostics() {
            if (client == null) {
                logger.debug("Cannot send diagnostics since the client hasn't been registered yet");
                return;
            }
            logger.trace("Sending diagnostics for: {}", file);
            client.publishDiagnostics(new PublishDiagnosticsParams(
                Locations.toUri(file).toString(),
                Lists.union(typeCheckerMessages, parseMessages)));
        }

        @Override
        public CompletableFuture<@Nullable SummaryBridge> getSummary() {
            return summary.get().get();
        }

        @Override
        public void invalidate() {
            summary.invalidate();
            typeCheckerMessages.clear();
            this.typeCheckResults.replace(
                rascal.compileFile(file, confs.lookupConfig(file), exec)
                    .thenApply(m -> Diagnostics.translateMessages(m, cm))
            ).thenAccept(m -> m.forEach((f, msgs) -> getFile(f).reportTypeCheckerErrors(msgs)));
        }

        @Override
        public void close() {
            if ((parseMessages.isEmpty() && typeCheckerMessages.isEmpty()) || !URIResolverRegistry.getInstance().exists(file)) {
                // If there are no messages for this file or the file has been deleted, can we remove it
                // else VS Code comes back and we've dropped the messages in our internal data
                files.remove(file);
            }
        }

        @Override
        public void clearDiagnostics() {
            summary.invalidate();
            typeCheckerMessages.clear();
            typeCheckResults.replace(CompletableFuture.completedFuture(Map.of()));
            client.publishDiagnostics(new PublishDiagnosticsParams(Locations.toUri(file).toString(), List.of()));
        }
    }

    class NopFileFact implements FileFact {
        @Override
        public void reportParseErrors(List<Diagnostic> msgs) {
            // NOP
        }

        @Override
        public void reportTypeCheckerErrors(List<Diagnostic> msgs) {
            // NOP
        }

        @Override
        public CompletableFuture<@Nullable SummaryBridge> getSummary() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void invalidate() {
            // NOP
        }

        @Override
        public void close() {
            // NOP
        }

        @Override
        public void clearDiagnostics() {
            // NOP
        }
    }
}
