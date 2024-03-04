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
import org.checkerframework.checker.nullness.qual.Nullable;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.vscode.lsp.util.concurrent.InterruptibleFuture;
import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValue;

public interface ILanguageContributions {
    public String getName();

    public CompletableFuture<ITree> parseSourceFile(ISourceLocation loc, String input);
    public InterruptibleFuture<IList> outline(ITree input);
    public InterruptibleFuture<IConstructor> analyze(ISourceLocation loc, ITree input);
    public InterruptibleFuture<IConstructor> build(ISourceLocation loc, ITree input);
    public InterruptibleFuture<ISet> lenses(ITree input);
    public InterruptibleFuture<@Nullable IValue> executeCommand(String command);
    public InterruptibleFuture<IList> inlayHint(@Nullable ITree input);
    public InterruptibleFuture<ISet> documentation(ISourceLocation loc, ITree input, ITree cursor);
    public InterruptibleFuture<ISet> definitions(ISourceLocation loc, ITree input, ITree cursor);
    public InterruptibleFuture<ISet> references(ISourceLocation loc, ITree input, ITree cursor);
    public InterruptibleFuture<ISet> implementations(ISourceLocation loc, ITree input, ITree cursor);

    public CompletableFuture<Boolean> hasAnalyzer();
    public CompletableFuture<Boolean> hasBuilder();
    public CompletableFuture<Boolean> hasOutliner();
    public CompletableFuture<Boolean> hasLensDetector();
    public CompletableFuture<Boolean> hasInlayHinter();
    public CompletableFuture<Boolean> hasCommandExecutor();
    public CompletableFuture<Boolean> hasDocumenter();
    public CompletableFuture<Boolean> hasDefiner();
    public CompletableFuture<Boolean> hasReferrer();
    public CompletableFuture<Boolean> hasImplementer();

    public CompletableFuture<SummaryConfig> getAnalyzerConfig();
    public CompletableFuture<SummaryConfig> getBuilderConfig();
    public CompletableFuture<SummaryConfig> getSingleShooterConfig();

    public static class SummaryConfig {
        public final boolean providesDocumentation;
        public final boolean providesDefinitions;
        public final boolean providesReferences;
        public final boolean providesImplementations;

        public SummaryConfig(
                boolean providesDocumentation,
                boolean providesDefinitions,
                boolean providesReferences,
                boolean providesImplementations) {

            this.providesDocumentation = providesDocumentation;
            this.providesDefinitions = providesDefinitions;
            this.providesReferences = providesReferences;
            this.providesImplementations = providesImplementations;
        }

        public static final SummaryConfig FALSY = new SummaryConfig(false, false, false, false);

        public static SummaryConfig or(SummaryConfig a, SummaryConfig b) {
            return new SummaryConfig(
                a.providesDocumentation || b.providesDocumentation,
                a.providesDefinitions || b.providesDefinitions,
                a.providesReferences || b.providesReferences,
                a.providesImplementations || b.providesImplementations);
        }
    }


}
