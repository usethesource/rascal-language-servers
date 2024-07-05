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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.rascalmpl.debug.IRascalMonitor;
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
    private final CompletableFuture<IFunction> parser;
    private final CompletableFuture<@Nullable IFunction> outliner;
    private final CompletableFuture<@Nullable IFunction> analyzer;
    private final CompletableFuture<@Nullable IFunction> builder;
    private final CompletableFuture<@Nullable IFunction> lenses;
    private final CompletableFuture<@Nullable IFunction> commandExecutor;
    private final CompletableFuture<@Nullable IFunction> inlayHinter;
    private final CompletableFuture<@Nullable IFunction> documenter;
    private final CompletableFuture<@Nullable IFunction> definer;
    private final CompletableFuture<@Nullable IFunction> referrer;
    private final CompletableFuture<@Nullable IFunction> implementer;
    private final CompletableFuture<@Nullable IFunction> codeActionContributor;

    private final CompletableFuture<Boolean> hasOutliner;
    private final CompletableFuture<Boolean> hasAnalyzer;
    private final CompletableFuture<Boolean> hasBuilder;
    private final CompletableFuture<Boolean> hasLensDetector;
    private final CompletableFuture<Boolean> hasCommandExecutor;
    private final CompletableFuture<Boolean> hasInlayHinter;
    private final CompletableFuture<Boolean> hasDocumenter;
    private final CompletableFuture<Boolean> hasDefiner;
    private final CompletableFuture<Boolean> hasReferrer;
    private final CompletableFuture<Boolean> hasImplementer;
    private final CompletableFuture<Boolean> hasCodeActionContributor;

    private final CompletableFuture<SummaryConfig> analyzerSummaryConfig;
    private final CompletableFuture<SummaryConfig> builderSummaryConfig;
    private final CompletableFuture<SummaryConfig> ondemandSummaryConfig;
    

    private class MonitorWrapper implements IRascalMonitor {
        private final IRascalMonitor original;
        private final String namePrefix;
        private final ThreadLocal<Deque<String>> activeProgress = ThreadLocal.withInitial(ArrayDeque::new);

        public MonitorWrapper(IRascalMonitor original, String namePrefix) {
            this.original = original;
            this.namePrefix = namePrefix + ": ";
        }

        @Override
        public void jobStart(String name, int workShare, int totalWork) {
            var progressStack = activeProgress.get();
            if (progressStack.isEmpty()) {
                progressStack.push(name);
                name = namePrefix + name;
            }
            else {
                progressStack.push(name);
            }
            original.jobStart(name, workShare, totalWork);
        }

        @Override
        public void jobStep(String name, String message, int workShare) {
            if (activeProgress.get().size() == 1) {
                name = namePrefix + name;
            }
            original.jobStep(name, message, workShare);
        }

        @Override
        public int jobEnd(String name, boolean succeeded) {
            Deque<String> progressStack = activeProgress.get();
            String topName;
            while ((topName = progressStack.pollFirst()) != null) {
                if (topName.equals(name)) {
                    break;
                }
            }
            if (progressStack.isEmpty()) {
                name = namePrefix + name;
                activeProgress.remove(); // clear memory
            }
            return original.jobEnd(name, succeeded);
        }

        @Override
        public boolean jobIsCanceled(String name) {
            if (activeProgress.get().size() == 1) {
                name = namePrefix + name;
            }
            return original.jobIsCanceled(name);
        }

        @Override
        public void jobTodo(String name, int work) {
            if (activeProgress.get().size() == 1) {
                name = namePrefix + name;
            }
            original.jobTodo(name, work);
        }

        @Override
        public void warning(String message, ISourceLocation src) {
            original.warning(message, src);
        }

    }

    public InterpretedLanguageContributions(LanguageParameter lang, IBaseTextDocumentService docService, BaseWorkspaceService workspaceService, IBaseLanguageClient client, ExecutorService exec) {
        this.name = lang.getName();
        this.mainModule = lang.getMainModule();
        this.exec = exec;

        try {
            PathConfig pcfg = new PathConfig().parse(lang.getPathConfig());

            this.eval =
                EvaluatorUtil.makeFutureEvaluator(exec, docService, workspaceService, client, "evaluator for " + lang.getName(), pcfg, false, lang.getMainModule())
                .thenApply(e -> {
                    e.setMonitor(new MonitorWrapper(e.getMonitor(), lang.getName()));
                    return e;
                });
            var contributions = EvaluatorUtil.runEvaluator(name + ": loading contributions", eval,
                e -> loadContributions(e, lang),
                ValueFactoryFactory.getValueFactory().set(),
                exec).get();
            this.store = eval.thenApply(e -> ((ModuleEnvironment)e.getModule(mainModule)).getStore());
            this.parser = getFunctionFor(contributions, LanguageContributions.PARSER);
            this.outliner = getFunctionFor(contributions, LanguageContributions.OUTLINER);
            this.analyzer = getFunctionFor(contributions, LanguageContributions.ANALYZER);
            this.builder = getFunctionFor(contributions, LanguageContributions.BUILDER);
            this.lenses = getFunctionFor(contributions, LanguageContributions.LENS_DETECTOR);
            this.commandExecutor = getFunctionFor(contributions, LanguageContributions.COMMAND_EXECUTOR);
            this.inlayHinter = getFunctionFor(contributions, LanguageContributions.INLAY_HINTER);
            this.documenter = getFunctionFor(contributions, LanguageContributions.DOCUMENTER);
            this.definer = getFunctionFor(contributions, LanguageContributions.DEFINER);
            this.referrer = getFunctionFor(contributions, LanguageContributions.REFERRER);
            this.implementer = getFunctionFor(contributions, LanguageContributions.IMPLEMENTER);
            this.codeActionContributor = getFunctionFor(contributions, LanguageContributions.CODE_ACTION_CONTRIBUTOR);

            // assign boolean properties once instead of wasting futures all the time
            this.hasOutliner = nonNull(this.outliner);
            this.hasAnalyzer = nonNull(this.analyzer);
            this.hasBuilder = nonNull(this.builder);
            this.hasLensDetector = nonNull(this.lenses);
            this.hasCommandExecutor = nonNull(this.commandExecutor);
            this.hasInlayHinter = nonNull(this.inlayHinter);
            this.hasDocumenter = nonNull(this.documenter);
            this.hasDefiner = nonNull(this.definer);
            this.hasReferrer = nonNull(this.referrer);
            this.hasImplementer = nonNull(this.implementer);
            this.hasCodeActionContributor = nonNull(this.codeActionContributor);

            this.analyzerSummaryConfig = scheduledSummaryConfig(contributions, LanguageContributions.ANALYZER);
            this.builderSummaryConfig = scheduledSummaryConfig(contributions, LanguageContributions.BUILDER);
            this.ondemandSummaryConfig = ondemandSummaryConfig(contributions);

        } catch (IOException e1) {
            logger.catching(e1);
            throw new RuntimeException(e1);
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
                    isTrue(constructor, LanguageContributions.Summarizers.PROVIDES_DOCUMENTATION),
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
                hasContribution(c, LanguageContributions.DOCUMENTER),
                hasContribution(c, LanguageContributions.DEFINER),
                hasContribution(c, LanguageContributions.REFERRER),
                hasContribution(c, LanguageContributions.IMPLEMENTER)));
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
    public CompletableFuture<IList> parseCommands(String command) {
        return store.thenApply(commandStore -> {
            try {
                var TF = TypeFactory.getInstance();
                return (IList) new StandardTextReader().read(VF, commandStore, TF.listType(commandStore.lookupAbstractDataType("Command")), new StringReader(command));
            } catch (FactTypeUseException | IOException e) {
                logger.catching(e);
                return VF.list();
            }
        });
    }

    private CompletableFuture<IConstructor> parseCommand(String command) {
        return store.thenApply(commandStore -> {
            try {
                return (IConstructor) new StandardTextReader().read(VF, commandStore, commandStore.lookupAbstractDataType("Command"), new StringReader(command));
            } catch (FactTypeUseException | IOException e) {
               throw new RuntimeException(e);
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
    public CompletableFuture<ITree> parseSourceFile(ISourceLocation loc, String input) {
        return parser.thenApplyAsync(p -> p.call(VF.string(input), loc), exec);
    }

    @Override
    public InterruptibleFuture<IList> outline(ITree input) {
        debug(LanguageContributions.OUTLINER, TreeAdapter.getLocation(input));
        return execFunction(LanguageContributions.OUTLINER, outliner, VF.list(), input);
    }

    @Override
    public InterruptibleFuture<IConstructor> analyze(ISourceLocation src, ITree input) {
        debug(LanguageContributions.ANALYZER, src);
        return execFunction(LanguageContributions.ANALYZER, analyzer, EmptySummary.newInstance(src), src, input);
    }

    @Override
    public InterruptibleFuture<IConstructor> build(ISourceLocation src, ITree input) {
        debug(LanguageContributions.BUILDER, src);
        return execFunction(LanguageContributions.BUILDER, builder, EmptySummary.newInstance(src), src, input);
    }

    @Override
    public InterruptibleFuture<ISet> lenses(ITree input) {
        debug(LanguageContributions.LENS_DETECTOR, TreeAdapter.getLocation(input));
        return execFunction(LanguageContributions.LENS_DETECTOR, lenses, VF.set(), input);
    }

    @Override
    public InterruptibleFuture<IList> inlayHint(@Nullable ITree input) {
        debug(LanguageContributions.INLAY_HINTER, input != null ? TreeAdapter.getLocation(input) : null);
        return execFunction(LanguageContributions.INLAY_HINTER, inlayHinter, VF.list(), input);
    }

    @Override
    public InterruptibleFuture<ISet> documentation(ISourceLocation loc, ITree input, ITree cursor) {
        debug(LanguageContributions.DOCUMENTER, TreeAdapter.getLocation(cursor));
        return execFunction(LanguageContributions.DOCUMENTER, documenter, VF.set(), loc, input, cursor);
    }

    @Override
    public InterruptibleFuture<ISet> definitions(ISourceLocation loc, ITree input, ITree cursor) {
        debug(LanguageContributions.DEFINER, loc, cursor != null ?  TreeAdapter.getLocation(cursor) : null);
        return execFunction(LanguageContributions.DEFINER, definer, VF.set(), loc, input, cursor);
    }

    @Override
    public InterruptibleFuture<ISet> implementations(ISourceLocation loc, ITree input, ITree cursor) {
        debug(LanguageContributions.IMPLEMENTER, TreeAdapter.getLocation(cursor));
        return execFunction(LanguageContributions.IMPLEMENTER, implementer, VF.set(), loc, input, cursor);
    }

    @Override
    public InterruptibleFuture<ISet> references(ISourceLocation loc, ITree input, ITree cursor) {
        debug(LanguageContributions.REFERRER, TreeAdapter.getLocation(cursor));
        return execFunction(LanguageContributions.REFERRER, referrer, VF.set(), loc, input, cursor);
    }

    @Override
    public InterruptibleFuture<IList> codeActions(IList focus) {
        debug(LanguageContributions.CODE_ACTION_CONTRIBUTOR, "(focus list has " + focus.length() + " elements)");
        return execFunction(LanguageContributions.CODE_ACTION_CONTRIBUTOR, codeActionContributor, VF.list(), focus);
    }

    private void debug(String name, Object param) {
        logger.debug("{}({})", name, param);
    }

    private void debug(String name, Object param1, Object param2) {
        logger.debug("{}({}, {})", name, param1, param2);
    }

    @Override
    public CompletableFuture<Boolean> hasDefiner() {
        return hasDefiner;
    }

    @Override
    public CompletableFuture<Boolean> hasReferrer() {
        return hasReferrer;
    }

    @Override
    public CompletableFuture<Boolean> hasImplementer() {
        return hasImplementer;
    }

    @Override
    public CompletableFuture<Boolean> hasDocumenter() {
        return hasDocumenter;
    }

    @Override
    public CompletableFuture<Boolean> hasCommandExecutor() {
        return hasCommandExecutor;
    }

    @Override
    public CompletableFuture<Boolean> hasInlayHinter() {
        return hasInlayHinter;
    }

    @Override
    public CompletableFuture<Boolean> hasLensDetector() {
        return hasLensDetector;
    }

    @Override
    public CompletableFuture<Boolean> hasOutliner() {
        return hasOutliner;
    }

    @Override
    public CompletableFuture<Boolean> hasCodeActionsContributor() {
        return hasCodeActionContributor;
    }

    @Override
    public CompletableFuture<Boolean> hasAnalyzer() {
        return hasAnalyzer;
    }

    @Override
    public CompletableFuture<Boolean> hasBuilder() {
        return hasBuilder;
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
    public InterruptibleFuture<@Nullable IValue> executeCommand(String command) {
        logger.debug("executeCommand({}...) (full command value in TRACE level)", () -> command.substring(0, Math.min(10, command.length())));
        logger.trace("Full command: {}", command);
        return InterruptibleFuture.flatten(parseCommand(command).thenCombine(commandExecutor,
            (cons, func) -> EvaluatorUtil.<@Nullable IValue>runEvaluator("executeCommand", eval, ev -> func.call(cons), null, exec)
        ), exec);
    }

    private <T> InterruptibleFuture<T> execFunction(String name, CompletableFuture<@Nullable IFunction> target, T defaultResult, IValue... args) {
        return InterruptibleFuture.flatten(target.thenApply(
            s -> {
                if (s == null) {
                    return InterruptibleFuture.completedFuture(defaultResult);
                }

                return EvaluatorUtil.runEvaluator(name, eval, e -> s.call(args), defaultResult, exec);
            }),
            exec);
    }
}
