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
import java.io.StringReader;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.rascalmpl.interpreter.Evaluator;
import org.rascalmpl.interpreter.env.ModuleEnvironment;
import org.rascalmpl.library.util.PathConfig;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.values.ValueFactoryFactory;
import org.rascalmpl.values.functions.IFunction;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.values.parsetrees.TreeAdapter;
import org.rascalmpl.vscode.lsp.BaseWorkspaceService;
import org.rascalmpl.vscode.lsp.IBaseLanguageClient;
import org.rascalmpl.vscode.lsp.IBaseTextDocumentService;
import org.rascalmpl.vscode.lsp.RascalLSPMonitor;
import org.rascalmpl.vscode.lsp.parametric.model.RascalADTs.LanguageContributions;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.LanguageParameter;
import org.rascalmpl.vscode.lsp.util.EvaluatorUtil;
import org.rascalmpl.vscode.lsp.util.EvaluatorUtil.LSPContext;
import org.rascalmpl.vscode.lsp.util.concurrent.InterruptibleFuture;
import io.usethesource.vallang.IBool;
import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IValueFactory;
import io.usethesource.vallang.exceptions.FactTypeUseException;
import io.usethesource.vallang.io.StandardTextReader;
import io.usethesource.vallang.type.TypeFactory;
import io.usethesource.vallang.type.TypeStore;

public class InterpretedLanguageContributions implements ILanguageContributions {
    private static final IValueFactory VF = IRascalValueFactory.getInstance();
    private static final Logger logger = LogManager.getLogger(InterpretedLanguageContributions.class);

    private final ExecutorService exec;

    private final String name;
    private final String mainModule;

    private final CompletableFuture<Evaluator> eval;
    private final CompletableFuture<TypeStore> store;

    private final CompletableFuture<IFunction> parsing;
    private final CompletableFuture<@Nullable IFunction> analysis;
    private final CompletableFuture<@Nullable IFunction> build;
    private final CompletableFuture<@Nullable IFunction> documentSymbol;
    private final CompletableFuture<@Nullable IFunction> codeLens;
    private final CompletableFuture<@Nullable IFunction> inlayHint;
    private final CompletableFuture<@Nullable IFunction> execution;
    private final CompletableFuture<@Nullable IFunction> hover;
    private final CompletableFuture<@Nullable IFunction> definition;
    private final CompletableFuture<@Nullable IFunction> references;
    private final CompletableFuture<@Nullable IFunction> implementation;
    private final CompletableFuture<@Nullable IFunction> codeAction;

    private final CompletableFuture<Boolean> hasAnalysis;
    private final CompletableFuture<Boolean> hasBuild;
    private final CompletableFuture<Boolean> hasDocumentSymbol;
    private final CompletableFuture<Boolean> hasCodeLens;
    private final CompletableFuture<Boolean> hasInlayHint;
    private final CompletableFuture<Boolean> hasExecution;
    private final CompletableFuture<Boolean> hasHover;
    private final CompletableFuture<Boolean> hasDefinition;
    private final CompletableFuture<Boolean> hasReferences;
    private final CompletableFuture<Boolean> hasImplementation;
    private final CompletableFuture<Boolean> hasCodeAction;

    private final CompletableFuture<Boolean> specialCaseHighlighting;

    private final CompletableFuture<SummaryConfig> analyzerSummaryConfig;
    private final CompletableFuture<SummaryConfig> builderSummaryConfig;
    private final CompletableFuture<SummaryConfig> ondemandSummaryConfig;
    private final IBaseLanguageClient client;

