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

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.vscode.lsp.util.concurrent.InterruptibleFuture;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.ITuple;
import io.usethesource.vallang.IValue;

public class NoContributions implements ILanguageContributions {

    private static final Logger logger = LogManager.getLogger(NoContributions.class);

    private final Duration delay = Duration.ofSeconds(10);
    private final CompletableFuture<Boolean> falseFut = CompletableFuture.completedFuture(false);
    private Executor exec;

    public class NoContributionException extends NotImplementedException {
        private NoContributionException(String message) {
            super(message);
        }
    }

    public NoContributions(Executor exec) {
        this.exec = exec;
    }

    private <T extends IValue> CompletableFuture<T> delayed(T t) {
        var delayedExec = CompletableFuture.delayedExecutor(delay.toMillis(), TimeUnit.MILLISECONDS, exec);
        return CompletableFuture.supplyAsync(() -> t, delayedExec);
    }

    private <T> InterruptibleFuture<T> delayedInterruptibleFailure(String contribution) {
        return new InterruptibleFuture<>(delayedFailure(contribution), () -> {});
    }

    private <T> CompletableFuture<T> delayedFailure(String contribution) {
        var delayedExec = CompletableFuture.delayedExecutor(delay.toMillis(), TimeUnit.MILLISECONDS, exec);
        return CompletableFuture.supplyAsync(() -> { throw new NoContributionException("Ignoring missing contribution " + contribution); }, delayedExec);
    }

    @Override
    public String getName() {
        return "Silent contributions";
    }

    @Override
    public CompletableFuture<ITree> parsing(ISourceLocation loc, String input) {
        logger.debug("No contrib: parse");
        return delayedFailure("parsing");
    }

    @Override
    public InterruptibleFuture<IConstructor> analysis(ISourceLocation loc, ITree input) {
        return delayedInterruptibleFailure("analysis");
    }

    @Override
    public InterruptibleFuture<IConstructor> build(ISourceLocation loc, ITree input) {
        return delayedInterruptibleFailure("build");
    }

    @Override
    public InterruptibleFuture<IList> documentSymbol(ITree input) {
        return delayedInterruptibleFailure("documentSymbol");
    }

    @Override
    public InterruptibleFuture<IList> codeLens(ITree input) {
        return delayedInterruptibleFailure("codeLens");
    }

    @Override
    public InterruptibleFuture<IList> inlayHint(ITree input) {
        return delayedInterruptibleFailure("inlayHint");
    }

    @Override
    public InterruptibleFuture<IValue> execution(String command) {
        return delayedInterruptibleFailure("execution");
    }

    @Override
    public InterruptibleFuture<ISet> hover(IList focus) {
        return delayedInterruptibleFailure("hover");
    }

    @Override
    public InterruptibleFuture<ISet> definition(IList focus) {
        return delayedInterruptibleFailure("definition");
    }

    @Override
    public InterruptibleFuture<ISet> references(IList focus) {
        return delayedInterruptibleFailure("references");
    }

    @Override
    public InterruptibleFuture<ISet> implementation(IList focus) {
        return delayedInterruptibleFailure("implementation");
    }

    @Override
    public InterruptibleFuture<IList> codeAction(IList focus) {
        return delayedInterruptibleFailure("codeLens");
    }

    @Override
    public InterruptibleFuture<IList> selectionRange(IList focus) {
        return delayedInterruptibleFailure("selectionRange");
    }

    @Override
    public InterruptibleFuture<ISourceLocation> prepareRename(IList focus) {
        return delayedInterruptibleFailure("prepareRename");
    }

    @Override
    public InterruptibleFuture<ITuple> rename(IList focus, String name) {
        return delayedInterruptibleFailure("rename");
    }

    @Override
    public InterruptibleFuture<ITuple> didRenameFiles(IList fileRenames) {
        return delayedInterruptibleFailure("didRenameFiles");
    }

    @Override
    public CompletableFuture<IList> parseCodeActions(String command) {
        return delayedFailure("parseCodeActions");
    }

    @Override
    public CompletableFuture<Boolean> hasAnalysis() {
        return falseFut;
    }

    @Override
    public CompletableFuture<Boolean> hasBuild() {
        return falseFut;
    }

    @Override
    public CompletableFuture<Boolean> hasDocumentSymbol() {
        return falseFut;
    }

    @Override
    public CompletableFuture<Boolean> hasCodeLens() {
        return falseFut;
    }

    @Override
    public CompletableFuture<Boolean> hasInlayHint() {
        return falseFut;
    }

    @Override
    public CompletableFuture<Boolean> hasRename() {
        return falseFut;
    }

    @Override
    public CompletableFuture<Boolean> hasExecution() {
        return falseFut;
    }

    @Override
    public CompletableFuture<Boolean> hasHover() {
        return falseFut;
    }

    @Override
    public CompletableFuture<Boolean> hasDefinition() {
        return falseFut;
    }

    @Override
    public CompletableFuture<Boolean> hasReferences() {
        return falseFut;
    }

    @Override
    public CompletableFuture<Boolean> hasImplementation() {
        return falseFut;
    }

    @Override
    public CompletableFuture<Boolean> hasCodeAction() {
        return falseFut;
    }

    @Override
    public CompletableFuture<Boolean> hasDidRenameFiles() {
        return falseFut;
    }

    @Override
    public CompletableFuture<Boolean> hasSelectionRange() {
        return falseFut;
    }

    @Override
    public CompletableFuture<Boolean> specialCaseHighlighting() {
        return falseFut;
    }

    @Override
    public CompletableFuture<SummaryConfig> getAnalyzerSummaryConfig() {
        return delayedFailure("getAnalyzerSummaryConfig");
    }

    @Override
    public CompletableFuture<SummaryConfig> getBuilderSummaryConfig() {
        return delayedFailure("getBuilderSummaryConfig");
    }

    @Override
    public CompletableFuture<SummaryConfig> getOndemandSummaryConfig() {
        return delayedFailure("getOndemandSummaryConfig");
    }

    @Override
    public void cancelProgress(String progressId) {
        logger.trace("Cancelling progress " + progressId + " not supported on dummy contributions.");
    }
}
