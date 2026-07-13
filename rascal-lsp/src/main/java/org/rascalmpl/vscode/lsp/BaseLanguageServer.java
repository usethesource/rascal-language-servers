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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.ServerInfo;
import org.eclipse.lsp4j.SetTraceParams;
import org.eclipse.lsp4j.WorkDoneProgressCancelParams;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Tuple.Two;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.rascalmpl.ideservices.GsonUtils;
import org.rascalmpl.library.util.PathConfig;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.uri.UnsupportedSchemeException;
import org.rascalmpl.uri.remote.jsonrpc.ISourceLocationRequest;
import org.rascalmpl.uri.remote.jsonrpc.RemoteIOError;
import org.rascalmpl.uri.remote.jsonrpc.SourceLocationResponse;
import org.rascalmpl.util.NamedThreadPool;
import org.rascalmpl.vscode.lsp.log.LogRedirectConfiguration;
import org.rascalmpl.vscode.lsp.parametric.LanguageRegistry.LanguageParameter;
import org.rascalmpl.vscode.lsp.terminal.RemoteIDEServicesThread;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.messages.PathConfigParameter;
import org.rascalmpl.vscode.lsp.util.Sets;
import org.rascalmpl.vscode.lsp.util.concurrent.CompletableFutureUtils;
import org.rascalmpl.vscode.lsp.util.locations.Locations;

import io.usethesource.vallang.IList;
import io.usethesource.vallang.ISourceLocation;

/**
* The main language server class for Rascal is built on top of the Eclipse lsp4j library
*/
@SuppressWarnings("java:S106") // we are using system.in/system.out correctly in this class
public abstract class BaseLanguageServer {
    private static final PrintStream capturedOut;
    private static final InputStream capturedIn;
    public static final boolean DEPLOY_MODE;
    private static final String LOG_CONFIGURATION_KEY = "log4j2.configurationFactory";

    static {
        DEPLOY_MODE = System.getProperty("rascal.lsp.deploy", "false").equalsIgnoreCase("true");
        if (DEPLOY_MODE){
            // we redirect system.out & system.in so that we can use them exclusively for lsp
            capturedIn = System.in;
            capturedOut = System.out;
            System.setIn(new ByteArrayInputStream(new byte[0]));
            System.setOut(new PrintStream(System.err, false)); // wrap stderr with a non flushing stream as that is how std.out normally works
        }
        else {
            capturedIn = InputStream.nullInputStream();
            capturedOut = new PrintStream(OutputStream.nullOutputStream());
        }
        System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
        // Do not overwrite existing settings (e.g. passed by the extension)
        System.setProperty(LOG_CONFIGURATION_KEY, System.getProperty(LOG_CONFIGURATION_KEY, LogRedirectConfiguration.class.getName()));
    }

    // hide implicit constructor
    protected BaseLanguageServer() {}

    private static final Logger logger = LogManager.getLogger(BaseLanguageServer.class);

    protected static Launcher<IBaseLanguageClient> constructLSPClient(Socket client, ActualLanguageServer server, ExecutorService threadPool)
        throws IOException {
        client.setTcpNoDelay(true);
        return constructLSPClient(client.getInputStream(), client.getOutputStream(), server, threadPool);
    }

    protected static Launcher<IBaseLanguageClient> constructLSPClient(InputStream in, OutputStream out, ActualLanguageServer server, ExecutorService threadPool) {
        Launcher<IBaseLanguageClient> clientLauncher = new Launcher.Builder<IBaseLanguageClient>()
            .setLocalService(server)
            .setRemoteInterface(IBaseLanguageClient.class)
            .setInput(in)
            .setOutput(out)
            .configureGson(GsonUtils.complexAsJsonObject())
            .setExecutorService(threadPool)
            .setExceptionHandler(t -> {
                if (t instanceof ResponseErrorException) {
                    return ((ResponseErrorException) t).getResponseError();
                }
                return RemoteIOError.translate(t).getResponseError();
            })
            .create();

        server.connect(clientLauncher.getRemoteProxy());

        return clientLauncher;
    }

    protected static void printClassPath() {
        logger.trace("Started with classpath: {}", () -> System.getProperty("java.class.path"));
    }

    @FunctionalInterface
    protected interface ServerBuilder {
        ActualLanguageServer apply(String name, Runnable a, ExecutorService b, IBaseTextDocumentService c, BaseWorkspaceService d);
    }

