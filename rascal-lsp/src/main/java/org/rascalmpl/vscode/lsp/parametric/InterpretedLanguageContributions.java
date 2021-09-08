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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.rascalmpl.interpreter.Evaluator;
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
import org.rascalmpl.vscode.lsp.util.concurrent.InterruptibleFuture;

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

    private final CompletableFuture<Evaluator> eval;
    private final CompletableFuture<IFunction> parser;
    private final CompletableFuture<IFunction> outliner;
    private final CompletableFuture<IFunction> summarizer;
    private final CompletableFuture<IFunction> lenses;
    private final CompletableFuture<IFunction> commandExecutor;


    public InterpretedLanguageContributions(LanguageParameter lang, IBaseTextDocumentService docService, IBaseLanguageClient client, ExecutorService exec) {
        this.name = lang.getName();
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
            this.parser = contributions.thenApply(s -> getFunctionFor(s, "parser"));
            this.outliner = contributions.thenApply(s -> getFunctionFor(s, "outliner"));
            this.summarizer = contributions.thenApply(s -> getFunctionFor(s, "summarizer"));
            this.lenses = contributions.thenApply(s -> getFunctionFor(s, "lenses"));
            this.commandExecutor = contributions.thenApply(s -> getFunctionFor(s, "executor"));
        } catch (IOException e1) {
            logger.catching(e1);
            throw new RuntimeException(e1);
        }
    }

    private static ISet loadContributions(Evaluator eval, LanguageParameter lang) {
        return (ISet) eval.eval(eval.getMonitor(), lang.getMainFunction() + "()", URIUtil.rootLocation("lsp"))
            .getValue();
    }

    private static IConstructor parseCommand(Evaluator eval, String command) {
        TypeStore store = eval.getCurrentModuleEnvironment().getStore();

        try {
            return (IConstructor) new StandardTextReader().read(VF, store, store.lookupAbstractDataType("Command"), new StringReader(command));
        } catch (FactTypeUseException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static IFunction getFunctionFor(ISet contributions, String cons) {
        for (IValue elem : contributions) {
            IConstructor contrib = (IConstructor) elem;
            if (cons.equals(contrib.getConstructorType().getName())) {
                return (IFunction) contrib.get(0);
            }
        }

        throw new UnsupportedOperationException("no " + cons + " is available.");
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
    public InterruptibleFuture<ITree> parseSourceFile(ISourceLocation loc, String input) {
    AtomicBoolean interrupted = new AtomicBoolean(false);
    AtomicReference<Evaluator> currentEval = new AtomicReference<>(null);
    /* we want to pass along parse errors to the IDE,
        so we cannot use the normal exec function call,
        as that catches all exceptions */
    return new InterruptibleFuture<>(parser.thenCombineAsync(eval, (p, ev) -> {
            synchronized(ev) {
                try {
                    currentEval.set(ev);
                    if (interrupted.get()) {
                        throw new RuntimeException("Interrupted before finishing");
                    }
                    return (ITree)p.call(VF.string(input), loc);
                }
                finally {
                    currentEval.set(null);
                    ev.__setInterrupt(false);
                }
            }
        }, exec),
        () -> {
            interrupted.set(true);
            Evaluator e = currentEval.get();
            if (e != null) {
                e.interrupt();
            }
        });
    }

    @Override
    public InterruptibleFuture<IList> outline(ITree input) {
        return execFunction("outline",outliner, VF.list(), input);
    }

    @Override
    public InterruptibleFuture<IConstructor> summarize(ISourceLocation src, ITree input) {
        logger.trace("summarize({})", src);
        return execFunction("summarize", summarizer,
            ParametricSummaryBridge.emptySummary(src), src, input);
    }

    @Override
    public InterruptibleFuture<ISet> lenses(ITree input) {
        logger.trace("lensen({})", TreeAdapter.getLocation(input));
        return execFunction("lenses", lenses, VF.set(), input);
    }

    @Override
    public InterruptibleFuture<Void> executeCommand(String command) {
        logger.trace("executeCommand({})", command);
        return new InterruptibleFuture<>(eval.thenApply(e -> execFunction("executor", commandExecutor, null, parseCommand(e, command)))
            .handle((r,e) -> {
                logger.catching(e);
                return null;
            }), () -> {});
    }

    private <T> InterruptibleFuture<T> execFunction(String name, CompletableFuture<IFunction> target, T defaultResult, IValue... args) {
        return InterruptibleFuture.flatten(
            target.thenApply(s -> EvaluatorUtil.runEvaluator(name, eval,e -> s.call(args), defaultResult, exec))
        , exec);
    }
}
