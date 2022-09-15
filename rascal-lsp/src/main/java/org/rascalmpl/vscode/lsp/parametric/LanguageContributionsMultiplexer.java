/*
 * Copyright (c) 2018-2022, NWO-I CWI and Swat.engineering
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
    private final String extension;

    public LanguageContributionsMultiplexer(String name, String extension, ExecutorService ownService) {
        this.name = name;
        this.extension = extension;
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
    }

    /**
     * @returns false if the multiplexer is empty, and therefore should not be used anymore
     */
    public boolean removeContributor(String contribKey) {
        contributions.removeIf(e -> e.key.equals(contribKey));
        return !contributions.isEmpty();
    }




    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getExtension() {
        return extension;
    }

    private ILanguageContributions firstOrFail() {
        var it = contributions.iterator();
        if (!it.hasNext()) {
            throw new RuntimeException("No more language contributions registered for " + name);
        }
        return it.next().contrib;
    }

    @Override
    public CompletableFuture<ITree> parseSourceFile(ISourceLocation loc, String input) {
        return firstOrFail().parseSourceFile(loc, input);
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

    private <T> InterruptibleFuture<T> deferFirstContrib(Function<ILanguageContributions, CompletableFuture<Boolean>> filter, Function<ILanguageContributions, InterruptibleFuture<T>> call) {
        return InterruptibleFuture.flatten(findFirstOrDefault(filter).thenApply(call), ownExecuter);
    }

    @Override
    public InterruptibleFuture<IList> outline(ITree input) {
        return deferFirstContrib(ILanguageContributions::hasOutline, c -> c.outline(input));
    }

    @Override
    public InterruptibleFuture<IConstructor> summarize(ISourceLocation loc, ITree input) {
        return deferFirstContrib(ILanguageContributions::hasSummarize, c -> c.summarize(loc, input));
    }

    @Override
    public InterruptibleFuture<ISet> lenses(ITree input) {
        return deferFirstContrib(ILanguageContributions::hasLenses, c -> c.lenses(input));
    }

    @Override
    public InterruptibleFuture<@Nullable IValue> executeCommand(String command) {
        return deferFirstContrib(ILanguageContributions::hasExecuteCommand, c -> c.executeCommand(command));
    }

    @Override
    public InterruptibleFuture<IList> inlayHint(@Nullable ITree input) {
        return deferFirstContrib(ILanguageContributions::hasInlayHint, c -> c.inlayHint(input));
    }

    @Override
    public InterruptibleFuture<ISet> documentation(ISourceLocation loc, ITree input, ITree cursor) {
        return deferFirstContrib(ILanguageContributions::hasDedicatedDocumentation, c -> c.documentation(loc, input, cursor));
    }

    @Override
    public InterruptibleFuture<ISet> defines(ISourceLocation loc, ITree input, ITree cursor) {
        return deferFirstContrib(ILanguageContributions::hasDedicatedDefines, c -> c.defines(loc, input, cursor));
    }

    @Override
    public InterruptibleFuture<ISet> references(ISourceLocation loc, ITree input, ITree cursor) {
        return deferFirstContrib(ILanguageContributions::hasDedicatedReferences, c -> c.references(loc, input, cursor));
    }

    @Override
    public InterruptibleFuture<ISet> implementations(ISourceLocation loc, ITree input, ITree cursor) {
        return deferFirstContrib(ILanguageContributions::hasDedicatedReferences, c -> c.implementations(loc, input, cursor));
    }



    private CompletableFuture<Boolean> anyTrue(Function<ILanguageContributions, CompletableFuture<Boolean>> predicate) {
        return CompletableFuture.supplyAsync(() -> {
            // note, it would be possible to construct a fancy variant of
            // anyOf, that short-circuits this whole lookup
            // but that would make the code less readable, and in most cases
            // this returns very quickly with a result, as the "has" functions
            // are all completed when the evaluator has loaded
            for (var c: contributions) {
                try {
                    if (predicate.apply(c.contrib).get().booleanValue()) {
                        return true;
                    }
                } catch(InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception ignored) {
                    continue;
                }
            }
            return false;
        }, ownExecuter);
    }

    @Override
    public CompletableFuture<Boolean> hasDedicatedDocumentation() {
        return anyTrue(ILanguageContributions::hasDedicatedDocumentation);
    }

    @Override
    public CompletableFuture<Boolean> hasDedicatedDefines() {
        return anyTrue(ILanguageContributions::hasDedicatedDefines);
    }

    @Override
    public CompletableFuture<Boolean> hasDedicatedReferences() {
        return anyTrue(ILanguageContributions::hasDedicatedReferences);
    }

    @Override
    public CompletableFuture<Boolean> hasDedicatedImplementations() {
        return anyTrue(ILanguageContributions::hasDedicatedImplementations);
    }

    @Override
    public CompletableFuture<Boolean> askSummaryForDocumentation() {
        return anyTrue(ILanguageContributions::askSummaryForDocumentation);
    }

    @Override
    public CompletableFuture<Boolean> askSummaryForDefinitions() {
        return anyTrue(ILanguageContributions::askSummaryForDefinitions);
    }

    @Override
    public CompletableFuture<Boolean> askSummaryForReferences() {
        return anyTrue(ILanguageContributions::askSummaryForReferences);
    }

    @Override
    public CompletableFuture<Boolean> askSummaryForImplementations() {
        return anyTrue(ILanguageContributions::askSummaryForImplementations);
    }

    @Override
    public CompletableFuture<Boolean> hasOutline() {
        return anyTrue(ILanguageContributions::hasOutline);
    }

    @Override
    public CompletableFuture<Boolean> hasSummarize() {
        return anyTrue(ILanguageContributions::hasSummarize);
    }

    @Override
    public CompletableFuture<Boolean> hasLenses() {
        return anyTrue(ILanguageContributions::hasLenses);
    }

    @Override
    public CompletableFuture<Boolean> hasExecuteCommand() {
        return anyTrue(ILanguageContributions::hasExecuteCommand);
    }

    @Override
    public CompletableFuture<Boolean> hasInlayHint() {
        return anyTrue(ILanguageContributions::hasInlayHint);
    }

}
