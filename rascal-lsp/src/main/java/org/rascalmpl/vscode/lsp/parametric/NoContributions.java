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
import java.util.concurrent.Executor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.vscode.lsp.util.concurrent.CompletableFutureUtils;
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

    private String name;
    private final CompletableFuture<Boolean> falsy;

    public class NoContributionException extends RuntimeException {
        private NoContributionException(String message) {
            super("Missing contribution: " + message);
        }
    }

    public NoContributions(String name, Executor exec) {
        this.name = name;
        this.falsy = CompletableFutureUtils.completedFuture(false, exec);
    }

    @Override
    public String getName() {
        return String.format("Empty contributions for '%s'", name);
    }

    @Override
    public CompletableFuture<ITree> parsing(ISourceLocation loc, String input) {
        throw new NoContributionException("parsing");
    }

    @Override
    public InterruptibleFuture<IConstructor> analysis(ISourceLocation loc, ITree input) {
        throw new NoContributionException("analysis");
    }

    @Override
    public InterruptibleFuture<IConstructor> build(ISourceLocation loc, ITree input) {
        throw new NoContributionException("build");
    }

    @Override
    public InterruptibleFuture<IList> documentSymbol(ITree input) {
        throw new NoContributionException("documentSymbol");
    }

    @Override
    public InterruptibleFuture<IList> codeLens(ITree input) {
        throw new NoContributionException("codeLens");
    }

    @Override
    public InterruptibleFuture<IList> inlayHint(ITree input) {
        throw new NoContributionException("inlayHint");
    }

    @Override
    public InterruptibleFuture<IValue> execution(String command) {
        throw new NoContributionException("execution");
    }

    @Override
    public InterruptibleFuture<ISet> hover(IList focus) {
        throw new NoContributionException("hover");
    }

    @Override
    public InterruptibleFuture<ISet> definition(IList focus) {
        throw new NoContributionException("definition");
    }

    @Override
    public InterruptibleFuture<ISet> references(IList focus) {
        throw new NoContributionException("references");
    }

    @Override
    public InterruptibleFuture<ISet> implementation(IList focus) {
        throw new NoContributionException("implementation");
    }

    @Override
    public InterruptibleFuture<IList> codeAction(IList focus) {
        throw new NoContributionException("codeLens");
    }

    @Override
    public InterruptibleFuture<IList> selectionRange(IList focus) {
        throw new NoContributionException("selectionRange");
    }

    @Override
    public InterruptibleFuture<ISourceLocation> prepareRename(IList focus) {
        throw new NoContributionException("prepareRename");
    }

    @Override
    public InterruptibleFuture<ITuple> rename(IList focus, String name) {
        throw new NoContributionException("rename");
    }

    @Override
    public InterruptibleFuture<ITuple> didRenameFiles(IList fileRenames) {
        throw new NoContributionException("didRenameFiles");
    }

    @Override
    public InterruptibleFuture<IList> prepareCallHierarchy(IList focus) {
        throw new NoContributionException("prepareCallHierarchy");
    }

    @Override
    public InterruptibleFuture<IList> incomingOutgoingCalls(IConstructor hierarchyItem, IConstructor direction) {
        throw new NoContributionException("incomingOutgoingCalls");
    }

    @Override
    public CompletableFuture<IList> parseCodeActions(String command) {
        throw new NoContributionException("parseCodeActions");
    }

    @Override
    public CompletableFuture<IConstructor> parseCallHierarchyData(String data) {
        throw new NoContributionException("parseCallHierarchyData");
    }

    @Override
    public CompletableFuture<Boolean> hasAnalysis() {
        return falsy;
    }

    @Override
    public CompletableFuture<Boolean> hasBuild() {
        return falsy;
    }

    @Override
    public CompletableFuture<Boolean> hasDocumentSymbol() {
        return falsy;
    }

    @Override
    public CompletableFuture<Boolean> hasCodeLens() {
        return falsy;
    }

    @Override
    public CompletableFuture<Boolean> hasInlayHint() {
        return falsy;
    }

    @Override
    public CompletableFuture<Boolean> hasRename() {
        return falsy;
    }

    @Override
    public CompletableFuture<Boolean> hasExecution() {
        return falsy;
    }

    @Override
    public CompletableFuture<Boolean> hasHover() {
        return falsy;
    }

    @Override
    public CompletableFuture<Boolean> hasDefinition() {
        return falsy;
    }

    @Override
    public CompletableFuture<Boolean> hasReferences() {
        return falsy;
    }

    @Override
    public CompletableFuture<Boolean> hasImplementation() {
        return falsy;
    }

    @Override
    public CompletableFuture<Boolean> hasCodeAction() {
        return falsy;
    }

    @Override
    public CompletableFuture<Boolean> hasDidRenameFiles() {
        return falsy;
    }

    @Override
    public CompletableFuture<Boolean> hasSelectionRange() {
        return falsy;
    }

    @Override
    public CompletableFuture<Boolean> hasCallHierarchy() {
        return falsy;
    }

    @Override
    public CompletableFuture<Boolean> specialCaseHighlighting() {
        return falsy;
    }

    @Override
    public CompletableFuture<SummaryConfig> getAnalyzerSummaryConfig() {
        throw new NoContributionException("getAnalyzerSummaryConfig");
    }

    @Override
    public CompletableFuture<SummaryConfig> getBuilderSummaryConfig() {
        throw new NoContributionException("getBuilderSummaryConfig");
    }

    @Override
    public CompletableFuture<SummaryConfig> getOndemandSummaryConfig() {
        throw new NoContributionException("getOndemandSummaryConfig");
    }

    @Override
    public void cancelProgress(String progressId) {
        logger.trace("Cancelling progress {} not supported on dummy contributions.", progressId);
    }
}
