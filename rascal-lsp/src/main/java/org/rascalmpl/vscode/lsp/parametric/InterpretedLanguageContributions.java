package org.rascalmpl.vscode.lsp.parametric;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
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
import io.usethesource.vallang.type.Type;
import io.usethesource.vallang.type.TypeFactory;
import io.usethesource.vallang.type.TypeStore;

public class InterpretedLanguageContributions implements ILanguageContributions {
    private static final IValueFactory VF = IRascalValueFactory.getInstance();
    private static final Logger logger = LogManager.getLogger(InterpretedLanguageContributions.class);
    private final CompletableFuture<Evaluator> eval;
    private final ExecutorService exec;

    private final String name;

    private @MonotonicNonNull IFunction parser;
    private @MonotonicNonNull IFunction outliner;
    private @MonotonicNonNull IFunction summarizer;
    
    private final TypeFactory TF = TypeFactory.getInstance();
    private final TypeStore TS = new TypeStore();
    private final Type summaryType = TF.abstractDataType(TS, "Summary");
    private final Type summaryConstructor = TF.constructor(TS, summaryType, "summary", TF.sourceLocationType(), "src");

    public InterpretedLanguageContributions(LanguageParameter lang, ExecutorService exec) {
        this.name = lang.getName();
        this.exec = exec;

        try {
            PathConfig pcfg = new PathConfig().parse(lang.getPathConfig());
            this.eval = EvaluatorUtil.makeFutureEvaluator(exec, "evaluator for " + lang.getName(), pcfg, lang.getMainModule())
                .thenApply(eval -> {
                    loadContributions(eval, lang);
                    return eval;
                });
        } catch (IOException e) {
            logger.catching(e);
            throw new RuntimeException(e);
        }
    }

    private void loadContributions(Evaluator eval, LanguageParameter lang) {
        try {
            ISet contribs = (ISet) eval.eval(null, lang.getMainFunction() + "()", URIUtil.rootLocation("lsp")).getValue();

            for (IValue elem : contribs) {
                IConstructor contrib = (IConstructor) elem;
                switch (contrib.getConstructorType().getName()) {
                    case "parser":
                        this.parser = (IFunction) contrib.get(0);
                        break;
                    case "outliner":
                        this.outliner = (IFunction) contrib.get(0);
                        break;
                    case "summarizer":
                        this.summarizer = (IFunction) contrib.get(0);
                        break;
                    default:
                        logger.warn("Contribution is not implemented yet: " + contrib); 
                }
            }
        }
        catch (Throwable e) {
            logger.catching(e);
            logger.error("failed to load contributions for {}", lang.getName());
        }
    }

    public String getName() {
        return name;
    }
    
    @Override
    public CompletableFuture<ITree> parseSourceFile(ISourceLocation loc, String input) {
        if (parser != null) {
            return eval.thenApply(e -> parser.call(VF.string(input), loc));
        }
        else {
            return CompletableFuture.supplyAsync(() -> {
                throw new UnsupportedOperationException("no parser is registered for " + name);
            });
        }
    }

    @Override
    public CompletableFuture<IList> outline(ITree input) {
        if (outliner != null) {
            return eval.thenApply(e -> outliner.call(input));
        }
        else {
            return CompletableFuture.supplyAsync(() -> {
                throw new UnsupportedOperationException("no outliner is registered for " + name);
            });
        }
    }

    @Override
    public InterruptibleFuture<IConstructor> summarize(ISourceLocation src, ITree input) {
        if (summarizer != null) {
            logger.trace("summarize({})", src);
            return EvaluatorUtil.runEvaluator(
                "summarize", 
                eval, 
                eval -> summarizer.call(src, input), 
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
