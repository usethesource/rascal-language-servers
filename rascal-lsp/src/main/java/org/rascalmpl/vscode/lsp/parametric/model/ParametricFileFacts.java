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
package org.rascalmpl.vscode.lsp.parametric.model;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.services.LanguageClient;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.util.locations.ColumnMaps;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.vscode.lsp.parametric.ILanguageContributions;
import org.rascalmpl.vscode.lsp.parametric.model.ParametricSummary.SummaryLookup;
import org.rascalmpl.vscode.lsp.util.Lists;
import org.rascalmpl.vscode.lsp.util.Versioned;
import org.rascalmpl.vscode.lsp.util.concurrent.CompletableFutureUtils;
import org.rascalmpl.vscode.lsp.util.locations.Locations;

import io.usethesource.vallang.ISourceLocation;

public class ParametricFileFacts {
    private static final Logger logger = LogManager.getLogger(ParametricFileFacts.class);

    private final Executor exec;
    private final ColumnMaps columns;
    private final ILanguageContributions contrib;
    private final ParametricSummary nullSummary;

    private final Map<ISourceLocation, FileFact> files = new ConcurrentHashMap<>();

    @SuppressWarnings("java:S3077") // Reads/writes happen sequentially
    private volatile @MonotonicNonNull LanguageClient client;

    // The following three fields store factories for summaries. Their intended
    // usage is a follows:
    //
    //   - Scheduled: If a change or a save happens, then the analyzer or the
    //     builder is scheduled to calculate a summary via `analysisFactory` or
    //     via `buildFactory`. The summary is used to fulfil later requests for
    //     documentation, definitions, references, or implementations.
    //
    //   - On-demand: If a request for documentation, definitions, references,
    //     or implementations happens, but if the previously scheduled summary
    //     calculations provide insufficient information to fulfil it (after
    //     completing), then a corresponding on-demand summarizer (if any) is
    //     fired to calculate a summary dedicated to the missing information via
    //     `ondemandSummaryFactory`.
    //
    // The factories are essentially stateless: they have references to a few
    // global data structures, but they do not store any information about
    // individual files. Thus, the factories are shared among them.

    @SuppressWarnings("java:S3077") // Reads/writes happen sequentially
    private volatile CompletableFuture<ScheduledSummaryFactory> analyzerSummaryFactory;
    @SuppressWarnings("java:S3077") // Reads/writes happen sequentially
    private volatile CompletableFuture<ScheduledSummaryFactory> builderSummaryFactory;
    @SuppressWarnings("java:S3077") // Reads/writes happen sequentially
    private volatile CompletableFuture<OndemandSummaryFactory> ondemandSummaryFactory;

    public ParametricFileFacts(Executor exec, ColumnMaps columns, ILanguageContributions contrib) {
        this.exec = exec;
        this.columns = columns;
        this.contrib = contrib;
        this.nullSummary = new NullSummary(exec);
    }

    public void setClient(LanguageClient client) {
        this.client = client;
    }

    public void reportParseErrors(ISourceLocation file, int version, List<Diagnostic> msgs) {
        getFile(file).reportParseErrors(version, msgs);
    }

    private FileFact getFile(ISourceLocation file) {
        var fact = files.get(file);
        if (fact == null) {
            if (URIResolverRegistry.getInstance().exists(file)) {
                fact = new ActualFileFact(file);
                var existing = files.putIfAbsent(file, fact);
                if (existing != null) {
                    fact = existing;
                }
            } else {
                fact = new NopFileFact();
            }
        }
        return fact;
    }

    public void reloadContributions() {
        analyzerSummaryFactory = contrib.getAnalyzerSummaryConfig().thenApply(config ->
            new ScheduledSummaryFactory(config, exec, columns, contrib::analysis));
        builderSummaryFactory = contrib.getBuilderSummaryConfig().thenApply(config ->
            new ScheduledSummaryFactory(config, exec, columns, contrib::build));
        ondemandSummaryFactory = contrib.getOndemandSummaryConfig().thenApply(config ->
            new OndemandSummaryFactory(config, exec, columns, contrib));
    }

    public void invalidateAnalyzer(ISourceLocation file) {
        getFile(file).invalidateAnalyzer(false);
    }

    public void invalidateBuilder(ISourceLocation file) {
        getFile(file).invalidateBuilder(false);
    }

    public void calculateAnalyzer(ISourceLocation file, CompletableFuture<Versioned<ITree>> tree, int version, Duration delay) {
        getFile(file).calculateAnalyzer(tree, version, delay);
    }

