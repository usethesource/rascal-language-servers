/*
 * Copyright (c) 2018-2021, NWO-I CWI and Swat.engineering
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
import org.rascalmpl.vscode.lsp.IBaseLanguageClient;
import org.rascalmpl.vscode.lsp.IBaseTextDocumentService;
import org.rascalmpl.vscode.lsp.parametric.model.ParametricSummaryBridge;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.LanguageParameter;
import org.rascalmpl.vscode.lsp.util.EvaluatorUtil;
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
    private final String extension;
    private final String mainModule;

    private final CompletableFuture<Evaluator> eval;
    private final CompletableFuture<TypeStore> store;
    private final CompletableFuture<IFunction> parser;
    private final CompletableFuture<@Nullable IFunction> outliner;
    private final CompletableFuture<@Nullable IFunction> summarizer;
    private final CompletableFuture<@Nullable IFunction> lenses;
    private final CompletableFuture<@Nullable IFunction> commandExecutor;
    private final CompletableFuture<@Nullable IFunction> inlayHinter;


    public InterpretedLanguageContributions(LanguageParameter lang, IBaseTextDocumentService docService, IBaseLanguageClient client, ExecutorService exec) {
        this.name = lang.getName();
        this.mainModule = lang.getMainModule();
        extension = lang.getExtension();
        this.exec = exec;

        try {
            PathConfig pcfg = new PathConfig().parse(lang.getPathConfig());

            this.eval =
                EvaluatorUtil.makeFutureEvaluator(exec, docService, client, "evaluator for " + lang.getName(), pcfg, lang.getMainModule());
            CompletableFuture<ISet> contributions = EvaluatorUtil.runEvaluator("load contributions", eval,
                e -> loadContributions(e, lang),
                ValueFactoryFactory.getValueFactory().set(),
                exec).get();
            this.store = eval.thenApply(e -> ((ModuleEnvironment)e.getModule(mainModule)).getStore());
            this.parser = contributions.thenApply(s -> getFunctionFor(s, "parser"));
            this.outliner = contributions.thenApply(s -> getFunctionFor(s, "outliner"));
            this.summarizer = contributions.thenApply(s -> getFunctionFor(s, "summarizer"));
            this.lenses = contributions.thenApply(s -> getFunctionFor(s, "lenses"));
            this.commandExecutor = contributions.thenApply(s -> getFunctionFor(s, "executor"));
            this.inlayHinter = contributions.thenApply(s -> getFunctionFor(s, "inlayHinter"));
        } catch (IOException e1) {
            logger.catching(e1);
            throw new RuntimeException(e1);
        }
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

    private static @Nullable IFunction getFunctionFor(ISet contributions, String cons) {
        for (IValue elem : contributions) {
            IConstructor contrib = (IConstructor) elem;
            if (cons.equals(contrib.getConstructorType().getName())) {
                return (IFunction) contrib.get(0);
            }
        }
        logger.debug("No {} defined", cons);
        return null;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getExtension() {
        return extension;
    }

    @Override
    public CompletableFuture<ITree> parseSourceFile(ISourceLocation loc, String input) {
        return parser.thenApplyAsync(p -> p.call(VF.string(input), loc), exec);
    }

    @Override
    public CompletableFuture<IList> outline(ITree input) {
        logger.debug("outline({})", TreeAdapter.getLocation(input));
        return execFunction("outline",outliner, VF.list(), input);
    }

    @Override
    public CompletableFuture<IConstructor> summarize(ISourceLocation src, ITree input) {
        logger.debug("summarize({})", src);
        return execFunction("summarize", summarizer,
            ParametricSummaryBridge.emptySummary(src), src, input);
    }

    @Override
    public CompletableFuture<ISet> lenses(ITree input) {
        logger.debug("lenses({})", TreeAdapter.getLocation(input));
        return execFunction("lenses", lenses, VF.set(), input);
    }

    @Override
    public CompletableFuture<IList> inlayHint(ITree input) {
        logger.debug("inlayHinter({})", TreeAdapter.getLocation(input));
        return execFunction("inlayHinter", inlayHinter, VF.list(), input);
    }

    @Override
    public CompletableFuture<Void> executeCommand(String command) {
        logger.debug("executeCommand({})", command);
        return parseCommand(command)
            .thenCombineAsync(commandExecutor, (c,e) -> {
                if (e != null) {
                    e.call(c);
                }
                return null;
            },exec)
            .handle((r,e) -> {
                logger.catching(e);
                return null;
            });
    }

    private <T> CompletableFuture<T> execFunction(String name, CompletableFuture<@Nullable IFunction> target, T defaultResult, IValue... args) {
        return target.thenApplyAsync(s -> {
                if (s == null) {
                    logger.trace("Not running {} since it's not defined for: {}", name, this.name);
                    return defaultResult;
                }
                return s.call(args);
        }, exec);
    }

}
