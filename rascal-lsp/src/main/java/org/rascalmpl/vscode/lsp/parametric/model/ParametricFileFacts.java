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
package org.rascalmpl.vscode.lsp.parametric.model;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.services.LanguageClient;
import org.rascalmpl.vscode.lsp.TextDocumentState;
import org.rascalmpl.vscode.lsp.parametric.ILanguageContributions;
import org.rascalmpl.vscode.lsp.util.locations.ColumnMaps;

import io.usethesource.vallang.ISourceLocation;

public class ParametricFileFacts {
    private static final Logger logger = LogManager.getLogger(ParametricFileFacts.class);
    private final ScheduledExecutorService exec;
    private volatile @MonotonicNonNull LanguageClient client;
    private final Map<ISourceLocation, FileFact> files = new ConcurrentHashMap<>();
    private final ILanguageContributions contrib;
    private final Function<ISourceLocation, TextDocumentState> lookupState;
    private final ColumnMaps columns;

    public ParametricFileFacts(ILanguageContributions contrib, Function<ISourceLocation, TextDocumentState> lookupState,
        ColumnMaps columns, ScheduledExecutorService exec) {
        this.contrib = contrib;
        this.lookupState = lookupState;
        this.columns = columns;
        this.exec = exec;
    }

    public void setClient(LanguageClient client) {
        this.client = client;
    }

    public void reportParseErrors(ISourceLocation file, int version, List<Diagnostic> msgs) {
        getFile(file).reportParseErrors(version, msgs);
    }

    private FileFact getFile(ISourceLocation l) {
        return files.computeIfAbsent(l, FileFact::new);
    }

    public void reloadContributions() {
        files.values().forEach(FileFact::reloadContributions);
    }

    public ParametricSummaryBridge getSummaryAnalyzer(ISourceLocation file) {
        return getFile(file).getSummaryAnalyzer();
    }

    public ParametricSummaryBridge getSummaryBuilder(ISourceLocation file) {
        return getFile(file).getSummaryBuilder();
    }

    public void invalidateAnalyzer(ISourceLocation file) {
        var current = files.get(file);
        if (current != null) {
            current.invalidateAnalyzer(false);
        }
    }

    public void invalidateBuilder(ISourceLocation file) {
        var current = files.get(file);
        if (current != null) {
            current.invalidateBuilder(false);
        }
    }

    public void calculateAnalyzer(ISourceLocation file, int version, Duration delay) {
        getFile(file).calculateAnalyzer(version, delay);
    }

    public void calculateBuilder(ISourceLocation file, int version, Duration delay) {
        getFile(file).calculateBuilder(version, delay);
    }

    public void close(ISourceLocation loc) {
        var present = files.get(loc);
        if (present != null) {
            present.invalidateAnalyzer(true);
            present.invalidateBuilder(true);

            var messagesAnalyzer = present.summaryAnalyzer.getMessages();
            var messagesBuilder = present.summaryBuilder.getMessages();
            messagesAnalyzer.thenAcceptBothAsync(messagesBuilder, (m1, m2) -> {
                if (m1.isEmpty() && m2.isEmpty()) {
                    // only if there are no messages for this class, can we remove it
                    // else vscode comes back and we've dropped the messages in our internal data
                    files.remove(loc);
                }
            });
        }
    }

    private static class VersionedDiagnostics {
        public final int version;
        public final List<Diagnostic> messages;

        public static final VersionedDiagnostics INITIAL = new VersionedDiagnostics(0, Collections.emptyList());

        public VersionedDiagnostics(int version, List<Diagnostic> messages) {
            this.version = version;
            this.messages = messages;
        }

        public static boolean setIfNewer(AtomicReference<VersionedDiagnostics> box, VersionedDiagnostics maybeNewer) {
            var isSet = false;
            var retry = true;
            while (retry) {
                var old = box.get();
                retry = old.version < maybeNewer.version && !(isSet = box.compareAndSet(old, maybeNewer));
            }
            return isSet;
        }

        public static VersionedDiagnostics union(VersionedDiagnostics... list) {
            var version = 0;
            var messages = new ArrayList<Diagnostic>();
            for (var versionedMessages : list) {
                version = Math.max(version, versionedMessages.version);
                messages.addAll(versionedMessages.messages);
            }
            return new VersionedDiagnostics(version, messages);
        }
    }

    private class FileFact {
        private final ISourceLocation file;

