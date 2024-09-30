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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.rascalmpl.interpreter.Evaluator;
import org.rascalmpl.interpreter.NullRascalMonitor;
import org.rascalmpl.interpreter.env.GlobalEnvironment;
import org.rascalmpl.interpreter.env.ModuleEnvironment;
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
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IValueFactory;
import io.usethesource.vallang.exceptions.FactTypeUseException;

public class ParserOnlyContribution implements ILanguageContributions {
    private static final Logger logger = LogManager.getLogger(ParserOnlyContribution.class);
    private static final IValueFactory VF = IRascalValueFactory.getInstance();
    private final String name;
    private final @Nullable Exception loadingParserError;
    private final @Nullable IFunction parser;

    public ParserOnlyContribution(String name, ParserSpecification spec) {
        this.name = name;

        // we use an entry and a single initialization function to make sure that parser and loadingParserError can be `final`:
        Either<IFunction,Exception> result = loadParser(spec);
        this.parser = result.getLeft();
        this.loadingParserError = result.getRight();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public CompletableFuture<ITree> parseSourceFile(ISourceLocation loc, String input) {
        if (loadingParserError != null || parser == null) {
            return CompletableFuture.failedFuture(new RuntimeException("Parser function did not load", loadingParserError));
        }

        return CompletableFuture.supplyAsync(() -> parser.call(VF.string(input), loc));
    }

    private static Either<IFunction, Exception> loadParser(ParserSpecification spec) {
        // the next two object are scaffolding. we only need them temporarily, and they will not be used by the returned IFunction if the (internal) _call_ methods are not used from ICallableValue.
        GlobalEnvironment unusedHeap = new GlobalEnvironment();
        Evaluator unusedEvaluator = new Evaluator(VF, InputStream.nullInputStream(), OutputStream.nullOutputStream(), OutputStream.nullOutputStream(), new NullRascalMonitor(), new ModuleEnvironment("***unused***", unusedHeap), unusedHeap);
        // this is what we are after: a factory that can load back parsers.
        IRascalValueFactory vf = new RascalFunctionValueFactory(unusedEvaluator /*can not be null unfortunately*/);
        IConstructor reifiedType = makeReifiedType(spec, vf);

        try {
            logger.debug("Loading parser {} at {}", reifiedType, spec.getParserLocation());
            // this hides all the loading and instantiation details of Rascal-generated parsers
            var parser = vf.loadParser(reifiedType, spec.getParserLocation(), VF.bool(spec.getAllowAmbiguity()), VF.bool(false), VF.bool(false), vf.set());
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
    public InterruptibleFuture<IList> outline(ITree input) {
        return InterruptibleFuture.completedFuture(VF.list());
    }

    @Override
    public InterruptibleFuture<IConstructor> analyze(ISourceLocation loc, ITree input) {
        return InterruptibleFuture.completedFuture(EmptySummary.newInstance(loc));
    }

    @Override
    public InterruptibleFuture<IConstructor> build(ISourceLocation loc, ITree input) {
        return InterruptibleFuture.completedFuture(EmptySummary.newInstance(loc));
    }

    @Override
    public InterruptibleFuture<ISet> lenses(ITree input) {
        return InterruptibleFuture.completedFuture(VF.set());
    }

    @Override
    public InterruptibleFuture<@Nullable IValue> executeCommand(String command) {
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
    public InterruptibleFuture<ISet> documentation(ISourceLocation loc, ITree input, ITree cursor) {
        return InterruptibleFuture.completedFuture(VF.set());
    }

    @Override
    public InterruptibleFuture<ISet> definitions(ISourceLocation loc, ITree input, ITree cursor) {
        return InterruptibleFuture.completedFuture(VF.set());
    }

    @Override
    public InterruptibleFuture<ISet> references(ISourceLocation loc, ITree input, ITree cursor) {
        return InterruptibleFuture.completedFuture(VF.set());
    }

    @Override
    public InterruptibleFuture<IList> codeActions(IList focus) {
        return InterruptibleFuture.completedFuture(VF.list());
    }

    @Override
    public InterruptibleFuture<ISet> implementations(ISourceLocation loc, ITree input, ITree cursor) {
        return InterruptibleFuture.completedFuture(VF.set());
    }

    @Override
    public CompletableFuture<Boolean> hasDocumenter() {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletableFuture<Boolean> hasDefiner() {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletableFuture<Boolean> hasReferrer() {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletableFuture<Boolean> hasImplementer() {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletableFuture<Boolean> hasOutliner() {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletableFuture<Boolean> hasAnalyzer() {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletableFuture<Boolean> hasBuilder() {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletableFuture<Boolean> hasCodeActionsContributor() {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletableFuture<Boolean> hasLensDetector() {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletableFuture<Boolean> hasCommandExecutor() {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletableFuture<Boolean> hasInlayHinter() {
        return CompletableFuture.completedFuture(false);
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

}