    public InterpretedLanguageContributions(LanguageParameter lang, IBaseTextDocumentService docService, BaseWorkspaceService workspaceService, IBaseLanguageClient client, ExecutorService exec) {
        this.client = client;
        this.name = lang.getName();
        this.mainModule = lang.getMainModule();
        this.exec = exec;

        try {
            var pcfg = new PathConfig().parse(lang.getPathConfig());
            pcfg = EvaluatorUtil.addLSPSources(pcfg, false);

            var monitor = new RascalLSPMonitor(client, LogManager.getLogger(logger.getName() + "[" + lang.getName() + "]"), lang.getName() + ": ");

            this.eval = EvaluatorUtil.makeFutureEvaluator(new LSPContext(exec, docService, workspaceService, client),
                "evaluator for " + lang.getName(), monitor, pcfg, lang.getMainModule());
            var contributions = EvaluatorUtil.runEvaluator(name + ": loading contributions", eval,
                e -> loadContributions(e, lang),
                ValueFactoryFactory.getValueFactory().set(),
                exec, true, client).get();

            this.store = eval.thenApply(e -> ((ModuleEnvironment)e.getModule(mainModule)).getStore());

            this.parsing = getFunctionFor(contributions, LanguageContributions.PARSING);
            this.analysis = getFunctionFor(contributions, LanguageContributions.ANALYSIS);
            this.build = getFunctionFor(contributions, LanguageContributions.BUILD);
            this.documentSymbol = getFunctionFor(contributions, LanguageContributions.DOCUMENT_SYMBOL);
            this.codeLens = getFunctionFor(contributions, LanguageContributions.CODE_LENS);
            this.inlayHint = getFunctionFor(contributions, LanguageContributions.INLAY_HINT);
            this.execution = getFunctionFor(contributions, LanguageContributions.EXECUTION);
            this.hover = getFunctionFor(contributions, LanguageContributions.HOVER);
            this.definition = getFunctionFor(contributions, LanguageContributions.DEFINITION);
            this.references = getFunctionFor(contributions, LanguageContributions.REFERENCES);
            this.implementation = getFunctionFor(contributions, LanguageContributions.IMPLEMENTATION);
            this.codeAction = getFunctionFor(contributions, LanguageContributions.CODE_ACTION);

            // assign boolean properties once instead of wasting futures all the time
            this.hasAnalysis = nonNull(this.analysis);
            this.hasBuild = nonNull(this.build);
            this.hasDocumentSymbol = nonNull(this.documentSymbol);
            this.hasCodeLens = nonNull(this.codeLens);
            this.hasInlayHint = nonNull(this.inlayHint);
            this.hasExecution = nonNull(this.execution);
            this.hasHover = nonNull(this.hover);
            this.hasDefinition = nonNull(this.definition);
            this.hasReferences = nonNull(this.references);
            this.hasImplementation = nonNull(this.implementation);
            this.hasCodeAction = nonNull(this.codeAction);

            this.specialCaseHighlighting = getContributionParameter(contributions,
                LanguageContributions.PARSING,
                LanguageContributions.Parameters.USES_SPECIAL_CASE_HIGHLIGHTING);

            this.analyzerSummaryConfig = scheduledSummaryConfig(contributions, LanguageContributions.ANALYSIS);
            this.builderSummaryConfig = scheduledSummaryConfig(contributions, LanguageContributions.BUILD);
            this.ondemandSummaryConfig = ondemandSummaryConfig(contributions);

        } catch (IOException e1) {
            logger.catching(e1);
            throw new IllegalArgumentException(e1);
        }
    }

    private static CompletableFuture<Boolean> nonNull(CompletableFuture<?> x) {
        return x.thenApply(Objects::nonNull);
    }

    private static CompletableFuture<SummaryConfig> scheduledSummaryConfig(CompletableFuture<ISet> contributions, String summarizer) {
        return contributions.thenApply(c -> {
            var constructor = getContribution(c, summarizer);
            if (constructor != null) {
                return new SummaryConfig(
                    isTrue(constructor, LanguageContributions.Parameters.PROVIDES_HOVERS),
                    isTrue(constructor, LanguageContributions.Parameters.PROVIDES_DEFINITIONS),
                    isTrue(constructor, LanguageContributions.Parameters.PROVIDES_REFERENCES),
                    isTrue(constructor, LanguageContributions.Parameters.PROVIDES_IMPLEMENTATIONS));
            } else {
                return SummaryConfig.FALSY;
            }
        });
    }

    private static CompletableFuture<SummaryConfig> ondemandSummaryConfig(CompletableFuture<ISet> contributions) {
        return contributions.thenApply(c ->
            new SummaryConfig(
                hasContribution(c, LanguageContributions.HOVER),
                hasContribution(c, LanguageContributions.DEFINITION),
                hasContribution(c, LanguageContributions.REFERENCES),
                hasContribution(c, LanguageContributions.IMPLEMENTATION)));
    }

