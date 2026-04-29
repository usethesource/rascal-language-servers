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

import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.apache.commons.lang3.tuple.Triple;
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
import org.rascalmpl.ideservices.GsonUtils;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.vscode.lsp.parametric.LanguageRegistry.LanguageParameter;
import org.rascalmpl.vscode.lsp.parametric.routing.RoutingTextDocumentService;
import org.rascalmpl.vscode.lsp.parametric.routing.RoutingWorkspaceService;
import org.rascalmpl.vscode.lsp.util.DocumentRouter;
import org.rascalmpl.vscode.lsp.util.NamedThreadPool;

import io.usethesource.vallang.IInteger;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;

/**
 * A language server implementation that routes LSP requests to dedicated remote language servers.
 */
public class LanguageServerRouter extends BaseLanguageServer.ActualLanguageServer implements IBaseLanguageClient, DocumentRouter<CompletableFuture<IBaseLanguageServerExtensions>> {

    private static final Logger logger = LogManager.getLogger(LanguageServerRouter.class);

    private final Map<String, String> languagesByExtension;
    // TODO To be able to route to arbitrary third-party language servers, remote servers should implement `LanguageServer` (instead of `IBaseLanguageServerExtensions`)
    private final Map<String, CompletableFuture<IBaseLanguageServerExtensions>> languageServers;
    private final Collection<Process> delegateProcesses = new CopyOnWriteArrayList<>();

    private @MonotonicNonNull InitializeParams initializeParams;

    private static final int REMOTE_BASE_PORT = 9990;
    private AtomicInteger remotePortOffset = new AtomicInteger(0);

