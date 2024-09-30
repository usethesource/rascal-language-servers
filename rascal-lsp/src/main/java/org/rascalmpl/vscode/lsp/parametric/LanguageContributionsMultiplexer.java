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
package org.rascalmpl.vscode.lsp.parametric;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.vscode.lsp.util.concurrent.InterruptibleFuture;
import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValue;

@SuppressWarnings("java:S3077") // Fields in this class are read/written sequentially
public class LanguageContributionsMultiplexer implements ILanguageContributions {

    private final ExecutorService ownExecuter;
    private final String name;

    private static final <T> CompletableFuture<T> failedInitialization() {
        return CompletableFuture.failedFuture(new RuntimeException("No contributions registered"));
    }

    private volatile @MonotonicNonNull ILanguageContributions parser = null;
    private volatile CompletableFuture<ILanguageContributions> outliner = failedInitialization();
    private volatile CompletableFuture<ILanguageContributions> analyzer = failedInitialization();
    private volatile CompletableFuture<ILanguageContributions> builder = failedInitialization();
    private volatile CompletableFuture<ILanguageContributions> lensDetector = failedInitialization();
    private volatile CompletableFuture<ILanguageContributions> commandExecutor = failedInitialization();
    private volatile CompletableFuture<ILanguageContributions> inlayHinter = failedInitialization();
    private volatile CompletableFuture<ILanguageContributions> definer = failedInitialization();
    private volatile CompletableFuture<ILanguageContributions> documenter = failedInitialization();
    private volatile CompletableFuture<ILanguageContributions> referrer = failedInitialization();
    private volatile CompletableFuture<ILanguageContributions> implementer = failedInitialization();
    private volatile CompletableFuture<ILanguageContributions> codeActionContributor = failedInitialization();

    private volatile CompletableFuture<Boolean> hasDocumenter = failedInitialization();
    private volatile CompletableFuture<Boolean> hasDefiner = failedInitialization();
    private volatile CompletableFuture<Boolean> hasReferrer = failedInitialization();
    private volatile CompletableFuture<Boolean> hasImplementer = failedInitialization();

    private volatile CompletableFuture<Boolean> hasOutliner = failedInitialization();
    private volatile CompletableFuture<Boolean> hasAnalyzer = failedInitialization();
    private volatile CompletableFuture<Boolean> hasBuilder = failedInitialization();
    private volatile CompletableFuture<Boolean> hasLensDetector = failedInitialization();
    private volatile CompletableFuture<Boolean> hasCommandExecutor = failedInitialization();
    private volatile CompletableFuture<Boolean> hasInlayHinter = failedInitialization();
    private volatile CompletableFuture<Boolean> hasCodeActionContributor = failedInitialization();

    private volatile CompletableFuture<SummaryConfig> analyzerSummaryConfig;
    private volatile CompletableFuture<SummaryConfig> builderSummaryConfig;
    private volatile CompletableFuture<SummaryConfig> ondemandSummaryConfig;

    public LanguageContributionsMultiplexer(String name, ExecutorService ownService) {
        this.name = name;
        this.ownExecuter = ownService;
    }

    private final CopyOnWriteArrayList<KeyedLanguageContribution> contributions = new CopyOnWriteArrayList<>();

    private static final class KeyedLanguageContribution {
        public final String key;
        public final ILanguageContributions contrib;
        public KeyedLanguageContribution(String key, ILanguageContributions contrib) {
            this.key = key;
            this.contrib = contrib;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof KeyedLanguageContribution) {
                return this.key.equals(((KeyedLanguageContribution)obj).key);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return key.hashCode();
        }
    }

    public void addContributor(String contribKey, ILanguageContributions contrib) {
        var newEntry = new KeyedLanguageContribution(contribKey, contrib);
        contributions.remove(newEntry); // we use the fact that the equals only checks the key
        contributions.addIfAbsent(newEntry);
        calculateRouting();
    }

    /**
     * @returns false if the multiplexer is empty, and therefore should not be used anymore
     */
    public boolean removeContributor(String contribKey) {
        contributions.removeIf(e -> e.key.equals(contribKey) || e.key.equals(contribKey + "$parser"));
        if (contributions.isEmpty()) {
            return false;
        }
        calculateRouting();
        return true;
    }