    private static @Nullable IConstructor getContribution(ISet contributions, String name) {
        return contributions
            .stream()
            .map(IConstructor.class::cast)
            .filter(cons -> cons.getConstructorType().getName().equals(name))
            .findAny()
            .orElse(null);
    }

    private static boolean hasContribution(ISet contributions, String name) {
        return getContribution(contributions, name) != null;
    }

    private static CompletableFuture<Boolean> getContributionParameter(
            CompletableFuture<ISet> contributions, String name, String parameter) {

        return contributions.thenApply(c -> isTrue(getContribution(c, name), parameter));
    }

    private static boolean isTrue(@Nullable IConstructor constructor, String parameter) {
        if (constructor == null) {
            return false;
        }
        var val = constructor.asWithKeywordParameters().getParameter(parameter);
        return !(val instanceof IBool) || ((IBool)val).getValue();
    }

    private static ISet loadContributions(Evaluator eval, LanguageParameter lang) {
        return (ISet) eval.eval(eval.getMonitor(), lang.getMainFunction() + "()", URIUtil.rootLocation("lsp"))
            .getValue();
    }

    @Override
    public CompletableFuture<IList> parseCodeActions(String command) {
        return store.thenApply(commandStore -> {
            try {
                var TF = TypeFactory.getInstance();
                return (IList) new StandardTextReader().read(VF, commandStore, TF.listType(commandStore.lookupAbstractDataType("CodeAction")), new StringReader(command));
            } catch (FactTypeUseException | IOException e) {
                // this should never happen as long as the Rascal code
                // for creating errors is type-correct. So it _might_ happen
                // when running the interpreter on broken code.
                throw new IllegalArgumentException("The command could not be parsed", e);
            }
        });
    }

    private CompletableFuture<IConstructor> parseCommand(String command) {
        return store.thenApply(commandStore -> {
            try {
                return (IConstructor) new StandardTextReader().read(VF, commandStore, commandStore.lookupAbstractDataType("Command"), new StringReader(command));
            } catch (FactTypeUseException | IOException e) {
                logger.catching(e);
                throw new IllegalArgumentException("The command could not be parsed", e);
            }
        });
    }

