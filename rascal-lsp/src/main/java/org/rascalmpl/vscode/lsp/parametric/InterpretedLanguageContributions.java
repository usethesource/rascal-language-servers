package org.rascalmpl.vscode.lsp.parametric;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.rascalmpl.interpreter.Evaluator;
import org.rascalmpl.library.util.PathConfig;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.values.functions.IFunction;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.LanguageParameter;
import org.rascalmpl.vscode.lsp.util.EvaluatorUtil;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IValueFactory;

public class InterpretedLanguageContributions implements ILanguageContributions {
    private static final IValueFactory VF = IRascalValueFactory.getInstance();
    private static final Logger logger = LogManager.getLogger(InterpretedLanguageContributions.class);
    private final CompletableFuture<Evaluator> eval;
    private final String name;

    private Optional<IFunction> parser = Optional.empty();
    private Optional<IFunction> outliner = Optional.empty();

    public InterpretedLanguageContributions(LanguageParameter lang) {
        this.name = lang.getName();

        try {
            PathConfig pcfg = new PathConfig().parse(lang.getPathConfig());
            this.eval = EvaluatorUtil.makeFutureEvaluator("evaluator for " + lang.getName(), pcfg, lang.getMainModule())
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
                        this.parser = Optional.of((IFunction) contrib.get(0));
                        break;
                    case "outliner":
                        this.outliner = Optional.of((IFunction) contrib.get(0));
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
        if (parser.isPresent()) {
            return eval.thenApply(e -> parser.get().call(VF.string(input), loc));
        }
        else {
            throw new UnsupportedOperationException("no parser is registered for " + name);
        }
    }

    @Override
    public CompletableFuture<IList> outline(ITree input) {
        if (outliner.isPresent()) {
            return eval.thenApply(e -> outliner.get().call(input));
        }
        else {
            throw new UnsupportedOperationException("no outliner is registered for " + name);
        }
    }
}
