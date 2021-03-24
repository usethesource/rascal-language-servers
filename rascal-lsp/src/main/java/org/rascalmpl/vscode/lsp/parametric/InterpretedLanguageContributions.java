package org.rascalmpl.vscode.lsp.parametric;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.rascalmpl.interpreter.Evaluator;
import org.rascalmpl.library.util.PathConfig;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.values.functions.IFunction;
import org.rascalmpl.values.parsetrees.ITree;
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

public class InterpretedLanguageContributions implements ILanguageContributions {
    private static final IValueFactory VF = IRascalValueFactory.getInstance();
    private static final Logger logger = LogManager.getLogger(InterpretedLanguageContributions.class);

    private final ExecutorService exec;

    private final String name;

    private final CompletableFuture<Evaluator> eval;
    private final CompletableFuture<IFunction> parser;
    private final CompletableFuture<IFunction> outliner;
    private final CompletableFuture<IFunction> summarizer;

    public InterpretedLanguageContributions(LanguageParameter lang, ExecutorService exec) {
        this.name = lang.getName();
        this.exec = exec;

        try {
            PathConfig pcfg = new PathConfig().parse(lang.getPathConfig());
            this.eval = EvaluatorUtil.makeFutureEvaluator(exec, "evaluator for " + lang.getName(), pcfg, lang.getMainModule());
            CompletableFuture<ISet> contributions = eval.thenApply(e -> loadContributions(e, lang));
            this.parser = contributions.thenApply(s -> getFunctionFor(s, "parser"));
            this.outliner = contributions.thenApply(s -> getFunctionFor(s, "outliner"));
            this.summarizer = contributions.thenApply(s -> getFunctionFor(s, "summarizer"));
        }
        catch (IOException e1) {
            logger.catching(e1);
            throw new RuntimeException(e1);
        }
    }

    private static ISet loadContributions(Evaluator eval, LanguageParameter lang) {
        return (ISet) eval.eval(null, lang.getMainFunction() + "()", URIUtil.rootLocation("lsp")).getValue();
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

    public String getName() {
        return name;
    }

    @Override
    public CompletableFuture<ITree> parseSourceFile(ISourceLocation loc, String input) {
        return parser.thenApply(p -> p.call(VF.string(input), loc))
            .handle((r,e) -> {
                logger.catching(e);
                throw new RuntimeException(e);
            });
    }

    @Override
    public CompletableFuture<IList> outline(ITree input) {
        return outliner.thenApply(o -> o.call(input))
        .handle((r,e) -> {
            logger.catching(e);
            throw new RuntimeException(e);
        });
    }

    @Override
    public InterruptibleFuture<IConstructor> summarize(ISourceLocation src, ITree input) {
        if (summarizer != null) {
            logger.trace("summarize({})", src);
            return EvaluatorUtil.runEvaluator(
                "summarize",
                eval,
                eval -> {
                    try {
                        return summarizer.get().call(src, input);
                    } catch (InterruptedException | ExecutionException e) {
                        logger.catching(e);
                        throw new RuntimeException(e);
                    }
                },
                ParametricSummaryBridge.emptySummary(src),
                exec);
        }
        else {
            return new InterruptibleFuture<>(CompletableFuture.supplyAsync(() -> {
                throw new UnsupportedOperationException("no summarizer is registered for " + name);
            }), () -> {});
        }
    }
}