    private static CompletableFuture<@Nullable IFunction> getFunctionFor(CompletableFuture<ISet> contributions, String cons) {
        return contributions.thenApply(conts -> {
            for (IValue elem : conts) {
                IConstructor contrib = (IConstructor) elem;
                if (cons.equals(contrib.getConstructorType().getName())) {
                    return (IFunction) contrib.get(0);
                }
            }
            logger.debug("No {} defined", cons);
            return null;
        });
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public CompletableFuture<ITree> parsing(ISourceLocation loc, String input) {
        debug(LanguageContributions.PARSING, loc, input);
        return parsing.thenApplyAsync(p -> p.call(VF.string(input), loc), exec);
    }

    @Override
    public InterruptibleFuture<IList> documentSymbol(ITree input) {
        debug(LanguageContributions.DOCUMENT_SYMBOL, TreeAdapter.getLocation(input));
        return execFunction(LanguageContributions.DOCUMENT_SYMBOL, documentSymbol, VF.list(), input);
    }

    @Override
    public InterruptibleFuture<IConstructor> analysis(ISourceLocation src, ITree input) {
        debug(LanguageContributions.ANALYSIS, src);
        return execFunction(LanguageContributions.ANALYSIS, analysis, EmptySummary.newInstance(src), src, input);
    }

    @Override
    public InterruptibleFuture<IConstructor> build(ISourceLocation src, ITree input) {
        debug(LanguageContributions.BUILD, src);
        return execFunction(LanguageContributions.BUILD, build, EmptySummary.newInstance(src), src, input);
    }

    @Override
    public InterruptibleFuture<IList> codeLens(ITree input) {
        debug(LanguageContributions.CODE_LENS, TreeAdapter.getLocation(input));
        return execFunction(LanguageContributions.CODE_LENS, codeLens, VF.list(), input);
    }

    @Override
    public InterruptibleFuture<IList> inlayHint(@Nullable ITree input) {
        debug(LanguageContributions.INLAY_HINT, input != null ? TreeAdapter.getLocation(input) : null);
        return execFunction(LanguageContributions.INLAY_HINT, inlayHint, VF.list(), input);
    }

    @Override
    public InterruptibleFuture<ISet> hover(IList focus) {
        debug(LanguageContributions.HOVER, focus.length());
        return execFunction(LanguageContributions.HOVER, hover, VF.set(), focus);
    }

    @Override
    public InterruptibleFuture<ISet> definition(IList focus) {
        debug(LanguageContributions.DEFINITION, focus.length());
        return execFunction(LanguageContributions.DEFINITION, definition, VF.set(), focus);
    }

    @Override
    public InterruptibleFuture<ISet> implementation(IList focus) {
        debug(LanguageContributions.IMPLEMENTATION, focus.length());
        return execFunction(LanguageContributions.IMPLEMENTATION, implementation, VF.set(), focus);
    }

    @Override
    public InterruptibleFuture<ISet> references(IList focus) {
        debug(LanguageContributions.REFERENCES, focus.length());
        return execFunction(LanguageContributions.REFERENCES, references, VF.set(), focus);
    }

    @Override
    public InterruptibleFuture<IList> codeAction(IList focus) {
        debug(LanguageContributions.CODE_ACTION, focus.length());
        return execFunction(LanguageContributions.CODE_ACTION, codeAction, VF.list(), focus);
    }

    private void debug(String name, Object param) {
        logger.debug("{}({})", name, param);
    }

    private void debug(String name, Object param1, Object param2) {
        logger.debug("{}({},{})", name, param1, param2);
    }

    @Override
    public CompletableFuture<Boolean> hasDefinition() {
        return hasDefinition;
    }

    @Override
    public CompletableFuture<Boolean> hasReferences() {
        return hasReferences;
    }

    @Override
    public CompletableFuture<Boolean> hasImplementation() {
        return hasImplementation;
    }

    @Override
    public CompletableFuture<Boolean> hasHover() {
        return hasHover;
    }

    @Override
    public CompletableFuture<Boolean> hasExecution() {
        return hasExecution;
    }

    @Override
    public CompletableFuture<Boolean> hasInlayHint() {
        return hasInlayHint;
    }

    @Override
    public CompletableFuture<Boolean> hasCodeLens() {
        return hasCodeLens;
    }

    @Override
    public CompletableFuture<Boolean> hasDocumentSymbol() {
        return hasDocumentSymbol;
    }

    @Override
    public CompletableFuture<Boolean> hasCodeAction() {
        return hasCodeAction;
    }

    @Override
    public CompletableFuture<Boolean> hasAnalysis() {
        return hasAnalysis;
    }

    @Override
    public CompletableFuture<Boolean> hasBuild() {
        return hasBuild;
    }

    @Override
    public CompletableFuture<Boolean> specialCaseHighlighting() {
        return specialCaseHighlighting;
    }

    @Override
    public CompletableFuture<SummaryConfig> getAnalyzerSummaryConfig() {
        return analyzerSummaryConfig;
    }

    @Override
    public CompletableFuture<SummaryConfig> getBuilderSummaryConfig() {
        return builderSummaryConfig;
    }

    @Override
    public CompletableFuture<SummaryConfig> getOndemandSummaryConfig() {
        return ondemandSummaryConfig;
    }

    @Override
    public InterruptibleFuture<@Nullable IValue> execution(String command) {
        logger.debug("executeCommand({}...) (full command value in TRACE level)", () -> command.substring(0, Math.min(10, command.length())));
        logger.trace("Full command: {}", command);

        return InterruptibleFuture.flatten(parseCommand(command).thenCombine(
            execution,
            (cons, func) -> {

                if (func == null) {
                    logger.warn("Command is being executed without a registered command executor; for language {}", name);
                    throw new IllegalStateException("No command executor registered for " + name);
                }

                return EvaluatorUtil.<@Nullable IValue>runEvaluator(
                    "executeCommand",
                    eval,
                    ev -> func.call(cons),
                    null,
                    exec,
                    true,
                    client
                );
            }
        ), exec);
    }

    private <T> InterruptibleFuture<T> execFunction(String name, CompletableFuture<@Nullable IFunction> target, T defaultResult, IValue... args) {
        return InterruptibleFuture.flatten(target.thenApply(
            s -> {
                if (s == null) {
                    return InterruptibleFuture.completedFuture(defaultResult);
                }

                return EvaluatorUtil.runEvaluator(name, eval, e -> s.call(args), defaultResult, exec, true, client);
            }),
            exec);
    }
}
