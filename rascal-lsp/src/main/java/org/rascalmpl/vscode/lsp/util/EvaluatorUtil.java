/*
 * Copyright (c) 2018-2025, NWO-I CWI and Swat.engineering
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

import java.awt.AWTError;
import java.awt.Desktop;
import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.io.IoBuilder;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.lsp4j.services.LanguageClient;
import org.rascalmpl.debug.IRascalMonitor;
import org.rascalmpl.exceptions.Throw;
import org.rascalmpl.ideservices.IDEServices;
import org.rascalmpl.interpreter.Evaluator;
import org.rascalmpl.interpreter.control_exceptions.InterruptException;
import org.rascalmpl.interpreter.staticErrors.StaticError;
import org.rascalmpl.interpreter.utils.LimitedResultWriter;
import org.rascalmpl.library.util.PathConfig;
import org.rascalmpl.shell.ShellEvaluatorFactory;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.uri.classloaders.SourceLocationClassLoader;
import org.rascalmpl.uri.jar.JarURIResolver;
import org.rascalmpl.vscode.lsp.BaseWorkspaceService;
import org.rascalmpl.vscode.lsp.IBaseLanguageClient;
import org.rascalmpl.vscode.lsp.IBaseTextDocumentService;
import org.rascalmpl.vscode.lsp.LSPIDEServices;
import org.rascalmpl.vscode.lsp.rascal.RascalLanguageServer;
import org.rascalmpl.vscode.lsp.util.concurrent.InterruptibleFuture;
import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.io.StandardTextWriter;

public class EvaluatorUtil {
    private static final Logger logger = LogManager.getLogger(EvaluatorUtil.class);

    public static <T> InterruptibleFuture<T> runEvaluator(String task, CompletableFuture<Evaluator> eval, Function<Evaluator, T> call, T defaultResult, Executor exec, boolean isParametric, LanguageClient client) {
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
                        runningEvaluator.set(null);
                        actualEval.__setInterrupt(false);
                    }
                }
            }
            catch (Throw e) {
                logger.error("Internal error during {}\n{}: {}\n{}", task, e.getLocation(), e.getMessage(),
                        e.getTrace());
                if (!isParametric) {
                    reportInternalError(e, task, client);
                }

                throw new ResponseErrorException(new ResponseError(ResponseErrorCode.RequestFailed, formatMessage(e), null));
            }
            catch (StaticError e) {
                logger.error("Static Rascal error in {}\n{}: {}", task, e.getLocation(), e.getMessage());
                if (!isParametric) {
                    reportInternalError(e, task, client);
                }
                throw new ResponseErrorException(new ResponseError(ResponseErrorCode.InternalError, formatMessage(e), null));
            }
            catch (ResponseErrorException e) {
                logger.debug("{} threw an intentional error that should be forwarded to the lsp client without our involvement: {}", task, e.getMessage());
                throw e;
            }
            catch (Throwable e) {
                logger.error("{} failed", task, e);
                if (!isParametric) {
                    reportInternalError(e, task, client);
                }
                throw e;
            }
        }, exec), () -> {
            interrupted.set(true);
            Evaluator actualEval = runningEvaluator.get();
            if (actualEval != null) {
                actualEval.interrupt();
            }
        });
    }

    private static void extractReasonAndStackTrace(Throwable e, String task, StringWriter reason, StringWriter stackTrace) {
        reason.append(task);
        reason.append(" crashed unexpectedly with: ");
        if (e instanceof Throw) {
            stackTrace.append(((Throw) e).getTrace().toString());
            reason.append(formatMessage((Throw) e));
        } else {
            if (e instanceof StaticError) {
                reason.append(formatMessage((StaticError) e).replace('\n', ' '));
            } else {
                reason.append(e.getMessage());
            }
            e.printStackTrace(new PrintWriter(stackTrace));
        }
    }

    private enum ErrorHandlingOption {
        // The order in which these values are declared is the order in which they will appear in the VS Code tooltip
        REPORT_ON_GITHUB("Report on GitHub"),
        COPY_STACK_TRACE("Copy stack trace to clipboard"),
        IGNORE("Ignore");

        final String label;
        private static final Map<String, ErrorHandlingOption> BY_LABEL = new HashMap<>();

        static {
            Stream.of(values()).forEach(e -> BY_LABEL.put(e.label, e));
        }

        ErrorHandlingOption(String label) {
            this.label = label;
        }

        MessageActionItem getActionItem() {
            return new MessageActionItem(label);
        }

        static List<MessageActionItem> getActionItems() {
            return Stream.of(ErrorHandlingOption.values()).map(ErrorHandlingOption::getActionItem).collect(Collectors.toList());
        }

        public static ErrorHandlingOption valueOfLabel(String label) {
            return BY_LABEL.get(label);
        }
    }

    private static void createGithubIssue(Throwable e, String title, String stackTrace, LanguageClient client) {
        var body = new StringWriter();
        try (var bodyWriter = new PrintWriter(body)) {
            bodyWriter.println("Context: ***Please provide context***");
            bodyWriter.println();
            bodyWriter.println("Exception thrown:");
            bodyWriter.println("```");
            bodyWriter.println(e.getMessage());
            bodyWriter.println("```");
            bodyWriter.println("Stacktrace:");
            bodyWriter.println("```");
            bodyWriter.println(stackTrace);
            bodyWriter.println("```");
        }
        browse("https://github.com/usethesource/rascal-language-servers/issues/new?labels=bug&title=" + URLEncoder.encode(title, StandardCharsets.UTF_8) + "&body=" + URLEncoder.encode(body.toString(), StandardCharsets.UTF_8), client);
    }

    private static void copyToClipboard(String text, LanguageClient client) {
        var content = new StringSelection(text);
        try {
            var toolkit = Toolkit.getDefaultToolkit();
            if (toolkit == null) {
                logger.error("Could not find toolkit");
                return;
            }
            toolkit.getSystemClipboard().setContents(content, content);
        } catch (AWTError | HeadlessException | IllegalStateException e) {
            client.showMessage(new MessageParams(MessageType.Error, "Cannot copy to clipboard: " + e.getMessage()));
            logger.catching(e);
        }
    }

    private static void reportInternalError(Throwable e, String task, LanguageClient client) {
        StringWriter reason = new StringWriter();
        StringWriter stackTrace = new StringWriter();
        extractReasonAndStackTrace(e, task, reason, stackTrace);
        String title = task + " crashed unexpectedly with: " + reason.toString();
        var msg = new ShowMessageRequestParams();
        msg.setMessage(title);
        msg.setType(MessageType.Error);
        msg.setActions(ErrorHandlingOption.getActionItems());
        client.showMessageRequest(msg).thenAccept(response -> {
            if (response != null){
                switch (ErrorHandlingOption.valueOfLabel(response.getTitle())) {
                    case REPORT_ON_GITHUB:
                        createGithubIssue(e, title, stackTrace.toString(), client);
                        break;
                    case COPY_STACK_TRACE:
                        copyToClipboard(stackTrace.toString(), client);
                        break;
                    default:
                        // Do nothing
                }
            }
        });
    }

    private static void browse(String url, LanguageClient client) {
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (IOException | URISyntaxException | RuntimeException e) {
            client.showMessage(new MessageParams(MessageType.Error, "Cannot open browser github automatically: " + e.getMessage()));
            logger.catching(e);
        }

    }

    private static String formatMessage(Throw e) {
        String message = "";
        IValue thrown = e.getException();
        if (thrown instanceof IConstructor) {
            IConstructor exp = (IConstructor)thrown;
            if (exp.has("message")) {
                message = asString(exp.get("message"));
            }
            else {
                message = exp.getName();
            }
        }
        else {
            message = asString(thrown);
        }
        return message + "\nat: " + e.getLocation();
    }

    private static String asString(IValue v) {
        if (v instanceof IString) {
            return ((IString)v).getValue();
        }
        LimitedResultWriter res = new LimitedResultWriter(512);
        try {
            new StandardTextWriter(false).write(v, res);
        }
        catch (/*IO Limit*/ RuntimeException | IOException _ignored) {
        }
        return res.toString();
    }

    private static String formatMessage(StaticError e) {
        return "Static error: " + e.getMessage();
    }

    /**
     * This function is used to construct evaluators used by LSP servers, not the terminal REPL
     */
    public static CompletableFuture<Evaluator> makeFutureEvaluator(ExecutorService exec, IBaseTextDocumentService docService, BaseWorkspaceService workspaceService, IBaseLanguageClient client, String label, IRascalMonitor monitor, PathConfig pcfg, boolean addRascalCore, final String... imports) {
        return CompletableFuture.supplyAsync(() -> {
            Logger customLog = LogManager.getLogger("Evaluator: " + label);
            IDEServices services = new LSPIDEServices(client, docService, workspaceService, customLog, monitor);
            boolean jobSuccess = false;
            String jobName = "Loading " + label;
            try {
                services.jobStart(jobName, imports.length);
                Evaluator eval;
                Reader nullReader = Reader.nullReader();
                PrintWriter infoWriter = IoBuilder.forLogger(customLog).setLevel(Level.INFO).buildPrintWriter();
                PrintWriter errorWriter = IoBuilder.forLogger(customLog).setLevel(Level.ERROR).buildPrintWriter();
                if (pcfg == null) {
                    eval = ShellEvaluatorFactory.getDefaultEvaluator(nullReader, infoWriter, errorWriter, services);
                } else {
                    var lspJar = PathConfig.resolveProjectOnClasspath("rascal-lsp");
                    var newPcfg = pcfg.addSourceLoc(JarURIResolver.jarify(lspJar));

                    eval = ShellEvaluatorFactory.getDefaultEvaluatorForPathConfig(pcfg.getProjectRoot(), newPcfg,
                        nullReader, infoWriter, errorWriter, services);
                }

                eval.addClassLoader(RascalLanguageServer.class.getClassLoader());
                eval.addClassLoader(IValue.class.getClassLoader());

                if (addRascalCore) {
                    var rascalJar = JarURIResolver.jarify(PathConfig.resolveCurrentRascalRuntimeJar());
                    var rascalCore = URIUtil.getChildLocation(rascalJar, "org/rascalmpl/compiler");
                    var typePalJar = URIUtil.getChildLocation(rascalJar, "org/rascalmpl/typepal");

                    eval.addRascalSearchPath(typePalJar);
                    eval.addRascalSearchPath(rascalCore);
                }

                eval.doImport(services, imports);

                jobSuccess = true;
                return eval;
            }
            catch (IOException e) {
                throw new IllegalStateException(e);
            }
            finally {
                services.jobEnd(jobName, jobSuccess);
            }
        }, exec);
    }

}
