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

/**
 * An implementation of {@link ILanguageContributions} that has no contributions.
 * It is intended to be used as a placeholder for files which do not have any contributions registered.
 */
public class NoContributions implements ILanguageContributions {

    private static final Logger logger = LogManager.getLogger(NoContributions.class);
    private static final CompletableFuture<Boolean> FALSE = CompletableFuture.completedFuture(false);

    private String name;

    public class NoContributionException extends NotImplementedException {
        private NoContributionException(String message) {
            super(message);
        }
    }

    public NoContributions(String name) {
        this.name = name;
    }

    private <T> InterruptibleFuture<T> failInterruptible(String contribution) {
        return new InterruptibleFuture<>(fail(contribution), () -> {});
    }

    private <T> CompletableFuture<T> fail(String contribution) {
        return CompletableFuture.failedFuture(new NoContributionException("Ignoring missing contribution " + contribution));
    }

    @Override
    public String getName() {
        return String.format("Empty contributions for '%s'", name);
    }

    @Override
    public CompletableFuture<ITree> parsing(ISourceLocation loc, String input) {
        return fail("parsing");
    }

    @Override
    public InterruptibleFuture<IConstructor> analysis(ISourceLocation loc, ITree input) {
        return failInterruptible("analysis");
    }

    @Override
    public InterruptibleFuture<IConstructor> build(ISourceLocation loc, ITree input) {
        return failInterruptible("build");
    }

    @Override
    public InterruptibleFuture<IList> documentSymbol(ITree input) {
        return failInterruptible("documentSymbol");
    }

    @Override
    public InterruptibleFuture<IList> codeLens(ITree input) {
        return failInterruptible("codeLens");
    }

    @Override
    public InterruptibleFuture<IList> inlayHint(ITree input) {
        return failInterruptible("inlayHint");
    }

    @Override
    public InterruptibleFuture<IValue> execution(String command) {
        return failInterruptible("execution");
    }

    @Override
    public InterruptibleFuture<ISet> hover(IList focus) {
        return failInterruptible("hover");
    }

    @Override
    public InterruptibleFuture<ISet> definition(IList focus) {
        return failInterruptible("definition");
    }

    @Override
    public InterruptibleFuture<ISet> references(IList focus) {
        return failInterruptible("references");
    }

    @Override
    public InterruptibleFuture<ISet> implementation(IList focus) {
        return failInterruptible("implementation");
    }

    @Override
    public InterruptibleFuture<IList> codeAction(IList focus) {
        return failInterruptible("codeLens");
    }

    @Override
    public InterruptibleFuture<IList> selectionRange(IList focus) {
        return failInterruptible("selectionRange");
    }

    @Override
    public InterruptibleFuture<ISourceLocation> prepareRename(IList focus) {
        return failInterruptible("prepareRename");
    }

    @Override
    public InterruptibleFuture<ITuple> rename(IList focus, String name) {
        return failInterruptible("rename");
    }

    @Override
    public InterruptibleFuture<ITuple> didRenameFiles(IList fileRenames) {
        return failInterruptible("didRenameFiles");
    }

    @Override
    public CompletableFuture<IList> parseCodeActions(String command) {
        return fail("parseCodeActions");
    }

    @Override
    public CompletableFuture<Boolean> hasAnalysis() {
        throw new NotImplementedException("No `has...` on empty contributions.");
    }

    @Override
    public CompletableFuture<Boolean> hasBuild() {
        throw new NotImplementedException("No `has...` on empty contributions.");
    }

    @Override
    public CompletableFuture<Boolean> hasDocumentSymbol() {
        throw new NotImplementedException("No `has...` on empty contributions.");
    }

    @Override
    public CompletableFuture<Boolean> hasCodeLens() {
        throw new NotImplementedException("No `has...` on empty contributions.");
    }

    @Override
    public CompletableFuture<Boolean> hasInlayHint() {
        throw new NotImplementedException("No `has...` on empty contributions.");
    }

    @Override
    public CompletableFuture<Boolean> hasRename() {
        throw new NotImplementedException("No `has...` on empty contributions.");
    }

    @Override
    public CompletableFuture<Boolean> hasExecution() {
        throw new NotImplementedException("No `has...` on empty contributions.");
    }

    @Override
    public CompletableFuture<Boolean> hasHover() {
        throw new NotImplementedException("No `has...` on empty contributions.");
    }

    @Override
    public CompletableFuture<Boolean> hasDefinition() {
        throw new NotImplementedException("No `has...` on empty contributions.");
    }

    @Override
    public CompletableFuture<Boolean> hasReferences() {
        throw new NotImplementedException("No `has...` on empty contributions.");
    }

    @Override
    public CompletableFuture<Boolean> hasImplementation() {
        throw new NotImplementedException("No `has...` on empty contributions.");
    }

    @Override
    public CompletableFuture<Boolean> hasCodeAction() {
        throw new NotImplementedException("No `has...` on empty contributions.");
    }

    @Override
    public CompletableFuture<Boolean> hasDidRenameFiles() {
        throw new NotImplementedException("No `has...` on empty contributions.");
    }

    @Override
    public CompletableFuture<Boolean> hasSelectionRange() {
        throw new NotImplementedException("No `has...` on empty contributions.");
    }

    @Override
    public CompletableFuture<Boolean> specialCaseHighlighting() {
        return FALSE;
    }

    @Override
    public CompletableFuture<SummaryConfig> getAnalyzerSummaryConfig() {
        return fail("getAnalyzerSummaryConfig");
    }

    @Override
    public CompletableFuture<SummaryConfig> getBuilderSummaryConfig() {
        return fail("getBuilderSummaryConfig");
    }

    @Override
    public CompletableFuture<SummaryConfig> getOndemandSummaryConfig() {
        return fail("getOndemandSummaryConfig");
    }

    @Override
    public void cancelProgress(String progressId) {
        logger.trace("Cancelling progress {} not supported on dummy contributions.", progressId);
    }
}
