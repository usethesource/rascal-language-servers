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
import java.util.function.BiFunction;
import java.util.function.Function;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.vscode.lsp.util.concurrent.InterruptibleFuture;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.ITuple;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.type.Type;
import io.usethesource.vallang.type.TypeFactory;
import io.usethesource.vallang.type.TypeStore;

public interface ILanguageContributions {
    public String getName();

    public CompletableFuture<ITree> parsing(ISourceLocation loc, String input);
    public InterruptibleFuture<IConstructor> analysis(ISourceLocation loc, ITree input);
    public InterruptibleFuture<IConstructor> build(ISourceLocation loc, ITree input);
    public InterruptibleFuture<IList> documentSymbol(ITree input);
    public InterruptibleFuture<IList> codeLens(ITree input);
    public InterruptibleFuture<IList> inlayHint(@Nullable ITree input);
    public InterruptibleFuture<@Nullable IValue> execution(String command);
    public InterruptibleFuture<ISet> hover(IList focus);
    public InterruptibleFuture<ISet> definition(IList focus);
    public InterruptibleFuture<ISet> references(IList focus);
    public InterruptibleFuture<ISet> implementation(IList focus);
    public InterruptibleFuture<IList> codeAction(IList focus);
    public InterruptibleFuture<IList> selectionRange(IList focus);
    public InterruptibleFuture<IList> formatting(ITree input, ISourceLocation loc, IConstructor formattingOptions);

    public InterruptibleFuture<ISourceLocation> prepareRename(IList focus);
    public InterruptibleFuture<ITuple> rename(IList focus, String name);
    public InterruptibleFuture<ITuple> didRenameFiles(IList fileRenames);

    public CompletableFuture<IList> parseCodeActions(String command);

    public CompletableFuture<Boolean> hasAnalysis();
    public CompletableFuture<Boolean> hasBuild();
    public CompletableFuture<Boolean> hasDocumentSymbol();
    public CompletableFuture<Boolean> hasCodeLens();
    public CompletableFuture<Boolean> hasInlayHint();
    public CompletableFuture<Boolean> hasRename();
    public CompletableFuture<Boolean> hasExecution();
    public CompletableFuture<Boolean> hasHover();
    public CompletableFuture<Boolean> hasDefinition();
    public CompletableFuture<Boolean> hasReferences();
    public CompletableFuture<Boolean> hasImplementation();
    public CompletableFuture<Boolean> hasCodeAction();
    public CompletableFuture<Boolean> hasDidRenameFiles();
    public CompletableFuture<Boolean> hasSelectionRange();
    public CompletableFuture<Boolean> hasFormatting();

    public CompletableFuture<Boolean> specialCaseHighlighting();

    public CompletableFuture<SummaryConfig> getAnalyzerSummaryConfig();
    public CompletableFuture<SummaryConfig> getBuilderSummaryConfig();
    public CompletableFuture<SummaryConfig> getOndemandSummaryConfig();

    public static class SummaryConfig {
        public final boolean providesHovers;
        public final boolean providesDefinitions;
        public final boolean providesReferences;
        public final boolean providesImplementations;

        public SummaryConfig(
                boolean providesHovers,
                boolean providesDefinitions,
                boolean providesReferences,
                boolean providesImplementations) {

            this.providesHovers = providesHovers;
            this.providesDefinitions = providesDefinitions;
            this.providesReferences = providesReferences;
            this.providesImplementations = providesImplementations;
        }

        public static final SummaryConfig FALSY = new SummaryConfig(false, false, false, false);

        public static SummaryConfig or(SummaryConfig a, SummaryConfig b) {
            return new SummaryConfig(
                a.providesHovers || b.providesHovers,
                a.providesDefinitions || b.providesDefinitions,
                a.providesReferences || b.providesReferences,
                a.providesImplementations || b.providesImplementations);
        }
    }

    @FunctionalInterface // Type alias to conveniently pass methods `analyze`and `build` as parameters
    public static interface ScheduledCalculator extends BiFunction<ISourceLocation, ITree, InterruptibleFuture<IConstructor>> {}

    @FunctionalInterface
    /**
     * To conveniently pass methods `documentation`, `definitions`,
     * `references`, and `implementations` as parameter.
     */
    public static interface OnDemandFocusToSetCalculator extends Function<IList, InterruptibleFuture<ISet>> { }

    public void cancelProgress(String progressId);
}

/*package*/ class EmptySummary {
    private EmptySummary() {}

    private static final Type summaryCons;

    static {
        TypeFactory typeFactory = TypeFactory.getInstance();
        TypeStore typeStore = new TypeStore();
        summaryCons = typeFactory.constructor(
            typeStore,
            typeFactory.abstractDataType(typeStore, "Summary"),
            "summary",
            typeFactory.sourceLocationType(),
            "src");
    }

    public static IConstructor newInstance(ISourceLocation src) {
        return IRascalValueFactory.getInstance().constructor(summaryCons, src);
    }
}
