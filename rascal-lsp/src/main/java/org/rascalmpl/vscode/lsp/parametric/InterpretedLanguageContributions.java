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

    private final CompletableFuture<IFunction> parsingService;
    private final CompletableFuture<@Nullable IFunction> analysisService;
    private final CompletableFuture<@Nullable IFunction> buildService;
    private final CompletableFuture<@Nullable IFunction> documentSymbolService;
    private final CompletableFuture<@Nullable IFunction> codeLensService;
    private final CompletableFuture<@Nullable IFunction> inlayHintService;
    private final CompletableFuture<@Nullable IFunction> executionService;
    private final CompletableFuture<@Nullable IFunction> hoverService;
    private final CompletableFuture<@Nullable IFunction> definitionService;
    private final CompletableFuture<@Nullable IFunction> referencesService;
    private final CompletableFuture<@Nullable IFunction> implementationService;
    private final CompletableFuture<@Nullable IFunction> codeActionService;

    private final CompletableFuture<Boolean> hasAnalysisService;
    private final CompletableFuture<Boolean> hasBuildService;
    private final CompletableFuture<Boolean> hasDocumentSymbolService;
    private final CompletableFuture<Boolean> hasCodeLensService;
    private final CompletableFuture<Boolean> hasInlayHintService;
    private final CompletableFuture<Boolean> hasExecutionService;
    private final CompletableFuture<Boolean> hasHoverService;
    private final CompletableFuture<Boolean> hasDefinitionService;
    private final CompletableFuture<Boolean> hasReferencesService;
    private final CompletableFuture<Boolean> hasImplementationService;
    private final CompletableFuture<Boolean> hasCodeActionService;

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
            PathConfig pcfg = new PathConfig().parse(lang.getPathConfig());

            var monitor = new RascalLSPMonitor(client, LogManager.getLogger(logger.getName() + "[" + lang.getName() + "]"), lang.getName() + ": ");

            this.eval =
                EvaluatorUtil.makeFutureEvaluator(exec, docService, workspaceService, client, "evaluator for " + lang.getName(), monitor, pcfg, false, lang.getMainModule());
            var contributions = EvaluatorUtil.runEvaluator(name + ": loading contributions", eval,
                e -> loadContributions(e, lang),
                ValueFactoryFactory.getValueFactory().set(),
                exec, true, client).get();

            this.store = eval.thenApply(e -> ((ModuleEnvironment)e.getModule(mainModule)).getStore());

            this.parsingService        = getFunctionFor(contributions, LanguageContributions.PARSING);
            this.analysisService       = getFunctionFor(contributions, LanguageContributions.ANALYSIS);
            this.buildService          = getFunctionFor(contributions, LanguageContributions.BUILD);
            this.documentSymbolService = getFunctionFor(contributions, LanguageContributions.DOCUMENT_SYMBOL);
            this.codeLensService       = getFunctionFor(contributions, LanguageContributions.CODE_LENS);
            this.inlayHintService      = getFunctionFor(contributions, LanguageContributions.INLAY_HINT);
            this.executionService      = getFunctionFor(contributions, LanguageContributions.EXECUTION);
            this.hoverService          = getFunctionFor(contributions, LanguageContributions.HOVER);
            this.definitionService     = getFunctionFor(contributions, LanguageContributions.DEFINITION);
            this.referencesService     = getFunctionFor(contributions, LanguageContributions.REFERENCES);
            this.implementationService = getFunctionFor(contributions, LanguageContributions.IMPLEMENTATION);
            this.codeActionService     = getFunctionFor(contributions, LanguageContributions.CODE_ACTION);

            // assign boolean properties once instead of wasting futures all the time
            this.hasAnalysisService       = nonNull(this.analysisService);
            this.hasBuildService          = nonNull(this.buildService);
            this.hasDocumentSymbolService = nonNull(this.documentSymbolService);
            this.hasCodeLensService       = nonNull(this.codeLensService);
            this.hasInlayHintService      = nonNull(this.inlayHintService);
            this.hasExecutionService      = nonNull(this.executionService);
            this.hasHoverService          = nonNull(this.hoverService);
            this.hasDefinitionService     = nonNull(this.definitionService);
            this.hasReferencesService     = nonNull(this.referencesService);
            this.hasImplementationService = nonNull(this.implementationService);
            this.hasCodeActionService     = nonNull(this.codeActionService);

            this.analyzerSummaryConfig = scheduledSummaryConfig(contributions, LanguageContributions.ANALYSIS);
            this.builderSummaryConfig  = scheduledSummaryConfig(contributions, LanguageContributions.BUILD);
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
                    isTrue(constructor, LanguageContributions.Summarizers.PROVIDES_HOVERS),
                    isTrue(constructor, LanguageContributions.Summarizers.PROVIDES_DEFINITIONS),
                    isTrue(constructor, LanguageContributions.Summarizers.PROVIDES_REFERENCES),
                    isTrue(constructor, LanguageContributions.Summarizers.PROVIDES_IMPLEMENTATIONS));
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
    public CompletableFuture<ITree> runParsingService(ISourceLocation loc, String input) {
        debug(LanguageContributions.PARSING, loc, input);
        return parsingService.thenApplyAsync(p -> p.call(VF.string(input), loc), exec);
    }

    @Override
    public InterruptibleFuture<IList> runDocumentSymbolService(ITree input) {
        debug(LanguageContributions.DOCUMENT_SYMBOL, TreeAdapter.getLocation(input));
        return execFunction(LanguageContributions.DOCUMENT_SYMBOL, documentSymbolService, VF.list(), input);
    }

    @Override
    public InterruptibleFuture<IConstructor> runAnalysisService(ISourceLocation src, ITree input) {
        debug(LanguageContributions.ANALYSIS, src);
        return execFunction(LanguageContributions.ANALYSIS, analysisService, EmptySummary.newInstance(src), src, input);
    }

    @Override
    public InterruptibleFuture<IConstructor> runBuildService(ISourceLocation src, ITree input) {
        debug(LanguageContributions.BUILD, src);
        return execFunction(LanguageContributions.BUILD, buildService, EmptySummary.newInstance(src), src, input);
    }

    @Override
    public InterruptibleFuture<IList> runCodeLensService(ITree input) {
        debug(LanguageContributions.CODE_LENS, TreeAdapter.getLocation(input));
        return execFunction(LanguageContributions.CODE_LENS, codeLensService, VF.list(), input);
    }

    @Override
    public InterruptibleFuture<IList> runInlayHintService(@Nullable ITree input) {
        debug(LanguageContributions.INLAY_HINT, input != null ? TreeAdapter.getLocation(input) : null);
        return execFunction(LanguageContributions.INLAY_HINT, inlayHintService, VF.list(), input);
    }

    @Override
    public InterruptibleFuture<ISet> runHoverService(IList focus) {
        debug(LanguageContributions.HOVER, focus.length());
        return execFunction(LanguageContributions.HOVER, hoverService, VF.set(), focus);
    }

    @Override
    public InterruptibleFuture<ISet> runDefinitionService(IList focus) {
        debug(LanguageContributions.DEFINITION, focus.length());
        return execFunction(LanguageContributions.DEFINITION, definitionService, VF.set(), focus);
    }

    @Override
    public InterruptibleFuture<ISet> runImplementationService(IList focus) {
        debug(LanguageContributions.IMPLEMENTATION, focus.length());
        return execFunction(LanguageContributions.IMPLEMENTATION, implementationService, VF.set(), focus);
    }

    @Override
    public InterruptibleFuture<ISet> runReferencesService(IList focus) {
        debug(LanguageContributions.REFERENCES, focus.length());
        return execFunction(LanguageContributions.REFERENCES, referencesService, VF.set(), focus);
    }

    @Override
    public InterruptibleFuture<IList> runCodeActionService(IList focus) {
        debug(LanguageContributions.CODE_ACTION, focus.length());
        return execFunction(LanguageContributions.CODE_ACTION, codeActionService, VF.list(), focus);
    }

    private void debug(String name, Object param) {
        logger.debug("{}({})", name, param);
    }

    private void debug(String name, Object param1, Object param2) {
        logger.debug("{}({},{})", name, param1, param2);
    }

    @Override
    public CompletableFuture<Boolean> hasDefinitionService() {
        return hasDefinitionService;
    }

    @Override
    public CompletableFuture<Boolean> hasReferencesService() {
        return hasReferencesService;
    }

    @Override
    public CompletableFuture<Boolean> hasImplementationService() {
        return hasImplementationService;
    }

    @Override
    public CompletableFuture<Boolean> hasHoverService() {
        return hasHoverService;
    }

    @Override
    public CompletableFuture<Boolean> hasExecutionService() {
        return hasExecutionService;
    }

    @Override
    public CompletableFuture<Boolean> hasInlayHintService() {
        return hasInlayHintService;
    }

    @Override
    public CompletableFuture<Boolean> hasCodeLensDetector() {
        return hasCodeLensService;
    }

    @Override
    public CompletableFuture<Boolean> hasDocumentSymbolService() {
        return hasDocumentSymbolService;
    }

    @Override
    public CompletableFuture<Boolean> hasCodeActionService() {
        return hasCodeActionService;
    }

    @Override
    public CompletableFuture<Boolean> hasAnalysisService() {
        return hasAnalysisService;
    }

    @Override
    public CompletableFuture<Boolean> hasBuildService() {
        return hasBuildService;
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
    public InterruptibleFuture<@Nullable IValue> runExecutionService(String command) {
        logger.debug("executeCommand({}...) (full command value in TRACE level)", () -> command.substring(0, Math.min(10, command.length())));
        logger.trace("Full command: {}", command);

        return InterruptibleFuture.flatten(parseCommand(command).thenCombine(
            executionService,
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