    private synchronized void calculateRouting() {
        // after contributions have changed, we calculate the routing
        // this is to avoid doing this lookup every time we get a request
        // we calculate the "route" once, and then just chain onto the completed
        // future
        parser = firstOrFail();
        outliner = findFirstOrDefault(ILanguageContributions::hasOutliner);
        analyzer = findFirstOrDefault(ILanguageContributions::hasAnalyzer);
        builder = findFirstOrDefault(ILanguageContributions::hasBuilder);
        lensDetector = findFirstOrDefault(ILanguageContributions::hasLensDetector);
        commandExecutor = findFirstOrDefault(ILanguageContributions::hasCommandExecutor);
        inlayHinter = findFirstOrDefault(ILanguageContributions::hasInlayHinter);
        definer = findFirstOrDefault(ILanguageContributions::hasDefiner);
        documenter = findFirstOrDefault(ILanguageContributions::hasDocumenter);
        referrer = findFirstOrDefault(ILanguageContributions::hasReferrer);
        implementer = findFirstOrDefault(ILanguageContributions::hasImplementer);
        codeActionContributor = findFirstOrDefault(ILanguageContributions::hasCodeActionsContributor);

        hasDocumenter = anyTrue(ILanguageContributions::hasDocumenter);
        hasDefiner = anyTrue(ILanguageContributions::hasDefiner);
        hasReferrer = anyTrue(ILanguageContributions::hasReferrer);
        hasImplementer = anyTrue(ILanguageContributions::hasImplementer);

        hasOutliner = anyTrue(ILanguageContributions::hasOutliner);
        hasAnalyzer = anyTrue(ILanguageContributions::hasAnalyzer);
        hasBuilder = anyTrue(ILanguageContributions::hasBuilder);
        hasLensDetector = anyTrue(ILanguageContributions::hasLensDetector);
        hasCommandExecutor = anyTrue(ILanguageContributions::hasCommandExecutor);
        hasInlayHinter = anyTrue(ILanguageContributions::hasInlayHinter);

        analyzerSummaryConfig = anyTrue(ILanguageContributions::getAnalyzerSummaryConfig, SummaryConfig.FALSY, SummaryConfig::or);
        builderSummaryConfig = anyTrue(ILanguageContributions::getBuilderSummaryConfig, SummaryConfig.FALSY, SummaryConfig::or);
        ondemandSummaryConfig = anyTrue(ILanguageContributions::getOndemandSummaryConfig, SummaryConfig.FALSY, SummaryConfig::or);
    }

    private ILanguageContributions firstOrFail() {
        var it = contributions.iterator();
        if (!it.hasNext()) {
            throw new RuntimeException("No more language contributions registered for " + name);
        }
        return it.next().contrib;
    }



    private CompletableFuture<ILanguageContributions> findFirstOrDefault(Function<ILanguageContributions, CompletableFuture<Boolean>> filter) {
        return CompletableFuture.supplyAsync(() -> {
            for (var c : contributions) {
                try {
                    // since we are inside the completable future, it's okay to do a get
                    if (filter.apply(c.contrib).get().booleanValue()) {
                        return c.contrib;
                    }
                } catch(InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception ignored) {
                    continue;
                }
            }
            // otherwise return the first one, that contains defaults on what to do if it's missing
            return firstOrFail();
        }, ownExecuter);
    }

    private CompletableFuture<Boolean> anyTrue(Function<ILanguageContributions, CompletableFuture<Boolean>> predicate) {
        return anyTrue(predicate, false, Boolean::logicalOr);
    }