    public void calculateBuilder(ISourceLocation file, CompletableFuture<Versioned<ITree>> tree) {
        getFile(file).calculateBuilder(tree);
    }

    public <T> CompletableFuture<List<T>> lookupInSummaries(SummaryLookup<T> lookup, ISourceLocation file, Versioned<ITree> tree, Position cursor) {
        return getFile(file).lookupInSummaries(lookup, tree, cursor);
    }

    public void close(ISourceLocation file) {
        getFile(file).close();
    }

    private interface FileFact {
        void invalidateAnalyzer(boolean isClosing);
        void invalidateBuilder(boolean isClosing);
        void close();
        void calculateAnalyzer(CompletableFuture<Versioned<ITree>> tree, int version, Duration delay);
        void calculateBuilder(CompletableFuture<Versioned<ITree>> tree);
        void reportParseErrors(int version, List<Diagnostic> messages);
        void clearDiagnostics();
        <T> CompletableFuture<List<T>> lookupInSummaries(SummaryLookup<T> lookup, Versioned<ITree> tree, Position cursor);
    }
    
    @SuppressWarnings("java:S3077") // Reads/writes to fields of this class happen sequentially
    private class ActualFileFact implements FileFact {
        private final ISourceLocation file;

        // To replace old diagnostics when new diagnostics become available in a
        // thread-safe way, we need to atomically: (1) check if the version of
        // the new diagnostics is greater than the version of the old
        // diagnostics; (2) if so, replace old with new. This is why
        // `AtomicReference` and `Versioned` are needed in the following three
        // fields.
        private final AtomicReference<Versioned<List<Diagnostic>>> parserDiagnostics = Versioned.atomic(-1, Collections.emptyList());
        private final AtomicReference<Versioned<List<Diagnostic>>> analyzerDiagnostics = Versioned.atomic(-1, Collections.emptyList());
        private final AtomicReference<Versioned<List<Diagnostic>>> builderDiagnostics = Versioned.atomic(-1, Collections.emptyList());

        private final AtomicInteger latestVersionCalculateAnalyzer = new AtomicInteger(-1);

        private volatile CompletableFuture<Versioned<ParametricSummary>> latestAnalyzerAnalysis =
            CompletableFutureUtils.completedFuture(new Versioned<>(-1, nullSummary), exec);
        private volatile CompletableFuture<Versioned<ParametricSummary>> latestBuilderBuild =
            CompletableFutureUtils.completedFuture(new Versioned<>(-1, nullSummary), exec);
        private volatile CompletableFuture<Versioned<ParametricSummary>> latestBuilderAnalysis =
            CompletableFutureUtils.completedFuture(new Versioned<>(-1, nullSummary), exec);

        public ActualFileFact(ISourceLocation file) {
            this.file = file;
        }

        private <T> void reportDiagnostics(AtomicReference<Versioned<T>> current, int version, T messages) {
            var maybeNewer = new Versioned<>(version, messages);
            if (Versioned.replaceIfNewer(current, maybeNewer)) {
                sendDiagnostics();
            }
        }

        @Override
        public void invalidateAnalyzer(boolean isClosing) {
            invalidate(latestAnalyzerAnalysis, isClosing);
        }

        @Override
        public void invalidateBuilder(boolean isClosing) {
            invalidate(latestBuilderAnalysis, isClosing);
            invalidate(latestBuilderBuild, isClosing);
        }

        private void invalidate(@Nullable CompletableFuture<Versioned<ParametricSummary>> summary, boolean isClosing) {
            if (summary != null && !isClosing) {
                summary
                    .thenApply(Versioned<ParametricSummary>::get)
                    .thenAccept(ParametricSummary::invalidate);
            }
        }

        @Override
        public void close() {
            invalidateAnalyzer(true);
            invalidateBuilder(true);

            var analyzerMessages = ParametricSummary.getMessages(latestAnalyzerAnalysis, exec).get();
            var builderMessages = ParametricSummary.getMessages(latestBuilderBuild, exec).get();
            analyzerMessages.thenAcceptBothAsync(builderMessages, (aMessages, bMessages) -> {
                if ((aMessages.isEmpty() && bMessages.isEmpty()) || !URIResolverRegistry.getInstance().exists(file)) {
                    // If there are no messages for this file or the file has been deleted, can we remove it
                    // else VS Code comes back and we've dropped the messages in our internal data
                    remove(file);
                }
            });
        }

        private @Nullable FileFact remove(ISourceLocation file) {
            var removed = files.remove(file.top());
            if (removed != null) {
                removed.clearDiagnostics();
            }
            return removed;
        }

