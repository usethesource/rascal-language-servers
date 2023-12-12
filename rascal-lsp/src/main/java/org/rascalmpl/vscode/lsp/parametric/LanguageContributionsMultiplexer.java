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

public class LanguageContributionsMultiplexer implements ILanguageContributions {

    private final ExecutorService ownExecuter;
    private final String name;

    private static final <T> CompletableFuture<T> failedInitialization() {
        return CompletableFuture.failedFuture(new RuntimeException("No contributions registered"));
    }

    private volatile @MonotonicNonNull ILanguageContributions parser = null;
    private volatile CompletableFuture<ILanguageContributions> outline = failedInitialization();
    private volatile CompletableFuture<ILanguageContributions> summarizer = failedInitialization();
    private volatile CompletableFuture<ILanguageContributions> lenses = failedInitialization();
    private volatile CompletableFuture<ILanguageContributions> executor = failedInitialization();
    private volatile CompletableFuture<ILanguageContributions> inlayHinter = failedInitialization();
    private volatile CompletableFuture<ILanguageContributions> definer = failedInitialization();
    private volatile CompletableFuture<ILanguageContributions> documenter = failedInitialization();
    private volatile CompletableFuture<ILanguageContributions> referrer = failedInitialization();
    private volatile CompletableFuture<ILanguageContributions> implementer = failedInitialization();

    private volatile CompletableFuture<Boolean> hasDedicatedDocumentation = failedInitialization();
    private volatile CompletableFuture<Boolean> hasDedicatedDefines = failedInitialization();
    private volatile CompletableFuture<Boolean> hasDedicatedReferences = failedInitialization();
    private volatile CompletableFuture<Boolean> hasDedicatedImplementations = failedInitialization();

    private volatile CompletableFuture<Boolean> hasOutline = failedInitialization();
    private volatile CompletableFuture<Boolean> hasSummarize = failedInitialization();
    private volatile CompletableFuture<Boolean> hasLenses = failedInitialization();
    private volatile CompletableFuture<Boolean> hasExecuteCommand = failedInitialization();
    private volatile CompletableFuture<Boolean> hasInlayHint = failedInitialization();

    private volatile CompletableFuture<Boolean> askSummaryForDocumentation = failedInitialization();
    private volatile CompletableFuture<Boolean> askSummaryForDefinitions = failedInitialization();
    private volatile CompletableFuture<Boolean> askSummaryForReferences = failedInitialization();
    private volatile CompletableFuture<Boolean> askSummaryForImplementations = failedInitialization();

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
        outline = findFirstOrDefault(ILanguageContributions::hasOutline);
        summarizer = findFirstOrDefault(ILanguageContributions::hasSummarize);
        lenses = findFirstOrDefault(ILanguageContributions::hasLenses);
        executor = findFirstOrDefault(ILanguageContributions::hasExecuteCommand);
        inlayHinter = findFirstOrDefault(ILanguageContributions::hasInlayHint);
        definer = findFirstOrDefault(ILanguageContributions::hasDedicatedDefines);
        documenter = findFirstOrDefault(ILanguageContributions::hasDedicatedDocumentation);
        referrer = findFirstOrDefault(ILanguageContributions::hasDedicatedReferences);
        implementer = findFirstOrDefault(ILanguageContributions::hasDedicatedReferences);

        hasDedicatedDocumentation = anyTrue(ILanguageContributions::hasDedicatedDocumentation);
        hasDedicatedDocumentation = anyTrue(ILanguageContributions::hasDedicatedDocumentation);
        hasDedicatedDefines = anyTrue(ILanguageContributions::hasDedicatedDefines);
        hasDedicatedReferences = anyTrue(ILanguageContributions::hasDedicatedReferences);
        hasDedicatedImplementations = anyTrue(ILanguageContributions::hasDedicatedImplementations);

        hasOutline = anyTrue(ILanguageContributions::hasOutline);
        hasSummarize = anyTrue(ILanguageContributions::hasSummarize);
        hasLenses = anyTrue(ILanguageContributions::hasLenses);
        hasExecuteCommand = anyTrue(ILanguageContributions::hasExecuteCommand);
        hasInlayHint = anyTrue(ILanguageContributions::hasInlayHint);

