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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.vscode.lsp.util.concurrent.CompletableFutureUtils;
import org.rascalmpl.vscode.lsp.util.concurrent.InterruptibleFuture;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IInteger;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.ITuple;
import io.usethesource.vallang.IValue;

@SuppressWarnings("java:S3077") // Fields in this class are read/written sequentially
public class LanguageContributionsMultiplexer implements ILanguageContributions {

    private static final Logger logger = LogManager.getLogger(LanguageContributionsMultiplexer.class);

    private final ExecutorService exec;
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
    private volatile CompletableFuture<ILanguageContributions> rename = failedInitialization();
    private volatile CompletableFuture<ILanguageContributions> didRenameFiles = failedInitialization();
    private volatile CompletableFuture<ILanguageContributions> selectionRange = failedInitialization();
    private volatile CompletableFuture<ILanguageContributions> callHierarchy = failedInitialization();
    private volatile CompletableFuture<ILanguageContributions> completion = failedInitialization();

    private volatile CompletableFuture<Boolean> providesAnalysis = failedInitialization();
    private volatile CompletableFuture<Boolean> providesBuild = failedInitialization();
    private volatile CompletableFuture<Boolean> providesDocumentSymbol = failedInitialization();
    private volatile CompletableFuture<Boolean> providesCodeLens = failedInitialization();
    private volatile CompletableFuture<Boolean> providesInlayHint = failedInitialization();
    private volatile CompletableFuture<Boolean> providesExecution = failedInitialization();
    private volatile CompletableFuture<Boolean> providesHover = failedInitialization();
    private volatile CompletableFuture<Boolean> providesDefinition = failedInitialization();
    private volatile CompletableFuture<Boolean> providesReferences = failedInitialization();
    private volatile CompletableFuture<Boolean> providesImplementation = failedInitialization();
    private volatile CompletableFuture<Boolean> providesCodeAction = failedInitialization();
    private volatile CompletableFuture<Boolean> providesRename = failedInitialization();
    private volatile CompletableFuture<Boolean> providesDidRenameFiles = failedInitialization();
    private volatile CompletableFuture<Boolean> providesSelectionRange = failedInitialization();
    private volatile CompletableFuture<Boolean> providesCallHierarchy = failedInitialization();
    private volatile CompletableFuture<Boolean> providesCompletion = failedInitialization();

    private volatile CompletableFuture<Boolean> specialCaseHighlighting = failedInitialization();

    private volatile CompletableFuture<SummaryConfig> analyzerSummaryConfig;
    private volatile CompletableFuture<SummaryConfig> builderSummaryConfig;
    private volatile CompletableFuture<SummaryConfig> ondemandSummaryConfig;

    public LanguageContributionsMultiplexer(String name, ExecutorService ownService) {
        this.name = name;
        this.exec = ownService;
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
        public boolean equals(@Nullable Object obj) {
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

        analysis = findFirstOrDefault(ILanguageContributions::providesAnalysis, "analysis");
        build = findFirstOrDefault(ILanguageContributions::providesBuild, "build");
        documentSymbol = findFirstOrDefault(ILanguageContributions::providesDocumentSymbol, "documentSymbol");
        codeLens = findFirstOrDefault(ILanguageContributions::providesCodeLens, "codeLens");
        inlayHint = findFirstOrDefault(ILanguageContributions::providesInlayHint, "inlayHint");
        execution = findFirstOrDefault(ILanguageContributions::providesExecution, "execution");
        hover = findFirstOrDefault(ILanguageContributions::providesHover, "hover");
        definition = findFirstOrDefault(ILanguageContributions::providesDefinition, "definition");
        references = findFirstOrDefault(ILanguageContributions::providesReferences, "references");
        implementation = findFirstOrDefault(ILanguageContributions::providesImplementation, "implementation");
        codeAction = findFirstOrDefault(ILanguageContributions::providesCodeAction, "codeAction");
        rename = findFirstOrDefault(ILanguageContributions::providesRename, "rename");
        didRenameFiles = findFirstOrDefault(ILanguageContributions::providesDidRenameFiles, "didRenameFiles");
        selectionRange = findFirstOrDefault(ILanguageContributions::providesSelectionRange, "selectionRange");
        callHierarchy = findFirstOrDefault(ILanguageContributions::providesCallHierarchy, "callHierarchy");
        completion = findFirstOrDefault(ILanguageContributions::providesCompletion, "completion");

        providesAnalysis = anyTrue(ILanguageContributions::providesAnalysis);
        providesBuild = anyTrue(ILanguageContributions::providesBuild);
        providesDocumentSymbol = anyTrue(ILanguageContributions::providesDocumentSymbol);
        providesCodeLens = anyTrue(ILanguageContributions::providesCodeLens);
        providesInlayHint = anyTrue(ILanguageContributions::providesInlayHint);
        providesExecution = anyTrue(ILanguageContributions::providesExecution);
        providesHover = anyTrue(ILanguageContributions::providesHover);
        providesDefinition = anyTrue(ILanguageContributions::providesDefinition);
        providesReferences = anyTrue(ILanguageContributions::providesReferences);
        providesImplementation = anyTrue(ILanguageContributions::providesImplementation);
        providesCodeAction = anyTrue(ILanguageContributions::providesCodeAction);
        providesRename = anyTrue(ILanguageContributions::providesRename);
        providesDidRenameFiles = anyTrue(ILanguageContributions::providesDidRenameFiles);
        providesSelectionRange = anyTrue(ILanguageContributions::providesSelectionRange);
        providesCallHierarchy = anyTrue(ILanguageContributions::providesCallHierarchy);
        providesCompletion = anyTrue(ILanguageContributions::providesCompletion);

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

    private CompletableFuture<ILanguageContributions> findFirstOrDefault(Function<ILanguageContributions, CompletableFuture<Boolean>> filter, String contribName) {
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
            logger.info("No contribution for {}; defaulting to empty implementation", contribName);
            return firstOrFail();
        }, exec);
    }