        /**
         * @param version the version of the file for which summary calculation
         * is currently requested
         * @param latestVersion the last version of the file for which summary
         * calculation was previously requested
         * @param delay the duration after which the current request for summary
         * calculation will be granted, unless another request is made in the
         * meantime (in which case the current request is abandoned)
         * @param calculation the actual summary calculation
         * @return a future that supplies the calculated summary if the current
         * request was granted, or an empty summary if it was abandoned
         */
        private CompletableFuture<Versioned<ParametricSummary>> debounce(
                int version, AtomicInteger latestVersion, Duration delay,
                Supplier<CompletableFuture<Versioned<ParametricSummary>>> calculation) {

            latestVersion.set(version);
            // Note: No additional logic (`compareAndSet` in a loop etc.) is
            // needed to change `latestVersion`, because:
            //   - LSP guarantees that the client sends change and save
            //     notifications in-order, and that the server receives them
            //     in-order. Thus, the version number of a file monotonically
            //     increases with each notifications to be processed.
            //   - To process notifications, calls of `didChange` and `didSave`
            //     in `ParametricTextDocumentService` run sequentially and
            //     in-order; these are the only methods that (indirectly) call
            //     `calculate`. Thus, parameter `version` (obtained from the
            //     notifications) monotonically increases with each `calculate`
            //     call.

            var delayed = CompletableFuture.delayedExecutor(delay.toMillis(), TimeUnit.MILLISECONDS, exec);
            var summary = CompletableFuture.supplyAsync(() -> {
                // If no new call to `calculate` has been made after `delay` has
                // passed (i.e., `lastVersion` hasn't changed in the meantime),
                // then run the calculation. Else, abandon this calculation.
                if (latestVersion.get() == version) {
                    return calculation.get();
                } else {
                    return CompletableFutureUtils.completedFuture(new Versioned<>(version, nullSummary), exec);
                }
            }, delayed);

            return summary.thenCompose(Function.identity());
        }

        @Override
        public void calculateAnalyzer(CompletableFuture<Versioned<ITree>> tree, int version, Duration delay) {
            latestAnalyzerAnalysis = debounce(version, latestVersionCalculateAnalyzer, delay, () -> {
                var summary = analyzerSummaryFactory
                    .thenApply(f -> f.createFullSummary(file, tree))
                    .thenCompose(Function.identity());
                ParametricSummary.getMessages(summary, exec)
                    .thenAcceptIfUninterrupted(ms -> reportDiagnostics(analyzerDiagnostics, version, ms));
                return summary;
            });
        }

        /**
         * The main complication when running the builder is that it might
         * produce a subset of the same diagnostics as the analyzer. Thus, as
         * the latest parser/analyzer/builder diagnostics are always reported
         * together, a diff needs to be computed of the analyzer diagnostics and
         * the builder diagnostics to avoid reporting duplicates produced by
         * both analyzer and builder.
         */
        @Override
        public void calculateBuilder(CompletableFuture<Versioned<ITree>> tree) {

            // Schedule the analyzer. This is *always* needed, because the
            // latest result of `calculateAnalyzer` may be for a syntax tree
            // with a greater version than parameter `tree` (because
            // `calculateAnalyzer` has debouncing), or it may be interrupted due
            // to later change (which should not affect the builder).
            latestBuilderAnalysis = analyzerSummaryFactory
                .thenApply(f -> f.createMessagesOnlySummary(file, tree))
                .thenCompose(Function.identity());

            // Schedule the builder and use exactly the same syntax tree as the
            // analyzer. In this way, a reliable diff of the analyzer
            // diagnostics and the builder diagnostics can be computed (by
            // removing the former from the latter).
            latestBuilderBuild = builderSummaryFactory
                .thenApply(f -> f.createFullSummary(file, tree))
                .thenCompose(Function.identity());

            // Only if neither the analyzer nor the builder was interrupted,
            // report diagnostics. Otherwise, *no* diagnostics are reported
            // (instead of reporting an empty list of diagnostics).
            var analyzerMessages = ParametricSummary.getMessages(latestBuilderAnalysis, exec);
            var builderMessages = ParametricSummary.getMessages(latestBuilderBuild, exec);
            analyzerMessages.thenAcceptBothIfUninterrupted(builderMessages, (aMessages, bMessages) -> {
                bMessages.removeAll(aMessages);
                tree.thenAccept(t -> reportDiagnostics(builderDiagnostics, t.version(), bMessages));
            });
        }

        @Override
        public void reportParseErrors(int version, List<Diagnostic> messages) {
            reportDiagnostics(parserDiagnostics, version, messages);
        }

