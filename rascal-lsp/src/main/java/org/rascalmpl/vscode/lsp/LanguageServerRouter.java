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
package org.rascalmpl.vscode.lsp;

import static org.rascalmpl.vscode.lsp.BaseLanguageServer.DEPLOY_MODE;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse;
import org.eclipse.lsp4j.ConfigurationParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.LogTraceParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.ProgressParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.ShowDocumentParams;
import org.eclipse.lsp4j.ShowDocumentResult;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.UnregistrationParams;
import org.eclipse.lsp4j.WorkDoneProgressCreateParams;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.vscode.lsp.parametric.LanguageRegistry.LanguageParameter;
import org.rascalmpl.vscode.lsp.parametric.routing.RoutingTextDocumentService;
import org.rascalmpl.vscode.lsp.parametric.routing.RoutingWorkspaceService;
import org.rascalmpl.vscode.lsp.util.Router;

import io.usethesource.vallang.IInteger;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;

public class LanguageServerRouter extends BaseLanguageServer.ActualLanguageServer implements IBaseLanguageClient, Router<CompletableFuture<IBaseLanguageServerExtensions>> {

    private static final Logger logger = LogManager.getLogger(LanguageServerRouter.class);

    private final Map<String, String> languagesByExtension;
    private final Map<String, CompletableFuture<IBaseLanguageServerExtensions>> languageServers;

    private @MonotonicNonNull InitializeParams initializeParams;

    private static final int REMOTE_BASE_PORT = 9990;
    private AtomicInteger remotePortOffset = new AtomicInteger(0);

    public LanguageServerRouter(Runnable onExit, ExecutorService exec) {
        super(onExit, exec, new RoutingTextDocumentService(exec), new RoutingWorkspaceService(exec));

        this.languagesByExtension = new ConcurrentHashMap<>();
        this.languageServers = new ConcurrentHashMap<>();
    }

    /*package*/ public CompletableFuture<IBaseLanguageServerExtensions> languageByName(String lang) {
        var service = languageServers.get(lang);
        if (service == null) {
            throw new UnsupportedOperationException(String.format("Rascal Parametric LSP has no support for this file, since no language is registered with name '%s'", lang));
        }
        return service;
    }

    @Override
    public CompletableFuture<IBaseLanguageServerExtensions> route(ISourceLocation file) {
        return route(extension(file));
    }

    public CompletableFuture<IBaseLanguageServerExtensions> route(String extension) {
        return safeLanguage(extension).map(this::languageByName).orElseThrow(() -> {
            throw new UnsupportedOperationException(String.format("Rascal Parametric LSP has no support for this file, since no language is registered with extension '%s'", extension));
        });
    }

    private Optional<String> safeLanguage(ISourceLocation loc) {
        return safeLanguage(extension(loc));
    }

    private Optional<String> safeLanguage(String extension) {
        if ("".equals(extension)) {
            var languages = new HashSet<>(languagesByExtension.values());
            if (languages.size() == 1) {
                logger.trace("File was opened without an extension; falling back to the single registered language for extension '{}'", extension);
                return languages.stream().findFirst();
            } else {
                logger.error("File was opened without an extension and there are multiple languages registered, so we cannot pick a fallback for extension '{}'", extension);
                return Optional.empty();
            }
        }
        return Optional.ofNullable(languagesByExtension.get(extension));
    }

    public static String extension(ISourceLocation doc) {
        return URIUtil.getExtension(doc);
    }

