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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.rascalmpl.interpreter.NullRascalMonitor;
import org.rascalmpl.shell.ShellEvaluatorFactory;
import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.values.RascalFunctionValueFactory;
import org.rascalmpl.values.RascalValueFactory;
import org.rascalmpl.values.functions.IFunction;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.ParserSpecification;
import org.rascalmpl.vscode.lsp.util.concurrent.InterruptibleFuture;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.ITuple;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IValueFactory;
import io.usethesource.vallang.exceptions.FactTypeUseException;

public class ParserOnlyContribution implements ILanguageContributions {
    private static final Logger logger = LogManager.getLogger(ParserOnlyContribution.class);
    private static final IValueFactory VF = IRascalValueFactory.getInstance();
    private final String name;
    private final @Nullable Exception loadingParserError;
    private final @Nullable IFunction parser;
    private final CompletableFuture<Boolean> specialCaseHighlighting;
    private final ExecutorService ownExecutor;

    public ParserOnlyContribution(String name, ParserSpecification spec, ExecutorService ownExecutor) {
        this.name = name;
        this.ownExecutor = ownExecutor;

        // we use an entry and a single initialization function to make sure that parser and loadingParserError can be `final`:
        Either<IFunction,Exception> result = loadParser(spec);
        this.parser = result.getLeft();
        this.loadingParserError = result.getRight();
        this.specialCaseHighlighting = CompletableFuture.completedFuture(spec.getSpecialCaseHighlighting());
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public CompletableFuture<ITree> parsing(ISourceLocation loc, String input) {
        if (loadingParserError != null || parser == null) {
            return CompletableFuture.failedFuture(new RuntimeException("Parser function did not load", loadingParserError));
        }

        return CompletableFuture.supplyAsync(() -> parser.call(VF.string(input), loc), ownExecutor);
    }

    private static Either<IFunction, Exception> loadParser(ParserSpecification spec) {
        // the next two object are scaffolding. we only need them temporarily, and they will not be used by the returned IFunction if the (internal) _call_ methods are not used from ICallableValue.
        var unusedEvaluator = ShellEvaluatorFactory.getBasicEvaluator(Reader.nullReader(), new PrintWriter(Writer.nullWriter()), new PrintWriter(Writer.nullWriter()), new NullRascalMonitor(), "***unused***");
        // this is what we are after: a factory that can load back parsers.
        IRascalValueFactory vf = new RascalFunctionValueFactory(unusedEvaluator /*can not be null unfortunately*/);
        IConstructor reifiedType = makeReifiedType(spec, vf);

        try {
            logger.debug("Loading parser {} at {}", reifiedType, spec.getParserLocation());
            // this hides all the loading and instantiation details of Rascal-generated parsers
            var parser = vf.loadParser(reifiedType, spec.getParserLocation(), VF.bool(spec.getAllowAmbiguity()), VF.integer(spec.getMaxAmbDepth()),
                VF.bool(spec.getAllowRecovery()), VF.integer(spec.getMaxRecoveryAttempts()), VF.integer(spec.getMaxRecoveryTokens()), VF.bool(false), VF.bool(false), vf.set());
            logger.debug("Got parser: {}", parser);
            return Either.forLeft(parser);
        }
        catch (IOException | ClassNotFoundException | FactTypeUseException e) {
            logger.catching(e);
            return Either.forRight(e);
        }

    }

    /** convert a non-terminal name into a proper reified type */
    private static IConstructor makeReifiedType(ParserSpecification spec, IRascalValueFactory vf) {
        String nt = spec.getNonTerminalName();
        IConstructor symbol = vf.constructor(RascalValueFactory.Symbol_Sort, VF.string(nt));
        symbol = spec.getNonTerminalIsStart() ? vf.constructor(RascalValueFactory.Symbol_Start, symbol) : symbol;
        return vf.reifiedType(symbol, vf.map());
    }

    @Override
    public InterruptibleFuture<IList> documentSymbol(ITree input) {
        return InterruptibleFuture.completedFuture(VF.list());
    }

    @Override
    public InterruptibleFuture<IConstructor> analysis(ISourceLocation loc, ITree input) {
        return InterruptibleFuture.completedFuture(EmptySummary.newInstance(loc));
    }

    @Override
    public InterruptibleFuture<IConstructor> build(ISourceLocation loc, ITree input) {
        return InterruptibleFuture.completedFuture(EmptySummary.newInstance(loc));
    }

    @Override
    public InterruptibleFuture<IList> codeLens(ITree input) {
        return InterruptibleFuture.completedFuture(VF.list());
    }

    @Override
    public InterruptibleFuture<@Nullable IValue> execution(String command) {
        return InterruptibleFuture.completedFuture(VF.bool(false));
    }

    @Override
    public CompletableFuture<IList> parseCodeActions(String commands) {
        return CompletableFuture.completedFuture(VF.list());
    }

    @Override
    public InterruptibleFuture<IList> inlayHint(@Nullable ITree input) {
        return InterruptibleFuture.completedFuture(VF.list());
    }

    @Override
    public InterruptibleFuture<ISourceLocation> prepareRename(IList focus) {
        return InterruptibleFuture.completedFuture(null);
    }

    @Override
    public InterruptibleFuture<ITuple> rename(IList focus, String name) {
        return InterruptibleFuture.completedFuture(VF.tuple(VF.list(), VF.list()));
    }

    @Override
    public InterruptibleFuture<ITuple> didRenameFiles(IList fileRenames) {
        return InterruptibleFuture.completedFuture(VF.tuple(VF.list(), VF.list()));
    }

    @Override
    public InterruptibleFuture<ISet> hover(IList focus) {
        return InterruptibleFuture.completedFuture(VF.set());
    }

    @Override
    public InterruptibleFuture<ISet> definition(IList focus) {
        return InterruptibleFuture.completedFuture(VF.set());
    }

    @Override
    public InterruptibleFuture<ISet> references(IList focus) {
        return InterruptibleFuture.completedFuture(VF.set());
    }

    @Override
    public InterruptibleFuture<IList> codeAction(IList focus) {
        return InterruptibleFuture.completedFuture(VF.list());
    }

    @Override
    public InterruptibleFuture<IList> formatting(ITree input, ISourceLocation loc, IConstructor formattingOptions) {
        return InterruptibleFuture.completedFuture(VF.list());
    }

    @Override
    public CompletableFuture<Boolean> hasFormatting() {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public InterruptibleFuture<ISet> implementation(IList focus) {
        return InterruptibleFuture.completedFuture(VF.set());
    }

    @Override
    public InterruptibleFuture<IList> selectionRange(IList focus) {
        return InterruptibleFuture.completedFuture(VF.list());
    }

    @Override
    public CompletableFuture<Boolean> hasHover() {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletableFuture<Boolean> hasDefinition() {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletableFuture<Boolean> hasReferences() {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletableFuture<Boolean> hasImplementation() {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletableFuture<Boolean> hasDocumentSymbol() {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletableFuture<Boolean> hasAnalysis() {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletableFuture<Boolean> hasBuild() {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletableFuture<Boolean> hasCodeAction() {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletableFuture<Boolean> hasCodeLens() {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletableFuture<Boolean> hasExecution() {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletableFuture<Boolean> hasInlayHint() {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletableFuture<Boolean> hasRename() {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletableFuture<Boolean> hasDidRenameFiles() {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletableFuture<Boolean> hasSelectionRange() {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletableFuture<Boolean> specialCaseHighlighting() {
        return specialCaseHighlighting;
    }

    @Override
    public CompletableFuture<SummaryConfig> getAnalyzerSummaryConfig() {
        return CompletableFuture.completedFuture(SummaryConfig.FALSY);
    }

    @Override
    public CompletableFuture<SummaryConfig> getBuilderSummaryConfig() {
        return CompletableFuture.completedFuture(SummaryConfig.FALSY);
    }

    @Override
    public CompletableFuture<SummaryConfig> getOndemandSummaryConfig() {
        return CompletableFuture.completedFuture(SummaryConfig.FALSY);
    }

    @Override
    public void cancelProgress(String progressId) {
        // empty, since this contribution does not have any running tasks nor a monitor
    }

}