    protected static void startLanguageServer(String serverName, String requestPoolName, String workerPoolName, Function<ExecutorService, IBaseTextDocumentService> docServiceProvider, Function<ExecutorService, BaseWorkspaceService> workspaceServiceProvider, int portNumber) {
        startLanguageServer(ActualLanguageServer::new, serverName, requestPoolName, workerPoolName, docServiceProvider, workspaceServiceProvider, portNumber);
    }

    @SuppressWarnings({"java:S2189", "java:S106"})
    protected static void startLanguageServer(ServerBuilder serverBuilder, String serverName, String requestPoolName, String workerPoolName, Function<ExecutorService, IBaseTextDocumentService> docServiceProvider, Function<ExecutorService, BaseWorkspaceService> workspaceServiceProvider, int portNumber) {
        logger.info("Starting Rascal Language Server: {}", getVersion());
        printClassPath();

        if (DEPLOY_MODE) {
            var requestPool = NamedThreadPool.single(requestPoolName);
            var workerPool = NamedThreadPool.cached(workerPoolName);

            try {
                var docService = docServiceProvider.apply(workerPool);
                var wsService = workspaceServiceProvider.apply(workerPool);
                startLSP(constructLSPClient(capturedIn, capturedOut, serverBuilder.apply(serverName, () -> System.exit(0), workerPool, docService, wsService), requestPool));
            } finally {
                requestPool.shutdown();
                workerPool.shutdown();
            }
        }
        else {
            try (ServerSocket serverSocket = new ServerSocket(portNumber, 0, InetAddress.getByName("127.0.0.1"))) {
                logger.info("Rascal LSP server listens on port number: {}", portNumber);
                while (true) {
                    var requestPool = NamedThreadPool.single(requestPoolName);
                    var workerPool = NamedThreadPool.cached(workerPoolName);

                    try (Socket clientSocket = serverSocket.accept()) {
                        logger.info("New client connected to Rascal LSP server (listening on port number: {})", portNumber);
                        var docService = docServiceProvider.apply(workerPool);
                        var wsService = workspaceServiceProvider.apply(workerPool);
                        startLSP(constructLSPClient(clientSocket, serverBuilder.apply(serverName, () -> {}, workerPool, docService, wsService), requestPool));
                    }
                    finally {
                        requestPool.shutdown();
                        workerPool.shutdown();
                    }
                }
            } catch (IOException e) {
                logger.fatal("Failure to start TCP server on port {}", portNumber, e);
            }
        }
    }

    private static final String DEFAULT_VERSION = "unknown";

    protected static String getVersion() {
        try (InputStream prop =  ActualLanguageServer.class.getClassLoader().getResourceAsStream("project.properties")) {
            if (prop == null) {
                logger.error("Could not find project.properties file");
                return DEFAULT_VERSION;
            }
            Properties properties = new Properties();
            properties.load(prop);
            return properties.getProperty("rascal.lsp.version", DEFAULT_VERSION) + " at "
                + properties.getProperty("rascal.lsp.build.timestamp",DEFAULT_VERSION);
        }
        catch (IOException e) {
            logger.debug("Cannot find lsp version", e);
            return DEFAULT_VERSION;
        }
    }

    protected static void startLSP(Launcher<IBaseLanguageClient> server) {
        try {
            server.startListening().get();
        } catch (InterruptedException e) {
            logger.trace("Interrupted server", e);
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            logger.fatal("Unexpected exception", e.getCause());
            if (DEPLOY_MODE) {
                System.exit(1);
            }
        } catch (Throwable e) {
            logger.fatal("Unexpected exception", e);
            if (DEPLOY_MODE) {
                System.exit(1);
            }
        }
    }
    public static class ActualLanguageServer implements IBaseLanguageServerExtensions, LanguageClientAware {
        private static final Logger logger = LogManager.getLogger(ActualLanguageServer.class);

        private final String serverName;
        private final IBaseTextDocumentService lspDocumentService;
        private final BaseWorkspaceService lspWorkspaceService;
        private final Runnable onExit;
        private final ExecutorService executor;

        private @MonotonicNonNull IDEServicesConfiguration remoteIDEServicesConfiguration;
        private @MonotonicNonNull IBaseLanguageClient client;
        private @MonotonicNonNull InitializeResult initializeResult;

