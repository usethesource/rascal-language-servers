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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.SetTraceParams;
import org.eclipse.lsp4j.WorkDoneProgressCancelParams;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.messages.Tuple.Two;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.rascalmpl.interpreter.NullRascalMonitor;
import org.rascalmpl.interpreter.utils.RascalManifest;
import org.rascalmpl.library.lang.json.internal.JsonValueReader;
import org.rascalmpl.library.lang.json.internal.JsonValueWriter;
import org.rascalmpl.library.util.PathConfig;
import org.rascalmpl.library.util.PathConfig.RascalConfigMode;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.vscode.lsp.log.LogRedirectConfiguration;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.LanguageParameter;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.impl.VSCodeVFSClient;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.messages.PathConfigParameter;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.messages.VFSRegister;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.type.TypeFactory;
import io.usethesource.vallang.type.TypeStore;

/**
* The main language server class for Rascal is build on top of the Eclipse lsp4j library
*/
@SuppressWarnings("java:S106") // we are using system.in/system.out correctly in this class
public abstract class BaseLanguageServer {
    private static final PrintStream capturedOut;
    private static final InputStream capturedIn;
    private static final boolean DEPLOY_MODE;
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

    private static Launcher<IBaseLanguageClient> constructLSPClient(Socket client, ActualLanguageServer server, ExecutorService threadPool)
        throws IOException {
        client.setTcpNoDelay(true);
        return constructLSPClient(client.getInputStream(), client.getOutputStream(), server, threadPool);
    }

    private static Launcher<IBaseLanguageClient> constructLSPClient(InputStream in, OutputStream out, ActualLanguageServer server, ExecutorService threadPool) {
        Launcher<IBaseLanguageClient> clientLauncher = new Launcher.Builder<IBaseLanguageClient>()
            .setLocalService(server)
            .setRemoteInterface(IBaseLanguageClient.class)
            .setInput(in)
            .setOutput(out)
            .configureGson(BaseLanguageServer::configureGson)
            .setExecutorService(threadPool)
            .create();

        server.connect(clientLauncher.getRemoteProxy());

        return clientLauncher;
    }

    private static void configureGson(GsonBuilder builder) {
        JsonValueWriter writer = new JsonValueWriter();
        JsonValueReader reader = new JsonValueReader(IRascalValueFactory.getInstance(), new TypeStore(), new NullRascalMonitor(), null);
        writer.setDatesAsInt(true);

        builder.registerTypeHierarchyAdapter(IValue.class, new TypeAdapter<IValue>() {
            @Override
            public IValue read(JsonReader source) throws IOException {
                return reader.read(source, TypeFactory.getInstance().valueType());
            }

            @Override
            public void write(JsonWriter target, IValue value) throws IOException {
                writer.write(target, value);
            }
        });
    }

    private static void printClassPath() {
        logger.trace("Started with classpath: {}", () -> System.getProperty("java.class.path"));
    }

    @SuppressWarnings({"java:S2189", "java:S106"})
    public static void startLanguageServer(ExecutorService threadPool, Function<ExecutorService, IBaseTextDocumentService> docServiceProvider, BiFunction<ExecutorService, IBaseTextDocumentService, BaseWorkspaceService> workspaceServiceProvider, int portNumber) {
        logger.info("Starting Rascal Language Server: {}", getVersion());
        printClassPath();

        if (DEPLOY_MODE) {
            var docService = docServiceProvider.apply(threadPool);
            var wsService = workspaceServiceProvider.apply(threadPool, docService);
            docService.pair(wsService);
            startLSP(constructLSPClient(capturedIn, capturedOut, new ActualLanguageServer(() -> System.exit(0), threadPool, docService, wsService), threadPool));
        }
        else {
            try (ServerSocket serverSocket = new ServerSocket(portNumber, 0, InetAddress.getByName("127.0.0.1"))) {
                logger.info("Rascal LSP server listens on port number: {}", portNumber);
                while (true) {
                    var docService = docServiceProvider.apply(threadPool);
                    var wsService = workspaceServiceProvider.apply(threadPool, docService);
                    docService.pair(wsService);
                    startLSP(constructLSPClient(serverSocket.accept(), new ActualLanguageServer(() -> {}, threadPool, docService, wsService), threadPool));
                }
            } catch (IOException e) {
                logger.fatal("Failure to start TCP server", e);
            }
        }
    }

    private static final String DEFAULT_VERSION = "unknown";

