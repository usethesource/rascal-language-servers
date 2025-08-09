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
package org.rascalmpl.vscode.lsp.parametric;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.function.BinaryOperator;
import java.util.function.Function;

import org.apache.commons.compress.harmony.unpack200.IcTuple;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.vscode.lsp.util.concurrent.InterruptibleFuture;
import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.IMap;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.ITuple;
import io.usethesource.vallang.IValue;

@SuppressWarnings("java:S3077") // Fields in this class are read/written sequentially
public class LanguageContributionsMultiplexer implements ILanguageContributions {

    private final ExecutorService ownExecuter;
    private final String name;

    private static final <T> CompletableFuture<T> failedInitialization() {
        return CompletableFuture.failedFuture(new RuntimeException("No contributions registered"));
    }

    private volatile @MonotonicNonNull ILanguageContributions  parsing = null;

    private volatile CompletableFuture<ILanguageContributions> analysis = failedInitialization();
    private volatile CompletableFuture<ILanguageContributions> build = failedInitialization();
    private volatile CompletableFuture<ILanguageContributions> documentSymbol = failedInitialization();
    private volatile CompletableFuture<ILanguageContributions> codeLens = failedInitialization();
    private volatile CompletableFuture<ILanguageContributions> inlayHint = failedInitialization();
    private volatile CompletableFuture<ILanguageContributions> execution = failedInitialization();
    private volatile CompletableFuture<ILanguageContributions> hover = failedInitialization();
    private volatile CompletableFuture<ILanguageContributions> definition = failedInitialization();
    private volatile CompletableFuture<ILanguageContributions> references = failedInitialization();
    private volatile CompletableFuture<ILanguageContributions> implementation = failedInitialization();
    private volatile CompletableFuture<ILanguageContributions> codeAction = failedInitialization();
    private volatile CompletableFuture<ILanguageContributions> prepareRename = failedInitialization();
    private volatile CompletableFuture<ILanguageContributions> rename = failedInitialization();
    private volatile CompletableFuture<ILanguageContributions> didRenameFiles = failedInitialization();

    private volatile CompletableFuture<Boolean> hasAnalysis = failedInitialization();
    private volatile CompletableFuture<Boolean> hasBuild = failedInitialization();
    private volatile CompletableFuture<Boolean> hasDocumentSymbol = failedInitialization();
    private volatile CompletableFuture<Boolean> hasCodeLens = failedInitialization();
    private volatile CompletableFuture<Boolean> hasInlayHint = failedInitialization();
    private volatile CompletableFuture<Boolean> hasExecution = failedInitialization();
    private volatile CompletableFuture<Boolean> hasHover = failedInitialization();
    private volatile CompletableFuture<Boolean> hasDefinition = failedInitialization();
    private volatile CompletableFuture<Boolean> hasReferences = failedInitialization();
    private volatile CompletableFuture<Boolean> hasImplementation = failedInitialization();
    private volatile CompletableFuture<Boolean> hasCodeAction = failedInitialization();
    private volatile CompletableFuture<Boolean> hasRename = failedInitialization();
    private volatile CompletableFuture<Boolean> hasDidRenameFiles = failedInitialization();

    private volatile CompletableFuture<Boolean> specialCaseHighlighting = failedInitialization();

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
        parsing = firstOrFail();

        analysis = findFirstOrDefault(ILanguageContributions::hasAnalysis);
        build = findFirstOrDefault(ILanguageContributions::hasBuild);
        documentSymbol = findFirstOrDefault(ILanguageContributions::hasDocumentSymbol);
        codeLens = findFirstOrDefault(ILanguageContributions::hasCodeLens);
        inlayHint = findFirstOrDefault(ILanguageContributions::hasInlayHint);
        execution = findFirstOrDefault(ILanguageContributions::hasExecution);
        hover = findFirstOrDefault(ILanguageContributions::hasHover);
        definition = findFirstOrDefault(ILanguageContributions::hasDefinition);
        references = findFirstOrDefault(ILanguageContributions::hasReferences);
        implementation = findFirstOrDefault(ILanguageContributions::hasImplementation);
        codeAction = findFirstOrDefault(ILanguageContributions::hasCodeAction);
        rename = findFirstOrDefault(ILanguageContributions::hasRename);
        prepareRename = findFirstOrDefault(ILanguageContributions::hasRename);
        didRenameFiles = findFirstOrDefault(ILanguageContributions::hasDidRenameFiles);

        hasAnalysis = anyTrue(ILanguageContributions::hasAnalysis);
        hasBuild = anyTrue(ILanguageContributions::hasBuild);
        hasDocumentSymbol = anyTrue(ILanguageContributions::hasDocumentSymbol);
        hasCodeLens = anyTrue(ILanguageContributions::hasCodeLens);
        hasInlayHint = anyTrue(ILanguageContributions::hasInlayHint);
        hasExecution = anyTrue(ILanguageContributions::hasExecution);
        hasHover = anyTrue(ILanguageContributions::hasHover);
        hasDefinition = anyTrue(ILanguageContributions::hasDefinition);
        hasReferences = anyTrue(ILanguageContributions::hasReferences);
        hasImplementation = anyTrue(ILanguageContributions::hasImplementation);
        hasRename = anyTrue(ILanguageContributions::hasRename);
        hasDidRenameFiles = anyTrue(ILanguageContributions::hasDidRenameFiles);

