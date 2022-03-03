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
import org.rascalmpl.debug.IRascalMonitor;
import org.rascalmpl.exceptions.Throw;
import org.rascalmpl.interpreter.Evaluator;
import org.rascalmpl.interpreter.control_exceptions.InterruptException;
import org.rascalmpl.library.util.PathConfig;
import org.rascalmpl.shell.ShellEvaluatorFactory;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.vscode.lsp.BaseWorkspaceService;
import org.rascalmpl.vscode.lsp.IBaseLanguageClient;
import org.rascalmpl.vscode.lsp.IBaseTextDocumentService;
import org.rascalmpl.vscode.lsp.LSPIDEServices;
import org.rascalmpl.vscode.lsp.rascal.RascalLanguageServer;
import org.rascalmpl.vscode.lsp.util.concurrent.InterruptibleFuture;

import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValue;

public class EvaluatorUtil {
    private static final Logger logger = LogManager.getLogger(EvaluatorUtil.class);

    public static <T> InterruptibleFuture<T> runEvaluator(String task, CompletableFuture<Evaluator> eval, Function<Evaluator, T> call, T defaultResult, Executor exec, boolean throwFailure) {
        AtomicBoolean interrupted = new AtomicBoolean(false);
        AtomicReference<@Nullable Evaluator> runningEvaluator = new AtomicReference<>(null);
        return new InterruptibleFuture<>(eval.thenApplyAsync(actualEval -> {
            try {
                actualEval.jobStart(task);
                synchronized (actualEval) {
                    boolean jobSuccess = false;
                    try {
                        runningEvaluator.set(actualEval);
                        if (interrupted.get()) {
                            return defaultResult;
                        }
                        T result = call.apply(actualEval);
                        jobSuccess = true;
                        return result;
                    } catch (InterruptException e) {
                        return defaultResult;
                    } finally {
                        actualEval.jobEnd(task, jobSuccess);
                        actualEval.__setInterrupt(false);
                        runningEvaluator.set(null);
                    }
                }
            } catch (Throw e) {
                logger.error("Internal error during {}\n{}: {}\n{}", task, e.getLocation(), e.getMessage(),
                        e.getTrace());
                logger.error("Full internal error: ", e);
                if (throwFailure) {
                    throw e;
                }
                return defaultResult;
            } catch (Throwable e) {
                logger.error("{} failed", task, e);
                if (throwFailure) {
                    throw e;
                }
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

    public static CompletableFuture<Evaluator> makeFutureEvaluator(ExecutorService exec, IBaseTextDocumentService docService, BaseWorkspaceService workspaceService, IBaseLanguageClient client, String label, PathConfig pcfg, final String... imports) {
        return CompletableFuture.supplyAsync(() -> {
            Logger customLog = LogManager.getLogger("Evaluator: " + label);
            IRascalMonitor monitor = new LSPIDEServices(client, docService, workspaceService, customLog);
            boolean jobSuccess = false;
            try {
                monitor.jobStart("Loading " + label);
                Evaluator eval = ShellEvaluatorFactory.getDefaultEvaluator(new ByteArrayInputStream(new byte[0]),
                        IoBuilder.forLogger(customLog).setLevel(Level.INFO).buildOutputStream(),
                        IoBuilder.forLogger(customLog).setLevel(Level.ERROR).buildOutputStream());
                eval.setMonitor(monitor);

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
                        logger.catching(e);
                        throw new RuntimeException("Failure to import required module " + i, e);
                    }
                }
                jobSuccess = true;
                return eval;
            } finally {
                monitor.jobEnd("Loading " + label, jobSuccess);
            }
        }, exec);
    }
}