    private @Nullable CompletableFuture<IBaseLanguageServerExtensions> startServer(LanguageParameter lang) {
        try {
            var classPath = System.getProperty("java.class.path");
            InputStream in;
            OutputStream out;
            Runnable onExit;
            if (DEPLOY_MODE) {
                // In deployment, we start a process and connect to it via input/output streams
                var proc = new ProcessBuilder("java"
                    , "-Dlog4j2.configurationFactory=org.rascalmpl.vscode.lsp.log.LogJsonConfiguration"
                    , "-Dlog4j2.level=DEBUG"
                    , "-Drascal.fallbackResolver=org.rascalmpl.vscode.lsp.uri.FallbackResolver"
                    , "-Drascal.lsp.deploy=" + DEPLOY_MODE
                    , "-Drascal.compilerClasspath=" + classPath
                    , "-cp" + classPath
                    , "org.rascalmpl.vscode.lsp.parametric.ParametricLanguageServer"
                ).start();
                in = proc.getInputStream();
                out = proc.getOutputStream();
                // TODO Do we need to close the process here? Or is this triggered after the process exits?
                onExit = () -> {};
            } else {
                // In development, we expect the server to have been launched on a pre-agreed port
                int port = getNextPort();
                Socket socket = new Socket(InetAddress.getLoopbackAddress(), port);
                socket.setTcpNoDelay(true);
                in = socket.getInputStream();
                out = socket.getOutputStream();
                onExit = () -> {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        logger.error("Closing socket for language {} on port {} failed", lang.getName(), port);
                    }
                };
            }
            var serverLauncher = new Launcher.Builder<IBaseLanguageServerExtensions>()
                .setRemoteInterface(IBaseLanguageServerExtensions.class)
                .setLocalService(this)
                .setInput(in)
                .setOutput(out)
                .create();

            scheduleShutdown(serverLauncher.startListening(), lang, onExit);
            var server = serverLauncher.getRemoteProxy();
            var delegateServerCaps = server.initialize(delegateInitializationParams());

            // When initialization is done, we can use the server
            return delegateServerCaps.thenApply(_c -> server);
        } catch (IOException e) {
            logger.fatal(e);
            return null;
        }
    }

    private void scheduleShutdown(Future<Void> server, LanguageParameter lang, Runnable onExit) {
        getExecutor().execute(() -> {
            try {
                server.get();
            } catch (CancellationException | ExecutionException | InterruptedException e) {
                logger.error("Language server for {} crashed", lang.getName(), e);
            }
            try {
                onExit.run();
            } catch (Throwable e) {
                logger.error("Unexpected error while cleaning up connection to language server for {}", lang.getName(), e);
            }
        });
    }

    private int getNextPort() {
        return REMOTE_BASE_PORT + remotePortOffset.getAndIncrement();
    }

    private InitializeParams availableInitializeParams() {
        if (this.initializeParams == null) {
            throw new IllegalStateException("Server not initialized yet");
        }
        return initializeParams;
    }

    // TODO If this function does not require any parameters, change to a constant
    private InitializeParams delegateInitializationParams() {
        var params = new InitializeParams();
        var clientParams = availableInitializeParams();
        params.setCapabilities(clientParams.getCapabilities()); // We support precisely the capabilities of VS Code
        params.setClientInfo(clientParams.getClientInfo());
        params.setInitializationOptions(clientParams.getInitializationOptions());
        params.setLocale(clientParams.getLocale());
        try {
            params.setProcessId((int) ProcessHandle.current().pid());
        } catch (UnsupportedOperationException | SecurityException e) {
            logger.debug("Cannot set process ID", e);
        }
        return params;
    }

    public RoutingTextDocumentService getTextDocumentService() {
        return (RoutingTextDocumentService) super.getTextDocumentService();
    }

    public RoutingWorkspaceService getWorkspaceService() {
        return (RoutingWorkspaceService) super.getWorkspaceService();
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        // Capture the initialization params to re-use when initializing our delegates
        this.initializeParams = params;

        // Our child needs us, but we cannot set this in the constructor, so we set it here.
        getTextDocumentService().setServer(this);

        return super.initialize(params);
    }

    @Override
    public synchronized CompletableFuture<Void> sendRegisterLanguage(LanguageParameter lang) {
        // If we do not have a parametric server running for this language, start and initialize it.
        synchronized (this) {
            languageServers.computeIfAbsent(lang.getName(), _n -> startServer(lang));
            for (var ext : lang.getExtensions()) {
                languagesByExtension.put(ext, lang.getName());
            }
        }

        return super.sendRegisterLanguage(lang);
    }

    @Override
    public synchronized CompletableFuture<Void> sendUnregisterLanguage(LanguageParameter lang) {
        // TODO Handle shutting down the remote parametric server iff it is empty now.
        return super.sendUnregisterLanguage(lang);
    }

    @Override
    public void telemetryEvent(Object object) {
        availableClient().telemetryEvent(object);
    }

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
        availableClient().publishDiagnostics(diagnostics);
    }

    @Override
    public void showMessage(MessageParams messageParams) {
        availableClient().showMessage(messageParams);
    }

    @Override
    public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
        return availableClient().showMessageRequest(requestParams);
    }

    @Override
    public void logMessage(MessageParams message) {
        availableClient().logMessage(message);
    }

    @Override
    public void showContent(URI uri, IString title, IInteger viewColumn) {
        availableClient().showContent(uri, title, viewColumn);
    }

    @Override
    public void receiveRegisterLanguage(LanguageParameter lang) {
        availableClient().receiveRegisterLanguage(lang);
    }

    @Override
    public void receiveUnregisterLanguage(LanguageParameter lang) {
        availableClient().receiveUnregisterLanguage(lang);
    }

    @Override
    public void editDocument(URI uri, @Nullable Range range, int viewColumn) {
        availableClient().editDocument(uri, range, viewColumn);
    }

    @Override
    public void startDebuggingSession(int serverPort) {
        availableClient().startDebuggingSession(serverPort);
    }

    @Override
    public void registerDebugServerPort(int processID, int serverPort) {
        availableClient().registerDebugServerPort(processID, serverPort);
    }

    @Override
    public CompletableFuture<Void> createProgress(WorkDoneProgressCreateParams params) {
        return availableClient().createProgress(params);
    }

    @Override
    public void notifyProgress(ProgressParams params) {
        availableClient().notifyProgress(params);
    }

    @Override
    public CompletableFuture<ApplyWorkspaceEditResponse> applyEdit(ApplyWorkspaceEditParams params) {
        return availableClient().applyEdit(params);
    }

    @Override
    public CompletableFuture<List<Object>> configuration(ConfigurationParams configurationParams) {
        return availableClient().configuration(configurationParams);
    }

    @Override
    public void logTrace(LogTraceParams params) {
        availableClient().logTrace(params);
    }

    @Override
    public CompletableFuture<Void> refreshCodeLenses() {
        return availableClient().refreshCodeLenses();
    }

    @Override
    public CompletableFuture<Void> refreshDiagnostics() {
        return availableClient().refreshDiagnostics();
    }

    @Override
    public CompletableFuture<Void> refreshInlayHints() {
        return availableClient().refreshInlayHints();
    }

    @Override
    public CompletableFuture<Void> refreshInlineValues() {
        return availableClient().refreshInlineValues();
    }

    @Override
    public CompletableFuture<Void> refreshSemanticTokens() {
        return availableClient().refreshSemanticTokens();
    }

    @Override
    public CompletableFuture<ShowDocumentResult> showDocument(ShowDocumentParams params) {
        return availableClient().showDocument(params);
    }

    @Override
    public CompletableFuture<Void> registerCapability(RegistrationParams params) {
        // TODO Collect/maintain capabilities of all delegate servers, combine, and unregister capabilities if necessary based on that.
        return availableClient().registerCapability(params);
    }

    @Override
    public CompletableFuture<Void> unregisterCapability(UnregistrationParams params) {
        // TODO Collect/maintain capabilities of all delegate servers, combine, and unregister capabilities if necessary based on that.
        return availableClient().unregisterCapability(params);
    }

    @Override
    public CompletableFuture<List<WorkspaceFolder>> workspaceFolders() {
        return availableClient().workspaceFolders();
    }

}
