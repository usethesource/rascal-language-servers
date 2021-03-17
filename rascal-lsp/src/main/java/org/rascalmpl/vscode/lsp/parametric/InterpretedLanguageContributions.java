package org.rascalmpl.vscode.lsp.parametric;

import java.io.ByteArrayInputStream;
import java.util.Optional;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.io.IoBuilder;
import org.rascalmpl.interpreter.Evaluator;
import org.rascalmpl.library.util.PathConfig;
import org.rascalmpl.shell.ShellEvaluatorFactory;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.values.functions.IFunction;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.vscode.lsp.rascal.RascalLanguageServer;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.LanguageParameter;
import org.rascalmpl.vscode.lsp.util.LoggingMonitor;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IValueFactory;

public class InterpretedLanguageContributions implements ILanguageContributions {
    private static final Logger logger = LogManager.getLogger(InterpretedLanguageContributions.class);
    private final Evaluator eval;
    private final IValueFactory VF;
    private final String name;

    private Optional<IFunction> parser = Optional.empty();

    public InterpretedLanguageContributions(LanguageParameter lang) {
        this.name = lang.getName();
        this.eval = makeEvaluator(lang);
        this.VF = eval.getValueFactory();

        loadContributions(eval, lang);
    }

    private void loadContributions(Evaluator eval, LanguageParameter lang) {
        try {
            ISet contribs = (ISet) eval.eval(null, lang.getMainFunction() + "()", URIUtil.rootLocation("lsp")).getValue();

            for (IValue elem : contribs) {
                IConstructor contrib = (IConstructor) elem;
                switch (contrib.getConstructorType().getName()) {
                    case "parser":
                    this.parser = Optional.of((IFunction) contrib.get(0));
                }
            }
        }
        catch (Throwable e) {
            logger.catching(e);
            logger.error("failed to load contributions for {}", lang.getName());
        }
    }

    private Evaluator makeEvaluator(LanguageParameter lang) {
        Logger customLog = LogManager.getLogger("Evaluator for language: " + lang.getName());
        customLog.debug("Creating evaluator for language: {}", lang.getName());

        Evaluator eval = ShellEvaluatorFactory.getDefaultEvaluator(
            new ByteArrayInputStream(new byte[0]),
            IoBuilder.forLogger(customLog).setLevel(Level.INFO).buildOutputStream(),
            IoBuilder.forLogger(customLog).setLevel(Level.ERROR).buildOutputStream());

        eval.setMonitor(new LoggingMonitor(customLog));

        eval.getConfiguration().setRascalJavaClassPathProperty(System.getProperty("rascal.compilerClasspath"));
        eval.addClassLoader(RascalLanguageServer.class.getClassLoader());
        eval.addClassLoader(IValue.class.getClassLoader());
        eval.addRascalSearchPath(URIUtil.correctLocation("lib", "rascal-lsp", ""));

        try {
            PathConfig pcfg = new PathConfig().parse(lang.getPathConfig());

            for (IValue src : pcfg.getSrcs()) {
                eval.addRascalSearchPath((ISourceLocation) src);
            }

            eval.doImport(eval, lang.getMainModule());

            return eval;
        } catch (Exception e) {
            customLog.catching(e);
            customLog.error("Failed to import: {}", lang.getMainModule());
            throw new RuntimeException("Failure to import required module " + lang.getMainModule(), e);
        }
    }
    
    @Override
    public ITree parseSourceFile(ISourceLocation loc, String input) {
        if (parser.isPresent()) {
            synchronized (eval) {
                return parser.get().call(VF.string(input), loc);
            }
        }
        else {
            throw new UnsupportedOperationException("no parser is registered for " + name);
        }
    }
}
