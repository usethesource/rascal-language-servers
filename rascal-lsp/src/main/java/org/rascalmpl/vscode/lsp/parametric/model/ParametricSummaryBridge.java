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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.vscode.lsp.parametric.ILanguageContributions;
import org.rascalmpl.vscode.lsp.parametric.ILanguageContributions.Config;
import org.rascalmpl.vscode.lsp.util.Versioned;
import org.rascalmpl.vscode.lsp.util.concurrent.InterruptibleFuture;
import org.rascalmpl.vscode.lsp.util.locations.ColumnMaps;
import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.type.Type;
import io.usethesource.vallang.type.TypeFactory;
import io.usethesource.vallang.type.TypeStore;

@SuppressWarnings({"java:S1192"})
public class ParametricSummaryBridge {
    private static final Logger logger = LogManager.getLogger(ParametricSummaryBridge.class);

    private final Executor exec;
    private final ColumnMaps columns;
    private final ILanguageContributions contrib;

    private final ISourceLocation file;
    private final BiFunction<ISourceLocation, ITree, InterruptibleFuture<IConstructor>> calculator;

    @SuppressWarnings("java:S3077") // Reads/writes happen sequentially
    private volatile CompletableFuture<SummarizerSummaryFactory> summaryFactory;
    private final Supplier<CompletableFuture<Config>> summarizerConfig;

    public ParametricSummaryBridge(ISourceLocation file,
            Executor exec, ColumnMaps columns, ILanguageContributions contrib,
            BiFunction<ISourceLocation, ITree, InterruptibleFuture<IConstructor>> calculator,
            Supplier<CompletableFuture<Config>> summarizerConfig) {

        this.file = file;
        this.exec = exec;
        this.columns = columns;
        this.contrib = contrib;
        this.calculator = calculator;
        this.summarizerConfig = summarizerConfig;
        reloadContributions();
    }

    public void reloadContributions() {
        summaryFactory = summarizerConfig.get().thenApply(config ->
            new SummarizerSummaryFactory(config, exec, columns, contrib));
    }

    public CompletableFuture<Versioned<ParametricSummary>> calculateSummary(CompletableFuture<Versioned<ITree>> tree) {
        logger.trace("Requesting Summary calculation for: {}", file);
        var version = tree.thenApply(Versioned::version);
        var summary = summaryFactory.thenApply(f -> f.create(calculate(tree)));
        return version.thenCombine(summary, Versioned::new);
    }

    private InterruptibleFuture<IConstructor> calculate(CompletableFuture<Versioned<ITree>> tree) {
        return InterruptibleFuture.flatten(
            tree.thenApplyAsync(t -> calculator.apply(file, t.get()), exec),
            exec);
    }

    private static final Type summaryCons;

    static {
        TypeFactory TF = TypeFactory.getInstance();
        TypeStore TS = new TypeStore();
        summaryCons = TF.constructor(TS, TF.abstractDataType(TS, "Summary"), "summary", TF.sourceLocationType(), "src");
    }

    public static IConstructor emptySummary(ISourceLocation src) {
        return IRascalValueFactory.getInstance().constructor(summaryCons, src);
    }
}
