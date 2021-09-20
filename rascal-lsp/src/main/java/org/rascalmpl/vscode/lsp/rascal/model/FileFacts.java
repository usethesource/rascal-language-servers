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
package org.rascalmpl.vscode.lsp.rascal.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.services.LanguageClient;
import org.rascalmpl.library.util.PathConfig;
import org.rascalmpl.library.util.PathConfig.RascalConfigMode;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.vscode.lsp.rascal.RascalLanguageServices;
import org.rascalmpl.vscode.lsp.util.Diagnostics;
import org.rascalmpl.vscode.lsp.util.concurrent.InterruptibleFuture;
import org.rascalmpl.vscode.lsp.util.concurrent.LazyUpdateableReference;
import org.rascalmpl.vscode.lsp.util.concurrent.ReplaceableFuture;
import org.rascalmpl.vscode.lsp.util.locations.ColumnMaps;

import io.usethesource.vallang.ISourceLocation;

public class FileFacts {
    private static final Logger logger = LogManager.getLogger(FileFacts.class);
    private final Executor exec;
    private final RascalLanguageServices rascal;
    private volatile @MonotonicNonNull LanguageClient client;
    private final Map<ISourceLocation, FileFact> files = new ConcurrentHashMap<>();
    private final ColumnMaps cm;

    public FileFacts(Executor exec, RascalLanguageServices rascal, ColumnMaps cm) {
        this.exec = exec;
        this.rascal = rascal;
        this.cm = cm;
    }

    public void setClient(LanguageClient client) {
        this.client = client;
    }

    public void invalidate(ISourceLocation changedFile) {
        getFile(changedFile).invalidate();
    }

    public CompletableFuture<@Nullable SummaryBridge> getSummary(ISourceLocation file) {
        return getFile(file).getSummary();
    }

    public void reportParseErrors(ISourceLocation file, List<Diagnostic> msgs) {
        getFile(file).reportParseErrors(msgs);
    }

    private FileFact getFile(ISourceLocation l) {
        ISourceLocation resolved = null;
        try {
            resolved = URIResolverRegistry.getInstance().logicalToPhysical(l);
            if (resolved == null) {
                resolved = l;
            }
        } catch (IOException e) {
            resolved = l;
        }
        return files.computeIfAbsent(resolved, l1 -> new FileFact(l1, exec));
    }

    private class FileFact {
        private final ISourceLocation file;
        private final PathConfig pcfg;
        private final LazyUpdateableReference<InterruptibleFuture<@Nullable SummaryBridge>> summary;
        private volatile List<Diagnostic> parseMessages = Collections.emptyList();
        private volatile List<Diagnostic> typeCheckerMessages = Collections.emptyList();
        private final ReplaceableFuture<Map<ISourceLocation, List<Diagnostic>>> typeCheckResults;

        public FileFact(ISourceLocation file, Executor exec) {
            this.file = file;
            PathConfig pcfg;
            try {
                pcfg = PathConfig.fromSourceProjectMemberRascalManifest(file, RascalConfigMode.COMPILER);
            } catch (IOException e) {
                logger.error("Could not figure out path config for: {}, falling back to default", file, e);
                pcfg = new PathConfig();
            }
            this.pcfg = pcfg;
            this.typeCheckResults = new ReplaceableFuture<>(CompletableFuture.completedFuture(Collections.emptyMap()));
            this.summary = new LazyUpdateableReference<>(
                new InterruptibleFuture<>(CompletableFuture.completedFuture(new SummaryBridge()), () -> {
                }),
                r -> {
                    r.interrupt();
                    InterruptibleFuture<@Nullable SummaryBridge> summaryCalc = rascal.getSummary(file, this.pcfg)
                        .thenApply(s -> s == null ? null : new SummaryBridge(s, cm));
                    // only run get summary after the typechecker for this file is done running
                    // (we cannot now global running type checkers, that is a different subject)
                    CompletableFuture<@Nullable SummaryBridge> mergedCalc = typeCheckResults.get().thenCompose(o -> summaryCalc.get());
                    return new InterruptibleFuture<>(mergedCalc, summaryCalc::interrupt);
                });
        }

        public void reportParseErrors(List<Diagnostic> msgs) {
            parseMessages = msgs;
            sendDiagnostics();
        }
        private void reportTypeCheckerErrors(List<Diagnostic> msgs) {
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
                file.getURI().toString(),
                union(typeCheckerMessages, parseMessages)));
        }


        public CompletableFuture<@Nullable SummaryBridge> getSummary() {
            return summary.get().get();
        }

        public void invalidate() {
            summary.invalidate();
            typeCheckerMessages.clear();
            this.typeCheckResults.replace(
                rascal.compileFile(file, pcfg, exec)
                    .thenApply(m -> {
                        Map<ISourceLocation, List<Diagnostic>> result = new HashMap<>(m.size());
                        m.forEach((l, msgs) -> result.put(l, Diagnostics.translateDiagnostics(l, msgs, cm)));
                        return result;
                    })
            ).thenAccept(m -> m.forEach((f, msgs) -> getFile(f).reportTypeCheckerErrors(msgs)));
        }

    }

    private static <T> List<T> union(List<T> a, List<T> b) {
        if (a.isEmpty()) {
            return b;
        }
        if (b.isEmpty()) {
            return a;
        }
        ArrayList<T> result = new ArrayList<>(a);
        result.addAll(b);
        return result;
    }
}