        askSummaryForDocumentation = anyTrue(ILanguageContributions::askSummaryForDocumentation);
        askSummaryForDefinitions = anyTrue(ILanguageContributions::askSummaryForDefinitions);
        askSummaryForReferences = anyTrue(ILanguageContributions::askSummaryForReferences);
        askSummaryForImplementations = anyTrue(ILanguageContributions::askSummaryForImplementations);
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

    private static boolean swallowExceptions(Throwable ex) {
        return false;
    }

    private CompletableFuture<Boolean> anyTrue(Function<ILanguageContributions, CompletableFuture<Boolean>> predicate) {
        var result = CompletableFuture.completedFuture(false);
        // no short-circuiting, but it's not problem, it's only triggered at the beginning of a registry
        // pretty soon the future will be completed.
        for (var c: contributions) {
            var checkCurrent = predicate.apply(c.contrib)
                .exceptionally(LanguageContributionsMultiplexer::swallowExceptions);
            result = result.thenCombine(checkCurrent, Boolean::logicalOr);
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
        return flatten(outline, c -> c.outline(input));
    }

    @Override
    public InterruptibleFuture<IConstructor> summarize(ISourceLocation loc, ITree input) {
        return flatten(summarizer, c -> c.summarize(loc, input));
    }

    @Override
    public InterruptibleFuture<ISet> lenses(ITree input) {
        return flatten(lenses, c -> c.lenses(input));
    }

    @Override
    public InterruptibleFuture<@Nullable IValue> executeCommand(String command) {
        return flatten(executor, c -> c.executeCommand(command));
    }

    @Override
    public InterruptibleFuture<IList> inlayHint(@Nullable ITree input) {
        return flatten(inlayHinter, c -> c.inlayHint(input));
    }

    @Override
    public InterruptibleFuture<ISet> documentation(ISourceLocation loc, ITree input, ITree cursor) {
        return flatten(documenter, c -> c.documentation(loc, input, cursor));
    }

    @Override
    public InterruptibleFuture<ISet> defines(ISourceLocation loc, ITree input, ITree cursor) {
        return flatten(definer, c -> c.defines(loc, input, cursor));
    }

    @Override
    public InterruptibleFuture<ISet> references(ISourceLocation loc, ITree input, ITree cursor) {
        return flatten(referrer, c -> c.references(loc, input, cursor));
    }

    @Override
    public InterruptibleFuture<ISet> implementations(ISourceLocation loc, ITree input, ITree cursor) {
        return flatten(implementer, c -> c.implementations(loc, input, cursor));
    }


    @Override
    public CompletableFuture<Boolean> hasDedicatedDocumentation() {
        return hasDedicatedDocumentation;
    }

    @Override
    public CompletableFuture<Boolean> hasDedicatedDefines() {
        return hasDedicatedDefines;
    }

    @Override
    public CompletableFuture<Boolean> hasDedicatedReferences() {
        return hasDedicatedReferences;
    }

    @Override
    public CompletableFuture<Boolean> hasDedicatedImplementations() {
        return hasDedicatedImplementations;
    }

    @Override
    public CompletableFuture<Boolean> askSummaryForDocumentation() {
        return askSummaryForDocumentation;
    }

    @Override
    public CompletableFuture<Boolean> askSummaryForDefinitions() {
        return askSummaryForDefinitions;
    }

    @Override
    public CompletableFuture<Boolean> askSummaryForReferences() {
        return askSummaryForReferences;
    }

    @Override
    public CompletableFuture<Boolean> askSummaryForImplementations() {
        return askSummaryForImplementations;
    }

    @Override
    public CompletableFuture<Boolean> hasOutline() {
        return hasOutline;
    }

    @Override
    public CompletableFuture<Boolean> hasSummarize() {
        return hasSummarize;
    }

    @Override
    public CompletableFuture<Boolean> hasLenses() {
        return hasLenses;
    }

    @Override
    public CompletableFuture<Boolean> hasExecuteCommand() {
        return hasExecuteCommand;
    }

    @Override
    public CompletableFuture<Boolean> hasInlayHint() {
        return hasInlayHint;
    }

}
