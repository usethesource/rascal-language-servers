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
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.values.IRascalValueFactory;
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
import io.usethesource.vallang.IValueFactory;

/**
 * An implementation of {@link ILanguageContributions} that has no contributions.
 * It is intended to be used as a placeholder for files which do not have any contributions registered,
 * or as a super-class for very selective contributions.
 * The default values that these contributions return should mirror the ones in `InterpretedLanguageContributions`.
 */
public class NoContributions implements ILanguageContributions {

    private static final Logger logger = LogManager.getLogger(NoContributions.class);
    private static final IValueFactory VF = IRascalValueFactory.getInstance();

    private final String name;
    private final Executor exec;
    private final CompletableFuture<Boolean> falsy;

    public class NoContributionException extends RuntimeException {
        private NoContributionException(String message) {
            super("Missing contribution: " + message);
        }
    }

    public NoContributions(String name, Executor exec) {
        this.name = name;
        this.exec = exec;
        this.falsy = CompletableFutureUtils.completedFuture(false, exec);
    }

    private final <T> InterruptibleFuture<T> interruptible(T t) {
        return InterruptibleFuture.completedFuture(t, exec);
    }

    private final <T> CompletableFuture<T> completable(T t) {
        return CompletableFutureUtils.completedFuture(t, exec);
    }

    @Override
    public String getName() {
        return String.format("Empty contributions for '%s'", name);
    }

    @Override
    public CompletableFuture<ITree> parsing(ISourceLocation loc, String input) {
        return CompletableFuture.failedFuture(new NoContributionException("parsing"));
    }

    @Override
    public InterruptibleFuture<IConstructor> analysis(ISourceLocation loc, ITree input) {
        return interruptible(EmptySummary.newInstance(loc));
    }

    @Override
    public InterruptibleFuture<IConstructor> build(ISourceLocation loc, ITree input) {
        return interruptible(EmptySummary.newInstance(loc));
    }

    @Override
    public InterruptibleFuture<IList> documentSymbol(ITree input) {
        return interruptible(VF.list());
    }

    @Override
    public InterruptibleFuture<IList> codeLens(ITree input) {
        return interruptible(VF.list());
    }

    @Override
    public InterruptibleFuture<IList> inlayHint(ITree input) {
        return interruptible(VF.list());
    }

    @Override
    public InterruptibleFuture<IValue> execution(String command) {
        // Similar to default in ParametricTextDocumentService::executeCommand
        return interruptible(VF.string("Execution not configured"));
    }

    @Override
    public InterruptibleFuture<ISet> hover(IList focus) {
        return interruptible(VF.set());
    }

    @Override
    public InterruptibleFuture<ISet> definition(IList focus) {
        return interruptible(VF.set());
    }

    @Override
    public InterruptibleFuture<ISet> references(IList focus) {
        return interruptible(VF.set());
    }

    @Override
    public InterruptibleFuture<ISet> implementation(IList focus) {
        return interruptible(VF.set());
    }

    @Override
    public InterruptibleFuture<IList> codeAction(IList focus) {
        return interruptible(VF.list());
    }

    @Override
    public InterruptibleFuture<IList> selectionRange(IList focus) {
        return interruptible(VF.list());
    }

    @Override
    public InterruptibleFuture<ISourceLocation> prepareRename(IList focus) {
        return interruptible(URIUtil.unknownLocation());
    }

    @Override
    public InterruptibleFuture<ITuple> rename(IList focus, String name) {
        return interruptible(VF.tuple(VF.list(), VF.set()));
    }

    @Override
    public InterruptibleFuture<ITuple> didRenameFiles(IList fileRenames) {
        return interruptible(VF.tuple(VF.list(), VF.set()));
    }

    @Override
    public InterruptibleFuture<IList> prepareCallHierarchy(IList focus) {
        return interruptible(VF.list());
    }

    @Override
    public InterruptibleFuture<IList> incomingOutgoingCalls(IConstructor hierarchyItem, IConstructor direction) {
        return interruptible(VF.list());
    }

    @Override
    public CompletableFuture<IList> parseCodeActions(String command) {
        return completable(VF.list());
    }

    @Override
    public CompletableFuture<IConstructor> parseCallHierarchyData(String data) {
        // This should only be called on the same contributions on which `callHierarchy` was called. It parses some data from the call hierarchy.
        // Since our `callHierarchy` return nothing, this should really never be called. There is also no sensible default.
        // Hence, we throw an exception here.
        return CompletableFuture.failedFuture(new NoContributionException("parseCallHierarchyData"));
    }

    @Override
    public InterruptibleFuture<IList> completion(IList focus, IInteger cursorOffset, IConstructor trigger) {
        return interruptible(VF.list());
    }

    @Override
    public CompletableFuture<IList> completionTriggerCharacters() {
        return completable(VF.list());
    }

    @Override
    public CompletableFuture<Boolean> providesAnalysis() {
        return falsy;
    }

    @Override
    public CompletableFuture<Boolean> providesBuild() {
        return falsy;
    }

    @Override
    public CompletableFuture<Boolean> providesDocumentSymbol() {
        return falsy;
    }

    @Override
    public CompletableFuture<Boolean> providesCodeLens() {
        return falsy;
    }

    @Override
    public CompletableFuture<Boolean> providesInlayHint() {
        return falsy;
    }

    @Override
    public CompletableFuture<Boolean> providesRename() {
        return falsy;
    }

    @Override
    public CompletableFuture<Boolean> providesExecution() {
        return falsy;
    }

    @Override
    public CompletableFuture<Boolean> providesHover() {
        return falsy;
    }

    @Override
    public CompletableFuture<Boolean> providesDefinition() {
        return falsy;
    }

    @Override
    public CompletableFuture<Boolean> providesReferences() {
        return falsy;
    }

    @Override
    public CompletableFuture<Boolean> providesImplementation() {
        return falsy;
    }

    @Override
    public CompletableFuture<Boolean> providesCodeAction() {
        return falsy;
    }

    @Override
    public CompletableFuture<Boolean> providesDidRenameFiles() {
        return falsy;
    }

    @Override
    public CompletableFuture<Boolean> providesSelectionRange() {
        return falsy;
    }

    @Override
    public CompletableFuture<Boolean> providesCallHierarchy() {
        return falsy;
    }

    @Override
    public CompletableFuture<Boolean> providesCompletion() {
        return falsy;
    }

    @Override
    public CompletableFuture<Boolean> specialCaseHighlighting() {
        return falsy;
    }

    @Override
    public CompletableFuture<SummaryConfig> getAnalyzerSummaryConfig() {
        return completable(SummaryConfig.FALSY);
    }

    @Override
    public CompletableFuture<SummaryConfig> getBuilderSummaryConfig() {
        return completable(SummaryConfig.FALSY);
    }

    @Override
    public CompletableFuture<SummaryConfig> getOndemandSummaryConfig() {
        return completable(SummaryConfig.FALSY);
    }

    @Override
    public void cancelProgress(String progressId) {
        logger.trace("Cancelling progress {} not supported on dummy contributions.", progressId);
    }
}