    private <T> CompletableFuture<T> anyTrue(
            Function<ILanguageContributions, CompletableFuture<T>> predicate,
            T falsy, BinaryOperator<T> or) {

        var result = CompletableFuture.completedFuture(falsy);
        // no short-circuiting, but it's not problem, it's only triggered at the beginning of a registry
        // pretty soon the future will be completed.
        for (var c: contributions) {
            var checkCurrent = predicate.apply(c.contrib)
                .exceptionally(e -> falsy);
            result = result.thenCombine(checkCurrent, or);
        }
        return result;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public CompletableFuture<ITree> parseSourceFile(ISourceLocation loc, String input) {
        var p = parser;
        if (p == null) {
            return failedInitialization();
        }
        return p.parseSourceFile(loc, input);
    }


    private <T> InterruptibleFuture<T> flatten(CompletableFuture<ILanguageContributions> target, Function<ILanguageContributions, InterruptibleFuture<T>> call) {
        return InterruptibleFuture.flatten(target.thenApply(call), ownExecuter);
    }

    @Override
    public InterruptibleFuture<IList> outline(ITree input) {
        return flatten(outliner, c -> c.outline(input));
    }

    @Override
    public InterruptibleFuture<IConstructor> analyze(ISourceLocation loc, ITree input) {
        return flatten(analyzer, c -> c.analyze(loc, input));
    }

    @Override
    public InterruptibleFuture<IConstructor> build(ISourceLocation loc, ITree input) {
        return flatten(builder, c -> c.build(loc, input));
    }

    @Override
    public InterruptibleFuture<ISet> lenses(ITree input) {
        return flatten(lensDetector, c -> c.lenses(input));
    }

    @Override
    public InterruptibleFuture<@Nullable IValue> executeCommand(String command) {
        return flatten(commandExecutor, c -> c.executeCommand(command));
    }

    @Override
    public CompletableFuture<IList> parseCodeActions(String command) {
        return commandExecutor.thenApply(c -> c.parseCodeActions(command)).thenCompose(Function.identity());
    }

    @Override
    public InterruptibleFuture<IList> inlayHint(@Nullable ITree input) {
        return flatten(inlayHinter, c -> c.inlayHint(input));
    }

    @Override
    public InterruptibleFuture<ISet> documentation(IList focus) {
        return flatten(documenter, c -> c.documentation(focus));
    }

    @Override
    public InterruptibleFuture<ISet> definitions(IList focus) {
        return flatten(definer, c -> c.definitions(focus));
    }

    @Override
    public InterruptibleFuture<ISet> references(IList focus) {
        return flatten(referrer, c -> c.references(focus));
    }

    @Override
    public InterruptibleFuture<ISet> implementations(IList focus) {
        return flatten(implementer, c -> c.implementations(focus));
    }

    @Override
    public InterruptibleFuture<IList> codeActions(IList focus) {
        return flatten(codeActionContributor, c -> c.codeActions(focus));
    }

    @Override
    public CompletableFuture<Boolean> hasCodeActionsContributor() {
        return hasCodeActionContributor;
    }

    @Override
    public CompletableFuture<Boolean> hasDocumenter() {
        return hasDocumenter;
    }

    @Override
    public CompletableFuture<Boolean> hasDefiner() {
        return hasDefiner;
    }

    @Override
    public CompletableFuture<Boolean> hasReferrer() {
        return hasReferrer;
    }

    @Override
    public CompletableFuture<Boolean> hasImplementer() {
        return hasImplementer;
    }

    @Override
    public CompletableFuture<Boolean> hasOutliner() {
        return hasOutliner;
    }

    @Override
    public CompletableFuture<Boolean> hasAnalyzer() {
        return hasAnalyzer;
    }

    @Override
    public CompletableFuture<Boolean> hasBuilder() {
        return hasBuilder;
    }

    @Override
    public CompletableFuture<Boolean> hasLensDetector() {
        return hasLensDetector;
    }

    @Override
    public CompletableFuture<Boolean> hasCommandExecutor() {
        return hasCommandExecutor;
    }

    @Override
    public CompletableFuture<Boolean> hasInlayHinter() {
        return hasInlayHinter;
    }

    @Override
    public CompletableFuture<SummaryConfig> getAnalyzerSummaryConfig() {
        return analyzerSummaryConfig;
    }

    @Override
    public CompletableFuture<SummaryConfig> getBuilderSummaryConfig() {
        return builderSummaryConfig;
    }

    @Override
    public CompletableFuture<SummaryConfig> getOndemandSummaryConfig() {
        return ondemandSummaryConfig;
    }
}
