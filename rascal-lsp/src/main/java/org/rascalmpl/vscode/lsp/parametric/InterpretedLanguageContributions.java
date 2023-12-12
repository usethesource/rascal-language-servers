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
import org.rascalmpl.vscode.lsp.parametric.model.ParametricSummaryBridge;
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
    private final CompletableFuture<@Nullable IFunction> summarizer;
    private final CompletableFuture<@Nullable IFunction> lenses;
    private final CompletableFuture<@Nullable IFunction> commandExecutor;
    private final CompletableFuture<@Nullable IFunction> inlayHinter;
    private final CompletableFuture<@Nullable IFunction> documenter;
    private final CompletableFuture<@Nullable IFunction> definer;
    private final CompletableFuture<@Nullable IFunction> referrer;
    private final CompletableFuture<@Nullable IFunction> implementer;

    private final CompletableFuture<Boolean> hasOutliner;
    private final CompletableFuture<Boolean> hasSummarizer;
    private final CompletableFuture<Boolean> hasLenses;
    private final CompletableFuture<Boolean> hasCommandExecutor;
    private final CompletableFuture<Boolean> hasInlayHinter;
    private final CompletableFuture<Boolean> hasDocumenter;
    private final CompletableFuture<Boolean> hasDefiner;
    private final CompletableFuture<Boolean> hasReferrer;
    private final CompletableFuture<Boolean> hasImplementer;

    private final CompletableFuture<Boolean> summaryProvidesDefinitions;
    private final CompletableFuture<Boolean> summaryProvidesReferences;
    private final CompletableFuture<Boolean> summaryProvidesImplementations;
    private final CompletableFuture<Boolean> summaryProvidesDocumentation;


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
                exec, true).get();
            this.store = eval.thenApply(e -> ((ModuleEnvironment)e.getModule(mainModule)).getStore());
            this.parser = getFunctionFor(contributions, "parser");
            this.outliner = getFunctionFor(contributions, "outliner");
            this.summarizer = getFunctionFor(contributions, "summarizer");
            this.lenses = getFunctionFor(contributions, "lenses");
            this.commandExecutor = getFunctionFor(contributions, "executor");
            this.inlayHinter = getFunctionFor(contributions, "inlayHinter");
            this.documenter = getFunctionFor(contributions, "documenter");
            this.definer = getFunctionFor(contributions, "definer");
            this.referrer = getFunctionFor(contributions, "referrer");
            this.implementer = getFunctionFor(contributions, "implementer");
            var summaryConfig = contributions.thenApply(c -> c.stream()
                    .map(IConstructor.class::cast)
                    .filter(cons -> cons.getConstructorType().getName().equals("summarizer"))
                    .findAny()
                    .orElse(null)
            );


            // assign boolean properties once instead of wasting futures all the time
            this.hasOutliner = nonNull(this.outliner);
            this.hasSummarizer = nonNull(this.summarizer);
            this.hasLenses = nonNull(this.lenses);
            this.hasCommandExecutor = nonNull(this.commandExecutor);
            this.hasInlayHinter = nonNull(this.inlayHinter);
            this.hasDocumenter = nonNull(this.documenter);
            this.hasDefiner = nonNull(this.definer);
            this.hasReferrer = nonNull(this.referrer);
            this.hasImplementer = nonNull(this.implementer);
            this.summaryProvidesDefinitions = summaryConfigLookup(summaryConfig, "providesDefinitions");
            this.summaryProvidesDocumentation = summaryConfigLookup(summaryConfig, "providesDocumentation");
            this.summaryProvidesImplementations = summaryConfigLookup(summaryConfig, "providesImplementations");
            this.summaryProvidesReferences = summaryConfigLookup(summaryConfig, "providesReferences");

        } catch (IOException e1) {
            logger.catching(e1);
            throw new RuntimeException(e1);
        }
    }

    private static CompletableFuture<Boolean> nonNull(CompletableFuture<?> x) {
        return x.thenApply(Objects::nonNull);
    }

    private static CompletableFuture<Boolean> summaryConfigLookup(CompletableFuture<IConstructor> conf, String parameter) {
        return conf.thenApply( d -> {
            if (d == null) {
                return false;
            }
            var val = d.asWithKeywordParameters().getParameter(parameter);
            return !(val instanceof IBool) || ((IBool)val).getValue();
        });
    }

    private static ISet loadContributions(Evaluator eval, LanguageParameter lang) {
        return (ISet) eval.eval(eval.getMonitor(), lang.getMainFunction() + "()", URIUtil.rootLocation("lsp"))
            .getValue();
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
        logger.debug("outline({})", TreeAdapter.getLocation(input));
        return execFunction("outline",outliner, VF.list(), input);
    }

    @Override
    public InterruptibleFuture<IConstructor> summarize(ISourceLocation src, ITree input) {
        logger.debug("summarize({})", src);
        return execFunction("summarize", summarizer,
            ParametricSummaryBridge.emptySummary(src), src, input);
    }

    @Override
    public InterruptibleFuture<ISet> lenses(ITree input) {
        logger.debug("lenses({})", TreeAdapter.getLocation(input));
        return execFunction("lenses", lenses, VF.set(), input);
    }

    @Override
    public InterruptibleFuture<IList> inlayHint(@Nullable ITree input) {
        logger.debug("inlayHinter({})", input != null ? TreeAdapter.getLocation(input) : null);
        return execFunction("inlayHinter", inlayHinter, VF.list(), input);
    }

    @Override
    public InterruptibleFuture<ISet> documentation(ISourceLocation loc, ITree input, ITree cursor) {
        logger.debug("documentation({})", TreeAdapter.getLocation(cursor));
        return execFunction("", documenter, VF.set(), loc, input, cursor);
    }

    @Override
    public InterruptibleFuture<ISet> defines(ISourceLocation loc, ITree input, ITree cursor) {
        logger.debug("defines({}, {})", loc, cursor != null ?  TreeAdapter.getLocation(cursor) : null);
        return execFunction("defines", definer, VF.set(), loc, input, cursor);
    }

    @Override
    public InterruptibleFuture<ISet> implementations(ISourceLocation loc, ITree input, ITree cursor) {
        logger.debug("implementer({})", TreeAdapter.getLocation(cursor));
        return execFunction("implementer", implementer, VF.set(), loc, input, cursor);
    }
    @Override
    public InterruptibleFuture<ISet> references(ISourceLocation loc, ITree input, ITree cursor) {
        logger.debug("references({})", TreeAdapter.getLocation(cursor));
        return execFunction("references", referrer, VF.set(), loc, input, cursor);
    }


    @Override
    public CompletableFuture<Boolean> hasDedicatedDefines() {
        return hasDefiner;
    }

    @Override
    public CompletableFuture<Boolean> hasDedicatedReferences() {
        return hasReferrer;
    }

    @Override
    public CompletableFuture<Boolean> hasDedicatedImplementations() {
        return hasImplementer;
    }

    @Override
    public CompletableFuture<Boolean> hasDedicatedDocumentation() {
        return hasDocumenter;
    }

    @Override
    public CompletableFuture<Boolean> hasExecuteCommand() {
        return hasCommandExecutor;
    }

    @Override
    public CompletableFuture<Boolean> hasInlayHint() {
        return hasInlayHinter;
    }

    @Override
    public CompletableFuture<Boolean> hasLenses() {
        return hasLenses;
    }

    @Override
    public CompletableFuture<Boolean> hasOutline() {
        return hasOutliner;
    }

    @Override
    public CompletableFuture<Boolean> hasSummarize() {
        return hasSummarizer;
    }



    @Override
    public CompletableFuture<Boolean> askSummaryForDefinitions() {
        return summaryProvidesDefinitions;
    }

    @Override
    public CompletableFuture<Boolean> askSummaryForReferences() {
        return summaryProvidesReferences;
    }


    @Override
    public CompletableFuture<Boolean> askSummaryForImplementations() {
        return summaryProvidesImplementations;
    }

    @Override
    public CompletableFuture<Boolean> askSummaryForDocumentation() {
        return summaryProvidesDocumentation;
    }


    @Override
    public InterruptibleFuture<@Nullable IValue> executeCommand(String command) {
        logger.debug("executeCommand({}...) (full command value in TRACE level)", () -> command.substring(0, Math.min(10, command.length())));
        logger.trace("Full command: {}", command);
        return InterruptibleFuture.flatten(parseCommand(command).thenCombine(commandExecutor,
            (cons, func) -> EvaluatorUtil.<@Nullable IValue>runEvaluator("executeCommand", eval, ev -> func.call(cons), null, exec, false)
        ), exec);
    }

    private <T> InterruptibleFuture<T> execFunction(String name, CompletableFuture<@Nullable IFunction> target, T defaultResult, IValue... args) {
        return InterruptibleFuture.flatten(target.thenApply(
            s -> {
                if (s == null) {
                    return InterruptibleFuture.completedFuture(defaultResult);
                }

                return EvaluatorUtil.runEvaluator(name, eval, e -> s.call(args), defaultResult, exec, false);
            }),
            exec);
    }


}
