package org.rascalmpl.vscode.lsp.util;

import java.io.ByteArrayInputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.io.IoBuilder;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.rascalmpl.exceptions.Throw;
import org.rascalmpl.interpreter.Evaluator;
import org.rascalmpl.interpreter.control_exceptions.InterruptException;
import org.rascalmpl.library.util.PathConfig;
import org.rascalmpl.shell.ShellEvaluatorFactory;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.vscode.lsp.rascal.RascalLanguageServer;
import org.rascalmpl.vscode.lsp.util.concurrent.InterruptibleFuture;

import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValue;

public class EvaluatorUtil {
    private static final Logger logger = LogManager.getLogger(EvaluatorUtil.class);

    public static <T> InterruptibleFuture<T> runEvaluator(String task, Future<Evaluator> eval, Function<Evaluator, T> call, T defaultResult, Executor exec) {
        AtomicBoolean interrupted = new AtomicBoolean(false);
        AtomicReference<@Nullable Evaluator> runningEvaluator = new AtomicReference<>(null);
        return new InterruptibleFuture<>(CompletableFuture.supplyAsync(() -> {
            try {
                Evaluator actualEval = eval.get();

                synchronized (actualEval) {
                    try {
                        runningEvaluator.set(actualEval);
                        if (interrupted.get()) {
                            return defaultResult;
                        }
                        return call.apply(actualEval);
                    } catch (InterruptException e) {
                        return defaultResult;
                    } finally {
                        actualEval.__setInterrupt(false);
                        runningEvaluator.set(null);
                    }
                }
            } catch (Throw e) {
                logger.error("Internal error during {}\n{}: {}\n{}", task, e.getLocation(), e.getMessage(),
                        e.getTrace());
                logger.error("Full internal error: ", e);
                return defaultResult;
            } catch (Exception e) {
                logger.error(task + " failed", e);
                return defaultResult;
            }
        }, exec), () -> {
            interrupted.set(true);
            Evaluator actualEval = runningEvaluator.get();
            if (actualEval != null) {
                actualEval.interrupt();
            }
        });
    }

    public static CompletableFuture<Evaluator> makeFutureEvaluator(ExecutorService exec, String label, PathConfig pcfg, final String... imports) {
        return CompletableFuture.supplyAsync(() -> {
            Logger customLog = LogManager.getLogger("Evaluator: " + label);
            Evaluator eval = ShellEvaluatorFactory.getDefaultEvaluator(new ByteArrayInputStream(new byte[0]),
                    IoBuilder.forLogger(customLog).setLevel(Level.INFO).buildOutputStream(),
                    IoBuilder.forLogger(customLog).setLevel(Level.ERROR).buildOutputStream());
            eval.setMonitor(new LoggingMonitor(customLog));

            eval.getConfiguration().setRascalJavaClassPathProperty(System.getProperty("rascal.compilerClasspath"));
            eval.addClassLoader(RascalLanguageServer.class.getClassLoader());
            eval.addClassLoader(IValue.class.getClassLoader());
            eval.addRascalSearchPath(URIUtil.correctLocation("lib", "typepal", ""));
            eval.addRascalSearchPath(URIUtil.correctLocation("lib", "rascal-core", ""));
            eval.addRascalSearchPath(URIUtil.correctLocation("lib", "rascal-lsp", ""));

            if (pcfg != null) {
                for (IValue src : pcfg.getSrcs()) {
                    eval.addRascalSearchPath((ISourceLocation) src);
                }
            }
            
            for (String i : imports) {
                try {
                    eval.doImport(eval, i);
                } catch (Exception e) {
                    throw new RuntimeException("Failure to import required module " + i, e);
                }
            }

            return eval;
        }, exec);
    }
}