    private CompletableFuture<Boolean> anyTrue(Function<ILanguageContributions, CompletableFuture<Boolean>> predicate) {
        return anyTrue(predicate, false, Boolean::logicalOr);
    }

    private <T> CompletableFuture<T> anyTrue(
            Function<ILanguageContributions, CompletableFuture<T>> predicate,
            T falsy, BinaryOperator<T> or) {

        var result = CompletableFutureUtils.completedFuture(falsy, exec);
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
        return InterruptibleFuture.flatten(target.thenApply(call), exec);
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
    public InterruptibleFuture<IValue> execution(String command) {
        return flatten(execution, c -> c.execution(command));
    }

    @Override
    public CompletableFuture<IList> parseCodeActions(String command) {
        return execution.thenApply(c -> c.parseCodeActions(command)).thenCompose(Function.identity());
    }

    @Override
    public CompletableFuture<IConstructor> parseCallHierarchyData(String data) {
        return callHierarchy.thenApply(c -> c.parseCallHierarchyData(data)).thenCompose(Function.identity());
    }

    @Override
    public InterruptibleFuture<IList> inlayHint(ITree input) {
        return flatten(inlayHint, c -> c.inlayHint(input));
    }

    @Override
    public InterruptibleFuture<ISourceLocation> prepareRename(IList focus) {
        return flatten(rename, c -> c.prepareRename(focus));
    }

    @Override
    public InterruptibleFuture<ITuple> rename(IList focus, String name) {
        return flatten(rename, c -> c.rename(focus, name));
    }

    @Override
    public InterruptibleFuture<ITuple> didRenameFiles(IList oldToNew) {
        return flatten(didRenameFiles, c -> c.didRenameFiles(oldToNew));
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
    public InterruptibleFuture<IList> selectionRange(IList focus) {
        return flatten(selectionRange, c -> c.selectionRange(focus));
    }

    @Override
    public InterruptibleFuture<IList> completion(IList focus, IInteger cursorOffset, IConstructor trigger) {
        return flatten(completion, c -> c.completion(focus, cursorOffset, trigger));
    }

    @Override
    public CompletableFuture<IList> completionTriggerCharacters() {
        return completion.thenCompose(ILanguageContributions::completionTriggerCharacters);
    }

    @Override
    public InterruptibleFuture<IList> prepareCallHierarchy(IList focus) {
        return flatten(callHierarchy, c -> c.prepareCallHierarchy(focus));
    }

    @Override
    public InterruptibleFuture<IList> incomingOutgoingCalls(IConstructor hierarchyItem, IConstructor direction) {
        return flatten(callHierarchy, c -> c.incomingOutgoingCalls(hierarchyItem, direction));
    }

    @Override
    public CompletableFuture<Boolean> providesCodeAction() {
        return providesCodeAction;
    }

    @Override
    public CompletableFuture<Boolean> providesHover() {
        return providesHover;
    }

    @Override
    public CompletableFuture<Boolean> providesDefinition() {
        return providesDefinition;
    }

    @Override
    public CompletableFuture<Boolean> providesReferences() {
        return providesReferences;
    }

    @Override
    public CompletableFuture<Boolean> providesImplementation() {
        return providesImplementation;
    }

    @Override
    public CompletableFuture<Boolean> providesDocumentSymbol() {
        return providesDocumentSymbol;
    }

    @Override
    public CompletableFuture<Boolean> providesAnalysis() {
        return providesAnalysis;
    }

    @Override
    public CompletableFuture<Boolean> providesBuild() {
        return providesBuild;
    }

    @Override
    public CompletableFuture<Boolean> providesCodeLens() {
        return providesCodeLens;
    }

    @Override
    public CompletableFuture<Boolean> providesExecution() {
        return providesExecution;
    }

    @Override
    public CompletableFuture<Boolean> providesInlayHint() {
        return providesInlayHint;
    }

    @Override
    public CompletableFuture<Boolean> providesSelectionRange() {
        return providesSelectionRange;
    }

    @Override
    public CompletableFuture<Boolean> providesRename() {
        return providesRename;
    }

    @Override
    public CompletableFuture<Boolean> providesDidRenameFiles() {
        return providesDidRenameFiles;
    }

    public CompletableFuture<Boolean> providesCallHierarchy() {
        return providesCallHierarchy;
    }

    @Override
    public CompletableFuture<Boolean> providesCompletion() {
        return providesCompletion;
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