        // To replace (`version`, `messages`) pairs in a thread-safe way when
        // new diagnostics become available, we need to atomically: (1) check if
        // the new version is greater than the old version; (2) if so, replace
        // the old pair with the new pair. This is why `AtomicReference` and
        // `VersionedDiagnostics` are needed here.
        private final AtomicReference<VersionedDiagnostics> messagesParser = new AtomicReference<>(VersionedDiagnostics.INITIAL);
        private final AtomicReference<VersionedDiagnostics> messagesAnalyzer = new AtomicReference<>(VersionedDiagnostics.INITIAL);
        private final AtomicReference<VersionedDiagnostics> messagesBuilder = new AtomicReference<>(VersionedDiagnostics.INITIAL);

        private final ParametricSummaryBridge summaryAnalyzer;
        private final ParametricSummaryBridge summaryBuilder;

        private final AtomicInteger lastVersionCalculateAnalyzer = new AtomicInteger();
        private final AtomicInteger lastVersionCalculateBuilder = new AtomicInteger();

        public FileFact(ISourceLocation file) {
            this.file = file;
            this.summaryAnalyzer = new ParametricSummaryBridge(exec, file, columns, contrib, lookupState, contrib::analyze);
            this.summaryBuilder = new ParametricSummaryBridge(exec, file, columns, contrib, lookupState, contrib::build);
        }

        public void reloadContributions() {
            summaryAnalyzer.reloadContributions();
            summaryBuilder.reloadContributions();
        }

        private void reportMessages(AtomicReference<VersionedDiagnostics> box, int version, List<Diagnostic> messages) {
            if (VersionedDiagnostics.setIfNewer(box, new VersionedDiagnostics(version, messages))) {
                sendDiagnostics();
            }
        }

        private void invalidate(ParametricSummaryBridge summary, boolean isClosing) {
            summary.invalidate(isClosing);
        }

        public void invalidateAnalyzer(boolean isClosing) {
            invalidate(summaryAnalyzer, isClosing);
        }

        public void invalidateBuilder(boolean isClosing) {
            invalidate(summaryBuilder, isClosing);
        }

        /**
         * @param version the version of the file for which summary calculation
         * is currently requested
         * @param lastVersion the last version of the file for which summary
         * calculation was previously requested
         * @param delay the duration after which the current request for summary
         * calculation will be granted, unless another request is made in the
         * meantime (in which case the current request is abandoned)
         * @param summary the summary to calculate
         * @param box a reference to store diagnostics after summary calculation
         */
        private void calculate(int version, AtomicInteger lastVersion, Duration delay,
                ParametricSummaryBridge summary, AtomicReference<VersionedDiagnostics> box) {

            // If no new call to `calculate` has been made after `delay` has
            // passed (i.e., `lastVersion` hasn't changed in the meantime), then
            // run the calculation. Else, ignore and leave the calculation to
            // the new call. Assumption: `calculate` is called at most once for
            // each (`version`, `lastVersion`) pair.
            lastVersion.set(version);
            exec.schedule(() -> {
                if (lastVersion.get() == version) {
                    summary.calculateSummary();
                    summary.getMessages().thenAccept(messages ->
                        reportMessages(box, version, messages));
                }
            }, delay.toMillis(), TimeUnit.MILLISECONDS);
        }

        public void calculateAnalyzer(int version, Duration delay) {
            calculate(version, lastVersionCalculateAnalyzer, delay, summaryAnalyzer, messagesAnalyzer);
        }

        public void calculateBuilder(int version, Duration delay) {
            calculate(version, lastVersionCalculateBuilder, delay, summaryBuilder, messagesBuilder);
        }

        public ParametricSummaryBridge getSummaryAnalyzer() {
            return summaryAnalyzer;
        }

        public ParametricSummaryBridge getSummaryBuilder() {
            return summaryBuilder;
        }

        public void reportParseErrors(int version, List<Diagnostic> messages) {
            reportMessages(messagesParser, version, messages);
        }

        private void sendDiagnostics() {
            if (client == null) {
                logger.debug("Cannot send diagnostics since the client hasn't been registered yet");
                return;
            }
            var diagnostics = VersionedDiagnostics.union(messagesParser.get(), messagesAnalyzer.get(), messagesBuilder.get());
            logger.trace("Sending diagnostics for {}. {} messages", file, diagnostics.messages.size());
            client.publishDiagnostics(new PublishDiagnosticsParams(
                file.getURI().toString(),
                diagnostics.messages,
                diagnostics.version));
        }
    }
}