    private static String getVersion() {
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

    private static void startLSP(Launcher<IBaseLanguageClient> server) {
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
    private static class ActualLanguageServer  implements IBaseLanguageServerExtensions, LanguageClientAware {
        static final Logger logger = LogManager.getLogger(ActualLanguageServer.class);
        private static final URIResolverRegistry reg = URIResolverRegistry.getInstance();
        private final IBaseTextDocumentService lspDocumentService;
        private final BaseWorkspaceService lspWorkspaceService;
        private final Runnable onExit;
        private final ExecutorService executor;
        private IDEServicesConfiguration ideServicesConfiguration;

        private ActualLanguageServer(Runnable onExit, ExecutorService executor, IBaseTextDocumentService lspDocumentService, BaseWorkspaceService lspWorkspaceService) {
            this.onExit = onExit;
            this.executor = executor;
            this.lspDocumentService = lspDocumentService;
            this.lspWorkspaceService = lspWorkspaceService;
        }

        @Override
        public CompletableFuture<IDEServicesConfiguration> supplyIDEServicesConfiguration() {
            if (ideServicesConfiguration != null) {
                return CompletableFuture.completedFuture(ideServicesConfiguration);
            }

            throw new RuntimeException("no IDEServicesConfiguration is set?");
        }

        private static String[] classLoaderFiles(IList source) {
            return source.stream()
                .map(ISourceLocation.class::cast)
                .filter(e -> e.getScheme().equals("file"))
                .map(e -> e.getPath())
                .toArray(String[]::new);
        }

        @Override
        public CompletableFuture<String[]> supplyProjectCompilationClasspath(URIParameter projectFolder) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    if (projectFolder.getUri() == null) {
                        return classLoaderFiles(IRascalValueFactory.getInstance().list(PathConfig.resolveCurrentRascalRuntimeJar()));
                    }

                    var isRascal = new AtomicBoolean();
                    PathConfig pcfg = findPathConfig(projectFolder.getLocation(), RascalConfigMode.INTERPRETER, isRascal);
                    return classLoaderFiles(isRascal.get() ? pcfg.getLibs() : pcfg.getLibsAndTarget());
                }
                catch (IOException | URISyntaxException e) {
                    logger.catching(e);
                    throw new CompletionException(e);
                }
            }, executor);
        }

        private static PathConfig findPathConfig(ISourceLocation path, RascalConfigMode mode, AtomicBoolean isRascal) throws IOException {
            if (!reg.isDirectory(path)) {
                path = URIUtil.getParentLocation(path);
            }

            isRascal.set(false);

            ISourceLocation projectDir = PathConfig.inferProjectRoot(path);
            if (projectDir == null) {
                throw new IOException("Project of file |" + path.toString() + "| is missing a `META-INF/RASCAL.MF` file!");
            }
            isRascal.set(new RascalManifest().getProjectName(projectDir).equals("rascal"));
            return PathConfig.fromSourceProjectRascalManifest(projectDir, mode, true);
        }

        private static URI[] toURIArray(IList src) {
            return src.stream()
                .map(ISourceLocation.class::cast)
                .map(ISourceLocation::getURI)
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
            return CompletableFuture.runAsync(() -> lspDocumentService.registerLanguage(lang), executor);
        }
        @Override
        public CompletableFuture<Void> sendUnregisterLanguage(LanguageParameter lang) {
            return CompletableFuture.runAsync(() -> lspDocumentService.unregisterLanguage(lang), executor);
        }

        @Override
        public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
            return CompletableFuture.supplyAsync(() -> {
                logger.info("LSP connection started (connected to {} version {})", params.getClientInfo().getName(), params.getClientInfo().getVersion());
                logger.debug("LSP client capabilities: {}", params.getCapabilities());
                final InitializeResult initializeResult = new InitializeResult(new ServerCapabilities());
                lspDocumentService.initializeServerCapabilities(initializeResult.getCapabilities());
                lspWorkspaceService.initialize(params.getCapabilities(), params.getWorkspaceFolders(), initializeResult.getCapabilities());
                logger.debug("Initialized LSP connection with capabilities: {}", initializeResult);
                return initializeResult;
            }, executor);
        }

        @Override
        @SuppressWarnings("unused") // InitializedParams is an empty interface
        public void initialized(InitializedParams params) {
            executor.submit(() -> {
                logger.debug("LSP connection initialized");
                lspWorkspaceService.initialized();
                lspDocumentService.initialized();
            });
        }

        @Override
        public CompletableFuture<Object> shutdown() {
            return CompletableFuture.supplyAsync(() -> {
                lspDocumentService.shutdown();
                return null;
            }, executor);
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
            var actualClient = (IBaseLanguageClient) client;
            this.ideServicesConfiguration = IDEServicesThread.startIDEServices(actualClient, lspDocumentService, lspWorkspaceService);
            lspDocumentService.connect(actualClient);
            lspWorkspaceService.connect(actualClient);
        }

        @Override
        public void registerVFS(VFSRegister registration) {
            VSCodeVFSClient.buildAndRegister(registration.getPort());
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
    }
}
