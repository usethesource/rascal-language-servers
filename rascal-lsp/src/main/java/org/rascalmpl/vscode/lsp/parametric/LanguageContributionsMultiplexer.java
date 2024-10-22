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

    private volatile @MonotonicNonNull ILanguageContributions  parsingService = null;

    private volatile CompletableFuture<ILanguageContributions> getAnalysisService = failedInitialization();
    private volatile CompletableFuture<ILanguageContributions> getBuildService = failedInitialization();
    private volatile CompletableFuture<ILanguageContributions> getDocumentSymbolService = failedInitialization();
    private volatile CompletableFuture<ILanguageContributions> getCodeLensService = failedInitialization();
    private volatile CompletableFuture<ILanguageContributions> getInlayHintService = failedInitialization();
    private volatile CompletableFuture<ILanguageContributions> getExecutionService = failedInitialization();
    private volatile CompletableFuture<ILanguageContributions> getHoverService = failedInitialization();
    private volatile CompletableFuture<ILanguageContributions> getDefinitionService = failedInitialization();
    private volatile CompletableFuture<ILanguageContributions> getReferencesService = failedInitialization();
    private volatile CompletableFuture<ILanguageContributions> getImplementationService = failedInitialization();
    private volatile CompletableFuture<ILanguageContributions> getCodeActionService = failedInitialization();

    private volatile CompletableFuture<Boolean> hasAnalysisService = failedInitialization();
    private volatile CompletableFuture<Boolean> hasBuildService = failedInitialization();
    private volatile CompletableFuture<Boolean> hasDocumentSymbolService = failedInitialization();
    private volatile CompletableFuture<Boolean> hasCodeLensService = failedInitialization();
    private volatile CompletableFuture<Boolean> hasInlayHintService = failedInitialization();
    private volatile CompletableFuture<Boolean> hasExecutionService = failedInitialization();
    private volatile CompletableFuture<Boolean> hasHoverService = failedInitialization();
    private volatile CompletableFuture<Boolean> hasDefinitionService = failedInitialization();
    private volatile CompletableFuture<Boolean> hasReferencesService = failedInitialization();
    private volatile CompletableFuture<Boolean> hasImplementationService = failedInitialization();
    private volatile CompletableFuture<Boolean> hasCodeActionService = failedInitialization();

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
        parsingService = firstOrFail();

        getAnalysisService       = findFirstOrDefault(ILanguageContributions::hasAnalysisService);
        getBuildService          = findFirstOrDefault(ILanguageContributions::hasBuildService);
        getDocumentSymbolService = findFirstOrDefault(ILanguageContributions::hasDocumentSymbolService);
        getCodeLensService       = findFirstOrDefault(ILanguageContributions::hasCodeLensService);
        getInlayHintService      = findFirstOrDefault(ILanguageContributions::hasInlayHintService);
        getExecutionService      = findFirstOrDefault(ILanguageContributions::hasExecutionService);
        getHoverService          = findFirstOrDefault(ILanguageContributions::hasHoverService);
        getDefinitionService     = findFirstOrDefault(ILanguageContributions::hasDefinitionService);
        getReferencesService     = findFirstOrDefault(ILanguageContributions::hasReferencesService);
        getImplementationService = findFirstOrDefault(ILanguageContributions::hasImplementationService);
        getCodeActionService     = findFirstOrDefault(ILanguageContributions::hasCodeActionService);

        hasAnalysisService       = anyTrue(ILanguageContributions::hasAnalysisService);
        hasBuildService          = anyTrue(ILanguageContributions::hasBuildService);
        hasDocumentSymbolService = anyTrue(ILanguageContributions::hasDocumentSymbolService);
        hasCodeLensService       = anyTrue(ILanguageContributions::hasCodeLensService);
        hasInlayHintService      = anyTrue(ILanguageContributions::hasInlayHintService);
        hasExecutionService      = anyTrue(ILanguageContributions::hasExecutionService);
        hasHoverService          = anyTrue(ILanguageContributions::hasHoverService);
        hasDefinitionService     = anyTrue(ILanguageContributions::hasDefinitionService);
        hasReferencesService     = anyTrue(ILanguageContributions::hasReferencesService);
        hasImplementationService = anyTrue(ILanguageContributions::hasImplementationService);

        analyzerSummaryConfig = anyTrue(ILanguageContributions::getAnalyzerSummaryConfig, SummaryConfig.FALSY, SummaryConfig::or);
        builderSummaryConfig  = anyTrue(ILanguageContributions::getBuilderSummaryConfig, SummaryConfig.FALSY, SummaryConfig::or);
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
    public CompletableFuture<ITree> runParsingService(ISourceLocation loc, String input) {
        var p = parsingService;
        if (p == null) {
            return failedInitialization();
        }
        return p.runParsingService(loc, input);
    }

    private <T> InterruptibleFuture<T> flatten(CompletableFuture<ILanguageContributions> target, Function<ILanguageContributions, InterruptibleFuture<T>> call) {
        return InterruptibleFuture.flatten(target.thenApply(call), ownExecuter);
    }

    @Override
    public InterruptibleFuture<IList> runDocumentSymbolService(ITree input) {
        return flatten(getDocumentSymbolService, c -> c.runDocumentSymbolService(input));
    }

    @Override
    public InterruptibleFuture<IConstructor> runAnalysisService(ISourceLocation loc, ITree input) {
        return flatten(getAnalysisService, c -> c.runAnalysisService(loc, input));
    }

    @Override
    public InterruptibleFuture<IConstructor> runBuildService(ISourceLocation loc, ITree input) {
        return flatten(getBuildService, c -> c.runBuildService(loc, input));
    }

    @Override
    public InterruptibleFuture<IList> runCodeLensService(ITree input) {
        return flatten(getCodeLensService, c -> c.runCodeLensService(input));
    }

    @Override
    public InterruptibleFuture<@Nullable IValue> runExecutionService(String command) {
        return flatten(getExecutionService, c -> c.runExecutionService(command));
    }

    @Override
    public CompletableFuture<IList> parseCodeActions(String command) {
        return getExecutionService.thenApply(c -> c.parseCodeActions(command)).thenCompose(Function.identity());
    }

    @Override
    public InterruptibleFuture<IList> runInlayHintService(@Nullable ITree input) {
        return flatten(getInlayHintService, c -> c.runInlayHintService(input));
    }

    @Override
    public InterruptibleFuture<ISet> runHoverService(IList focus) {
        return flatten(getHoverService, c -> c.runHoverService(focus));
    }

    @Override
    public InterruptibleFuture<ISet> runDefinitionService(IList focus) {
        return flatten(getDefinitionService, c -> c.runDefinitionService(focus));
    }

    @Override
    public InterruptibleFuture<ISet> runReferencesService(IList focus) {
        return flatten(getReferencesService, c -> c.runReferencesService(focus));
    }

    @Override
    public InterruptibleFuture<ISet> runImplementationService(IList focus) {
        return flatten(getImplementationService, c -> c.runImplementationService(focus));
    }

    @Override
    public InterruptibleFuture<IList> runCodeActionService(IList focus) {
        return flatten(getCodeActionService, c -> c.runCodeActionService(focus));
    }

    @Override
    public CompletableFuture<Boolean> hasCodeActionService() {
        return hasCodeActionService;
    }

    @Override
    public CompletableFuture<Boolean> hasHoverService() {
        return hasHoverService;
    }

    @Override
    public CompletableFuture<Boolean> hasDefinitionService() {
        return hasDefinitionService;
    }

    @Override
    public CompletableFuture<Boolean> hasReferencesService() {
        return hasReferencesService;
    }

    @Override
    public CompletableFuture<Boolean> hasImplementationService() {
        return hasImplementationService;
    }

    @Override
    public CompletableFuture<Boolean> hasDocumentSymbolService() {
        return hasDocumentSymbolService;
    }

    @Override
    public CompletableFuture<Boolean> hasAnalysisService() {
        return hasAnalysisService;
    }

    @Override
    public CompletableFuture<Boolean> hasBuildService() {
        return hasBuildService;
    }

    @Override
    public CompletableFuture<Boolean> hasCodeLensService() {
        return hasCodeLensService;
    }

    @Override
    public CompletableFuture<Boolean> hasExecutionService() {
        return hasExecutionService;
    }

    @Override
    public CompletableFuture<Boolean> hasInlayHintService() {
        return hasInlayHintService;
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