    public LanguageServerRouter(Runnable onExit, ExecutorService exec) {
        super(onExit, exec, new RoutingTextDocumentService(exec), new RoutingWorkspaceService(exec));

        this.languagesByExtension = new ConcurrentHashMap<>();
        this.languageServers = new ConcurrentHashMap<>();

        // Shutdown child processes when we exit
        Runtime.getRuntime().addShutdownHook(new Thread(() -> delegateProcesses.forEach(Process::destroy)));
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

    /*
    private static boolean isRascalLspProject(Artifact art) {
        var c = art.getCoordinate();
        if (!c.getGroupId().equals("org.rascalmpl")) {
            return false;
        }
        var id = c.getArtifactId();
        return "rascal-lsp".equals(id);
    }
    */

    private static String classPath(LanguageParameter lang) {
        // TODO Build class path based on POM
        /*
        try {
            var pcfg = PathConfig.parse(lang.getPathConfig());
            var pom = Locations.toPhysicalIfPossible(URIUtil.getChildLocation(pcfg.getProjectRoot(), "pom.xml"));
            var p = new MavenParser(Path.of(pom.getURI()));
            var rootProject = p.parseProject();

            // Check if we are in Rascal-LSP
            var classPath = new StringBuilder();
            if (isRascalLspProject(rootProject)) {
                classPath.append(';');
                classPath.append(Path.of(Locations.toUri(pcfg.getBin())));
            }

            // Check the project dependencies
            var deps = rootProject.resolveDependencies(Scope.COMPILE, p);
            for (var d : deps) {
                if (d.getResolved() != null) {
                    classPath.append(';');
                    classPath.append(d.getResolved());
                }
            }
            // strip of the initial separator ';'
            return classPath.substring(1).toString();
        } catch (IOException e) {
            logger.error("Error while parsing path config {}", lang.getPathConfig(), e);
        } catch (ModelResolutionError e) {
            logger.error("Error while parsing Maven project {}", e);
        }
        */
        return System.getProperty("java.class.path");
    }

    private static Triple<InputStream, OutputStream, Runnable> startServerProcess(LanguageParameter lang) throws IOException {
        // TODO Figure out Rascal/Rascal-LSP versions/class path
        logger.info("Starting LSP process for {}", lang.getName());

        var classPath = classPath(lang);
        logger.debug("{} runs with class path {}", lang.getName(), classPath);
        // In deployment, we start a process and connect to it via input/output streams
        var proc = new ProcessBuilder(ProcessHandle.current().info().command().orElse("java")
            , "-Dlog4j2.configurationFactory=org.rascalmpl.vscode.lsp.log.LogJsonConfiguration"
            , "-Dlog4j2.level=DEBUG"
            , "-Drascal.fallbackResolver=org.rascalmpl.vscode.lsp.uri.FallbackResolver"
            , "-Drascal.lsp.deploy=true"
            , "-Drascal.compilerClasspath=" + classPath
            , "-Xmx2048M"
            , "-cp", classPath
            , "org.rascalmpl.vscode.lsp.parametric.ParametricLanguageServer"
            , new GsonBuilder().create().toJson(lang, LanguageParameter.class).replace("\"", "\\\"")
        )
        .redirectError(Redirect.INHERIT) // Show logs in current process
        .start();

        logger.debug("Launched language server on process {}", proc.pid());
        return Triple.of(proc.getInputStream(), proc.getOutputStream(), () -> {});
    }

    private Triple<InputStream, OutputStream, Runnable> connectToServer(LanguageParameter lang) throws IOException {
        // In development, we expect the server to have been launched on a pre-agreed port
        int port = getNextPort();
        @SuppressWarnings("java:S2095") // no need to close the socket here - we close it on server shutdown
        Socket socket = new Socket(InetAddress.getLoopbackAddress(), port);
        socket.setTcpNoDelay(true);
        return Triple.of(socket.getInputStream(), socket.getOutputStream(), () -> {
            try {
                logger.debug("Closing socket for language {} on port {}", lang.getName(), port);
                socket.close();
            } catch (IOException e) {
                logger.error("Closing socket for {} on port {} failed", lang.getName(), port);
            }
        });
    }

    private CompletableFuture<IBaseLanguageServerExtensions> startServer(LanguageParameter lang) throws IOException {
        var serverParams = DEPLOY_MODE
            ? startServerProcess(lang)
            : connectToServer(lang)
            ;

        var serverLauncher = new Launcher.Builder<IBaseLanguageServerExtensions>()
            .setRemoteInterface(IBaseLanguageServerExtensions.class)
            .setLocalService(this)
            .setInput(serverParams.getLeft())
            .setOutput(serverParams.getMiddle())
            .configureGson(GsonUtils.complexAsJsonObject()) // Only needed if we want to communicate IValues
            .setExecutorService(NamedThreadPool.single("parametric-lsp-router-out"))
            .create();

        var runner = serverLauncher.startListening();
        var server = serverLauncher.getRemoteProxy();

        getExecutor().execute(() -> {
            try {
                runner.get();
                logger.info("Language server for {} terminated gracefully", lang.getName());
            } catch (CancellationException | ExecutionException e) {
                logger.error("Language server for {} terminated", lang.getName(), e);
                languageServers.remove(lang.getName(), server);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            try {
                serverParams.getRight().run();
            } catch (Throwable e) {
                logger.error("Unexpected error while cleaning up connection to language server for {}", lang.getName(), e);
            }
        });

        // When initialization is done, we can use the server
        return server.initialize(delegateInitializationParams())
            .thenApply(_c -> server);
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

    @Override
    public RoutingTextDocumentService getTextDocumentService() {
        return (RoutingTextDocumentService) super.getTextDocumentService();
    }

    @Override
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
        logger.debug("rascal/sendRegisterLanguage({}, {})", lang.getName(), lang.getMainFunction());
        // If we do not have a parametric server running for this language, start and initialize it.
        synchronized (this) {
            var server = languageServers.computeIfAbsent(lang.getName(), (Function<String, @Nullable CompletableFuture<IBaseLanguageServerExtensions>>) _n -> {
                try {
                    return startServer(lang);
                } catch (IOException e) {
                    logger.error("Unexpected error while starting language server for {}", lang.getName(), e);
                    return null;
                }
            });
            if (server != null) {
                for (var ext : lang.getExtensions()) {
                    languagesByExtension.put(ext, lang.getName());
                }
            }
        }

        return super.sendRegisterLanguage(lang);
    }

    @Override
    public synchronized CompletableFuture<Void> sendUnregisterLanguage(LanguageParameter lang) {
        logger.debug("rascal/sendUnregisterLanguage({})", lang.getName());
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
        logger.debug("rascal/receiveRegisterLanguage({}, {})", lang.getName(), lang.getMainFunction());
        availableClient().receiveRegisterLanguage(lang);
    }

    @Override
    public void receiveUnregisterLanguage(LanguageParameter lang) {
        logger.debug("rascal/receiveUnregisterLanguage({}, {})", lang.getName(), lang.getMainFunction());
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