        protected ActualLanguageServer(String serverName, Runnable onExit, ExecutorService executor, IBaseTextDocumentService lspDocumentService, BaseWorkspaceService lspWorkspaceService) {
            this.serverName = serverName;
            this.onExit = onExit;
            this.executor = executor;
            this.lspDocumentService = lspDocumentService;
            this.lspWorkspaceService = lspWorkspaceService;
            lspDocumentService.pair(lspWorkspaceService);
            lspWorkspaceService.pair(lspDocumentService);
        }

        @Override
        public CompletableFuture<IDEServicesConfiguration> supplyRemoteIDEServicesConfiguration() {
            if (remoteIDEServicesConfiguration == null) {
                return CompletableFuture.failedFuture(new IllegalStateException("No RemoteIDEServices configuration is set"));
            }
            return CompletableFutureUtils.completedFuture(remoteIDEServicesConfiguration, executor);
        }

        private static URI[] toURIArray(IList src) {
            return src.stream()
                .map(ISourceLocation.class::cast)
                .map(Locations::toUri)
                .toArray(URI[]::new);
        }

        @Override
        public CompletableFuture<Two<String, URI[]>[]> supplyPathConfig(PathConfigParameter projectFolder) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    // TODO: why are we not communicating the JSON representation of the PathConfig constructor?
                    var pcfg = PathConfig.fromSourceProjectMemberRascalManifest(projectFolder.getLocation(), projectFolder.getMode().mapConfigMode());
                    @SuppressWarnings("unchecked")
                    Two<String, URI[]>[] result = new Two[2];
                    result[0] = new Two<>("Sources", toURIArray(pcfg.getSrcs()));
                    result[1] = new Two<>("Libraries", toURIArray(pcfg.getLibs()));
                    return result;
                } catch (URISyntaxException e) {
                    logger.catching(e);
                    throw new CompletionException(e);
                }
            }, executor);
        }

        @Override
        public CompletableFuture<Void> sendRegisterLanguage(LanguageParameter lang) {
            logger.debug("rascal/sendRegisterLanguage({}, {})", lang.getName(), lang.getMainFunction());
            lspDocumentService.registerLanguage(lang);
            return CompletableFutureUtils.completedFuture(null, executor);
        }
        @Override
        public CompletableFuture<Void> sendUnregisterLanguage(LanguageParameter lang) {
            logger.debug("rascal/sendUnregisterLanguage({})", lang.getName());
            lspDocumentService.unregisterLanguage(lang);
            return CompletableFutureUtils.completedFuture(null, executor);
        }

        @Override
        public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
            // Exit when our parent process exits
            executor.submit(() -> ProcessHandle.of(params.getProcessId()).ifPresent(p -> p.onExit().thenAccept(ignored -> this.exit())));

            logger.info("LSP connection started (connected to {} version {})", params.getClientInfo().getName(), params.getClientInfo().getVersion());
            logger.debug("LSP client capabilities: {}", params.getCapabilities());
            final InitializeResult initializeResult = new InitializeResult(new ServerCapabilities(), new ServerInfo(serverName, getClass().getPackage().getSpecificationVersion()));
            lspDocumentService.initializeServerCapabilities(params.getCapabilities(), initializeResult.getCapabilities());
            lspWorkspaceService.initialize(params.getCapabilities(), params.getWorkspaceFolders(), initializeResult.getCapabilities());
            logger.debug("Initialized LSP connection with capabilities: {}", initializeResult);
            this.initializeResult = initializeResult;
            return CompletableFutureUtils.completedFuture(initializeResult, executor);
        }

        @Override
        @SuppressWarnings("unused") // InitializedParams is an empty interface
        public void initialized(InitializedParams params) {
            logger.debug("LSP connection initialized");
            lspWorkspaceService.initialized();
            lspDocumentService.initialized();
        }

        @Override
        public CompletableFuture<Object> shutdown() {
            return CompletableFuture.completedFuture(new Object());
        }

        @Override
        public void exit() {
            onExit.run();
        }

        @Override
        public IBaseTextDocumentService getTextDocumentService() {
            return lspDocumentService;
        }

        @Override
        public BaseWorkspaceService getWorkspaceService() {
            return lspWorkspaceService;
        }

        @Override
        public void setTrace(SetTraceParams params) {
            logger.trace("Got trace request: {}", params);
            // TODO: handle this trace level
        }

        @Override
        public void connect(LanguageClient client) {
            this.client = addShutdownDetectionTo(client);
            lspDocumentService.connect(this.client);
            lspWorkspaceService.connect(this.client);
            remoteIDEServicesConfiguration = RemoteIDEServicesThread.startRemoteIDEServicesServer(this.client, lspDocumentService, executor);
            logger.debug("Remote IDE Services Port {}", remoteIDEServicesConfiguration);
        }

        protected IBaseLanguageClient availableClient() {
            if (client == null) {
                throw new IllegalStateException("Language Client has not been connected yet");
            }
            return client;
        }

        protected InitializeResult availableInitialization() {
            if (initializeResult == null) {
                throw new IllegalStateException("Server has not been initialized yet");
            }
            return initializeResult;
        }

        /**
         * Creates a proxy instance that forwards method calls to the provided
         * language client only when (the thread pool of) this language server
         * isn't shutdown yet. Otherwise, the proxy instance throws an
         * exception.
         *
         * This is a workaround for the LSP4J issue that (dis)connection-related
         * IO exceptions aren't propagated out of LSP4J (but only logged) when
         * the language client is used after shutdown. Thus, proactive effort is
         * needed to make sure such usage doesn't happen to begin with
         * (https://github.com/eclipse-lsp4j/lsp4j/issues/849).
         */
        private IBaseLanguageClient addShutdownDetectionTo(LanguageClient client) {
            var loader = IBaseLanguageClient.class.getClassLoader();
            var interfaces = new Class<?>[] { IBaseLanguageClient.class };
            var handler = (InvocationHandler) (Object proxy, Method method, Object[] args) -> {
                if (this.executor.isShutdown()) {
                    throw new IllegalStateException("The language client can no longer be used, as the server's thread pool is shutdown.");
                }
                return method.invoke(client, args);
            };

            return (IBaseLanguageClient) Proxy.newProxyInstance(loader, interfaces, handler);
        }

        protected ExecutorService getExecutor() {
            return executor;
        }

        @Override
        public void cancelProgress(WorkDoneProgressCancelParams params) {
            lspDocumentService.cancelProgress(params.getToken().getLeft());
        }

        @Override
        public void setMinimumLogLevel(String level) {
            final var l = Level.toLevel(level, Level.DEBUG); // fall back to debug when the string cannot be mapped
            Configurator.setRootLevel(l);
        }

        @Override
        public CompletableFuture<String[]> fileSystemSchemes() {
            return CompletableFuture.supplyAsync(() -> {
                var reg = URIResolverRegistry.getInstance();
                var inputs = reg.getRegisteredInputSchemes();
                var logicals = reg.getRegisteredLogicalSchemes();
                return Sets.union(inputs, logicals).toArray(String[]::new);
            }, executor);
        }

        private static ISourceLocation toRascalLocation(ISourceLocation loc) {
            if (Locations.isWrappedOpaque(loc)) {
                throw RemoteIOError.translate(new UnsupportedSchemeException("Opaque locations are not supported by Rascal: " + loc.getScheme()));
            }
            return Locations.toClientLocation(loc);
        }

        @Override
        public CompletableFuture<SourceLocationResponse> resolve(ISourceLocationRequest req) {
            logger.trace("resolve: {}", req.getLocation());
            return CompletableFuture.supplyAsync(() -> {
                var loc = toRascalLocation(req.getLocation());
                ISourceLocation resolved = null;
                if (!loc.getScheme().equals("std")) {
                    // TODO: this works around the fact that `std` is a bit of a broken scheme in
                    // VS Code, as REPL 1 might have a different std than REPL 2, and again different from the
                    // rascal-lsp server
                    // In a follow-up PR we should reconsider how we deal with std, but if we rewrite it here
                    // debugging is broken.
                    try {
                        resolved = URIResolverRegistry.getInstance().logicalToPhysical(loc);
                    } catch (IOException ignored) {
                        logger.trace("Resolving {} failed, but we ignored it", loc, ignored);
                    }
                }
                if (resolved == null) {
                    resolved = loc;
                }
                return new SourceLocationResponse(resolved);
            }, executor);
        }
    }
}