        @Override
        public void clearDiagnostics() {
            var emptyDiagnostics = new Versioned<List<Diagnostic>>(latestVersionCalculateAnalyzer.get(), Collections.emptyList());
            parserDiagnostics.set(emptyDiagnostics);
            analyzerDiagnostics.set(emptyDiagnostics);
            builderDiagnostics.set(emptyDiagnostics);
            if (client != null) {
                client.publishDiagnostics(new PublishDiagnosticsParams(Locations.toUri(file).toString(), Collections.emptyList()));
            }
        }

        private void sendDiagnostics() {
            if (client == null) {
                logger.debug("Cannot send diagnostics since the client hasn't been registered yet");
                return;
            }

            // Read the atomic references once for the whole method, to ensure
            // that published diagnostics correspond with logged versions
            var fromParser = parserDiagnostics.get();
            var fromAnalyzer = analyzerDiagnostics.get();
            var fromBuilder = builderDiagnostics.get();

            var diagnostics = Lists.union(
                fromParser.get(),
                fromAnalyzer.get(),
                fromBuilder.get());

            logger.trace(
                "Sending {} diagnostic(s) for {} (parser: v{}; analyzer: v{}; builder: v{})",
                diagnostics.size(), file, fromParser.version(), fromAnalyzer.version(), fromBuilder.version());

            client.publishDiagnostics(new PublishDiagnosticsParams(
                Locations.toUri(file).toString(),
                diagnostics));
        }

        /**
         * Dynamically routes the lookup to the latest analyzer summary, to the
         * latest builder summary, or to an on-the-fly created on-demand
         * summary. Note: Static routing is less suitable here, because which
         * summary to use depends on the version of `tree`, which is known only
         * dynamically.
         */
        @Override
        public <T> CompletableFuture<List<T>> lookupInSummaries(SummaryLookup<T> lookup, Versioned<ITree> tree, Position cursor) {
            return latestAnalyzerAnalysis
                .thenCombine(latestBuilderBuild, (a, b) -> lookupInSummaries(lookup, tree, cursor, a, b))
                .thenCompose(Function.identity());
        }

        private <T> CompletableFuture<List<T>> lookupInSummaries(
                SummaryLookup<T> lookup, Versioned<ITree> tree, Position cursor,
                Versioned<ParametricSummary> analyzerSummary,
                Versioned<ParametricSummary> builderSummary) {

            // If a builder summary is available (i.e., a builder exists *and*
            // provides), and if it's of the right version, use that.
            if (builderSummary.version() == tree.version()) {
                var result = lookup.apply(builderSummary.get(), cursor);
                if (result != null) {
                    logger.trace("Look-up in builder summary succeeded");
                    return result.get();
                }
            }

            // Else, if an analyzer summary is available (i.e., an analyzer
            // exists *and* provides), and if it's of the right version, use
            // that.
            if (analyzerSummary.version() == tree.version()) {
                var result = lookup.apply(analyzerSummary.get(), cursor);
                if (result != null) {
                    logger.trace("Look-up in analyzer summary succeeded");
                    return result.get();
                }
            }

            // Else, if an on-demand summary is available, use that.
            return ondemandSummaryFactory
                .thenCompose(f -> {
                    var result = f.createSummaryThenLookup(file, tree, cursor, lookup);
                    if (result != null) {
                        logger.trace("Look-up in on-demand summary succeeded");
                        return result.get();
                    } else {
                        logger.trace("Look-up failed");
                        return CompletableFutureUtils.completedFuture(Collections.<T>emptyList(), exec);
                    }});
        }
    }

    class NopFileFact implements FileFact {
        @Override
        public void invalidateAnalyzer(boolean isClosing) {
            // NOP
        }

        @Override
        public void invalidateBuilder(boolean isClosing) {
            // NOP
        }

        @Override
        public void close() {
            // NOP
        }

        @Override
        public void calculateAnalyzer(CompletableFuture<Versioned<ITree>> tree, int version, Duration delay) {
            // NOP
        }

        @Override
        public void calculateBuilder(CompletableFuture<Versioned<ITree>> tree) {
            // NOP
        }

        @Override
        public void reportParseErrors(int version, List<Diagnostic> messages) {
            // NOP
        }

        @Override
        public void clearDiagnostics() {
            // NOP
        }

        @Override
        public <T> CompletableFuture<List<T>> lookupInSummaries(SummaryLookup<T> lookup, Versioned<ITree> tree, Position cursor) {
            return CompletableFuture.completedFuture(List.of());
        }
    }
}