        // Always use the special-case highlighting status of *the first*
        // contribution (possibly using the default value in the Rascal ADT if
        // it's not explicitly set), just as for `parsing` itself
        specialCaseHighlighting = firstOrFail().specialCaseHighlighting();

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
    public CompletableFuture<ITree> parsing(ISourceLocation loc, String input) {
        var p = parsing;
        if (p == null) {
            return failedInitialization();
        }
        return p.parsing(loc, input);
    }

    private <T> InterruptibleFuture<T> flatten(CompletableFuture<ILanguageContributions> target, Function<ILanguageContributions, InterruptibleFuture<T>> call) {
        return InterruptibleFuture.flatten(target.thenApply(call), ownExecuter);
    }

    @Override
    public InterruptibleFuture<IList> documentSymbol(ITree input) {
        return flatten(documentSymbol, c -> c.documentSymbol(input));
    }

    @Override
    public InterruptibleFuture<IConstructor> analysis(ISourceLocation loc, ITree input) {
        return flatten(analysis, c -> c.analysis(loc, input));
    }

    @Override
    public InterruptibleFuture<IConstructor> build(ISourceLocation loc, ITree input) {
        return flatten(build, c -> c.build(loc, input));
    }

    @Override
    public InterruptibleFuture<IList> codeLens(ITree input) {
        return flatten(codeLens, c -> c.codeLens(input));
    }

    @Override
    public InterruptibleFuture<@Nullable IValue> execution(String command) {
        return flatten(execution, c -> c.execution(command));
    }

    @Override
    public CompletableFuture<IList> parseCodeActions(String command) {
        return execution.thenApply(c -> c.parseCodeActions(command)).thenCompose(Function.identity());
    }

    @Override
    public InterruptibleFuture<IList> inlayHint(@Nullable ITree input) {
        return flatten(inlayHint, c -> c.inlayHint(input));
    }

    @Override
    public InterruptibleFuture<ISourceLocation> prepareRename(IList focus) {
        return flatten(prepareRename, c -> c.prepareRename(focus));
    }

    @Override
    public InterruptibleFuture<ITuple> rename(IList focus, String name) {
        return flatten(rename, c -> c.rename(focus, name));
    }

    @Override
    public InterruptibleFuture<ITuple> didRenameFiles(IList oldToNew) {
        return flatten(rename, c -> c.didRenameFiles(oldToNew));
    }

    @Override
    public InterruptibleFuture<ISet> hover(IList focus) {
        return flatten(hover, c -> c.hover(focus));
    }

    @Override
    public InterruptibleFuture<ISet> definition(IList focus) {
        return flatten(definition, c -> c.definition(focus));
    }

    @Override
    public InterruptibleFuture<ISet> references(IList focus) {
        return flatten(references, c -> c.references(focus));
    }

    @Override
    public InterruptibleFuture<ISet> implementation(IList focus) {
        return flatten(implementation, c -> c.implementation(focus));
    }

    @Override
    public InterruptibleFuture<IList> codeAction(IList focus) {
        return flatten(codeAction, c -> c.codeAction(focus));
    }

    @Override
    public CompletableFuture<Boolean> hasCodeAction() {
        return hasCodeAction;
    }

    @Override
    public CompletableFuture<Boolean> hasHover() {
        return hasHover;
    }

    @Override
    public CompletableFuture<Boolean> hasDefinition() {
        return hasDefinition;
    }

    @Override
    public CompletableFuture<Boolean> hasReferences() {
        return hasReferences;
    }

    @Override
    public CompletableFuture<Boolean> hasImplementation() {
        return hasImplementation;
    }

    @Override
    public CompletableFuture<Boolean> hasDocumentSymbol() {
        return hasDocumentSymbol;
    }

    @Override
    public CompletableFuture<Boolean> hasAnalysis() {
        return hasAnalysis;
    }

    @Override
    public CompletableFuture<Boolean> hasBuild() {
        return hasBuild;
    }

    @Override
    public CompletableFuture<Boolean> hasCodeLens() {
        return hasCodeLens;
    }

    @Override
    public CompletableFuture<Boolean> hasExecution() {
        return hasExecution;
    }

    @Override
    public CompletableFuture<Boolean> hasInlayHint() {
        return hasInlayHint;
    }

    @Override
    public CompletableFuture<Boolean> hasRename() {
        return hasRename;
    }

    @Override
    public CompletableFuture<Boolean> hasDidRenameFiles() {
        return hasDidRenameFiles;
    }

    @Override
    public CompletableFuture<Boolean> specialCaseHighlighting() {
        return specialCaseHighlighting;
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

    @Override
    public void cancelProgress(String progressId) {
        contributions.forEach(klc -> klc.contrib.cancelProgress(progressId));
    }
}
