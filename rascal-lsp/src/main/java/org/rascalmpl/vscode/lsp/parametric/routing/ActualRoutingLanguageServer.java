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
package org.rascalmpl.vscode.lsp.parametric.routing;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.WorkDoneProgressCancelParams;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.rascalmpl.library.util.PathConfig;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.util.maven.Artifact;
import org.rascalmpl.util.maven.MavenParser;
import org.rascalmpl.util.maven.ModelResolutionError;
import org.rascalmpl.util.maven.Scope;
import org.rascalmpl.vscode.lsp.BaseLanguageServer;
import org.rascalmpl.vscode.lsp.BaseWorkspaceService;
import org.rascalmpl.vscode.lsp.IBaseLanguageServerExtensions;
import org.rascalmpl.vscode.lsp.IBaseTextDocumentService;
import org.rascalmpl.vscode.lsp.log.LogJsonConfiguration;
import org.rascalmpl.vscode.lsp.parametric.LanguageRegistry.LanguageParameter;
import org.rascalmpl.vscode.lsp.parametric.ParametricTextDocumentService;
import org.rascalmpl.vscode.lsp.util.DocumentRouter;
import org.rascalmpl.vscode.lsp.util.Lists;
import org.rascalmpl.vscode.lsp.util.concurrent.CompletableFutureUtils;
import org.rascalmpl.vscode.lsp.util.locations.Locations;

import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValue;

/**
 * A language server implementation that routes LSP requests to dedicated remote language servers.
 */
public class ActualRoutingLanguageServer extends BaseLanguageServer.ActualLanguageServer implements DocumentRouter<CompletableFuture<IBaseLanguageServerExtensions>> {

    private static final Logger logger = LogManager.getLogger(ActualRoutingLanguageServer.class);

    private final Gson gson = new Gson();

    // NOTE
    // 1. This map should only contains running server processes.
    // 2. Upon removal from this map, the process should be killed to avoid resource leaks.
    // NOTE To be able to route to arbitrary third-party language servers, remote servers should implement `LanguageServer` (instead of `IBaseLanguageServerExtensions`)
    private final Map<String, CompletableFuture<IBaseLanguageServerExtensions>> languageServers = new ConcurrentHashMap<>();
    private final Map<String, String> languagesByExtension = new ConcurrentHashMap<>();

    private final MultipleClientProxy client = new MultipleClientProxy();
    private @MonotonicNonNull InitializeParams initializeParams;
    private final JsonWriter logForwarder;

    private static final int REMOTE_BASE_PORT = 9990;
    private static final int PORT_POOL_SIZE = 9;
    private NavigableSet<Integer> portPool = new ConcurrentSkipListSet<>();

    @SuppressWarnings("java:S106") // System.err
    public ActualRoutingLanguageServer(Runnable onExit, ExecutorService exec, IBaseTextDocumentService lspDocumentService, BaseWorkspaceService lspWorkspaceService) {
        super(onExit, exec, lspDocumentService, lspWorkspaceService);

        // log4j loggers write to stderr. We wrap the same stream, so we can directly pipe log messages from our child processes to it.
        logForwarder = new JsonWriter(new BufferedWriter(new OutputStreamWriter(System.err)));

        for (int i = 0; i < PORT_POOL_SIZE; i++) {
            portPool.add(REMOTE_BASE_PORT + i);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> destroyChildProcesses()));
    }

    private static void destroyChildProcesses() {
        ProcessHandle.current().children().forEach(p -> {
            try {
                if (p.isAlive() && !p.destroy()) {
                    p.destroyForcibly();
                }
            } catch (Exception e) {
                logger.error("Error while destroying process {}", p.pid(), e);
            }
        });
    }

    @Override
    public CompletableFuture<IBaseLanguageServerExtensions> route(String lang) {
        var service = languageServers.get(lang);
        if (service == null) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException(String.format("Rascal Parametric LSP has no support for this file, since no language is registered with name '%s'", lang)));
        }
        return service;
    }

    @Override
    public Collection<CompletableFuture<IBaseLanguageServerExtensions>> allRoutes() {
        return languageServers.values();
    }

    @Override
    public CompletableFuture<IBaseLanguageServerExtensions> route(ISourceLocation loc) {
        var lang = ParametricTextDocumentService.languageByExtension(loc, languagesByExtension);
        if (lang.isEmpty()) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException(String.format("Rascal Parametric LSP has no support for this file, since no language is registered with extension '%s'", extension(loc))));
        }
        return route(lang.get());
    }

    @Override
    public void connect(LanguageClient client) {
        super.connect(client); // first let the super class proxy the client
        this.client.connect(availableClient());
    }

    private static String extension(ISourceLocation doc) {
        return URIUtil.getExtension(doc);
    }

    private static boolean isRascal(Artifact art) {
        return "org.rascalmpl".equals(art.getCoordinate().getGroupId()) && "rascal".equals(art.getCoordinate().getArtifactId());
    }

    private static boolean isRascalLsp(Artifact art) {
        return "org.rascalmpl".equals(art.getCoordinate().getGroupId()) && "rascal-lsp".equals(art.getCoordinate().getArtifactId());
    }

    private static List<Path> classPath(LanguageParameter lang) throws IOException, ModelResolutionError {
        var pcfg = PathConfig.parse(lang.getPathConfig());
        var pom = Locations.toPhysicalIfPossible(URIUtil.getChildLocation(pcfg.getProjectRoot(), "pom.xml"));
        var maven = new MavenParser(Path.of(pom.getURI()));

        var project = maven.parseProject();
        var deps = project.resolveDependencies(Scope.COMPILE, maven);

        if (isRascalLsp(project)) {
            // When loading a language server within the Rasal LSP project (e.g. in tests), we do not have a dependency on/JAR of LSP.
            // Instead, we use its compiled classes and the JARs of all its dependencies.
            var target = Path.of(Locations.toUri(Locations.toPhysicalIfPossible(pcfg.getBin())));
            var depPaths = deps.stream()
                .map((Function<Artifact, @Nullable Path>) Artifact::getResolved)
                .filter(Objects::nonNull)
                .collect(Collectors.<@NonNull Path>toList());
            return Lists.union(List.of(target), depPaths);
        }

        return deps.stream()
            .filter(d -> isRascal(d) || isRascalLsp(d))
            .map((Function<Artifact, @Nullable Path>) Artifact::getResolved)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    private static void prependThreadName(String prefix, JsonElement json) {
        try {
            var obj = json.getAsJsonObject();
            var threadName = obj.getAsJsonPrimitive("threadName").getAsString();
            if (threadName != null) {
                obj.addProperty("threadName", prefix + threadName);
            }
        } catch (Exception e) { /* ignored */ }
    }

    @SuppressWarnings("java:S106") // System.err
    private void forwardLogs(InputStream logStream, String langName) {
        getExecutor().execute(() -> {
            var threadPrefix = langName + " | ";
            try (var reader = new BufferedReader(new InputStreamReader(logStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    try {
                        var json = JsonParser.parseString(line);
                        prependThreadName(threadPrefix, json);
                        // Lock, so we can make sure our JSON is followed by a newline.
                        synchronized (System.err) {
                            gson.toJson(json, logForwarder);
                            logForwarder.flush();
                            // One object per line; this is what log4j does as well.
                            System.err.println();
                        }
                    } catch (JsonSyntaxException e) {
                        // Sometimes the child process logs non-JSON (e.g. logs while setting up the JSON logger).
                        // In this case, just forward the raw line.
                        if (!line.isBlank()) {
                            // No need to lock, since `println` takes care of that.
                            System.err.println(line);
                        }
                    }
                }
            } catch (IOException e) {
                logger.error("Error while reading logs for {}", langName, e);
            }
        });
    }

    private @Nullable Triple<InputStream, OutputStream, Runnable> startServerProcess(LanguageParameter lang) {
        logger.info("Starting LSP process for {}", lang.getName());

        // In deployment, we start a process and connect to it via input/output streams
        try {
            var classPath = String.join(File.pathSeparator, classPath(lang).stream().map(Path::toString).collect(Collectors.toList()));
            logger.debug("{} runs with class path {}", lang.getName(), classPath);

            var proc = new ProcessBuilder(ProcessHandle.current().info().command().orElse("java")
                    , "-Dlog4j2.configurationFactory=org.rascalmpl.vscode.lsp.log.LogJsonConfiguration"
                    , "-Dlog4j2.level=" + LogJsonConfiguration.getLogLevel()
                    , "-Drascal.fallbackResolver=org.rascalmpl.vscode.lsp.uri.FallbackResolver"
                    , "-Drascal.lsp.deploy=true"
                    , "-Drascal.compilerClasspath=" + classPath
                    , "-Xmx2048M"
                    , "-cp", classPath
                    , "org.rascalmpl.vscode.lsp.parametric.ParametricLanguageServer"
                    , "--exitWhenEmpty"
                )
                .start();

            // Pipe logs from error stream
            forwardLogs(proc.getErrorStream(), lang.getName());

            logger.debug("Launched language server on process {}", proc.pid());
            return Triple.of(proc.getInputStream(), proc.getOutputStream(), () -> {});
        } catch (IOException | ModelResolutionError e) {
            logger.error("Starting language server process for {} failed", lang.getName(), e);
            return null;
        }
    }

    private @Nullable Triple<InputStream, OutputStream, Runnable> connectToServer(LanguageParameter lang) {
        // In development, we expect the server to have been launched on a pre-agreed port
        int port = portPool.pollFirst();
        try {
            @SuppressWarnings("java:S2095") // no need to close the socket here - we close it on server shutdown
            Socket socket = new Socket(InetAddress.getLoopbackAddress(), port);
            socket.setTcpNoDelay(true);
            return Triple.of(socket.getInputStream(), socket.getOutputStream(), () -> {
                try {
                    logger.debug("Closing socket for language {} on port {}", lang.getName(), port);
                    socket.close();
                } catch (IOException e) {
                    logger.error("Closing socket for {} on port {} failed", lang.getName(), port);
                } finally {
                    portPool.add(port); // Re-use the port
                }
            });
        } catch (IOException e) {
            logger.error("Connecting to socket at port {} failed", port, e);
            return null;
        }
    }

    /**
     * Special GSON configuration that (un)wraps IValues as-is.
     *
     * Encoding and decoding an {@link IValue} loses dynamic type information, hance a decoded value can not be encoded properly again.
     * `encode(decode(encode(v))) != encode(v)`
     * Since the router should just proxy values passed from remote servers, without changing them, it uses a special encoder/decoder.
     *
     */
    private static void configureProxyGson(GsonBuilder builder) {
        builder.registerTypeAdapter(ProxiedIValue.class, new TypeAdapter<ProxiedIValue>() {

            @Override
            public ProxiedIValue read(JsonReader reader) throws IOException {
                return ProxiedIValue.fromJson(reader);
            }

            @Override
            public void write(JsonWriter writer, ProxiedIValue proxiedValue) throws IOException {
                ProxiedIValue.toJson(writer, proxiedValue);
            }

        });

        // If support for creating (instead of forwaring) IValues in the routing server is required,
        // register JSON encoding (but not decoding) for regular (non-proxy) IValues here.
    }

    private @Nullable CompletableFuture<IBaseLanguageServerExtensions> startServer(LanguageParameter lang) {
        var serverParams = BaseLanguageServer.DEPLOY_MODE
            ? startServerProcess(lang)
            : connectToServer(lang)
            ;

        if (serverParams == null) {
            return null;
        }

        var serverLauncher = new Launcher.Builder<IBaseLanguageServerExtensions>()
            .setRemoteInterface(IBaseLanguageServerExtensions.class)
            .setLocalService(client)
            .setInput(serverParams.getLeft())
            .setOutput(serverParams.getMiddle())
            .configureGson(ActualRoutingLanguageServer::configureProxyGson)
            .setExecutorService(getExecutor())
            .create();

        var runner = serverLauncher.startListening();
        var server = serverLauncher.getRemoteProxy();

        var initializedServer = CompletableFutureUtils.completedFuture(delegateInitializationParams(), getExecutor())
            .thenCompose(server::initialize)
            // TODO Handle static server capabilities that are different than ours (because the remote has a different Rascal-LSP version)
            .thenApply(ignored -> server);

        getExecutor().execute(() -> {
            try {
                runner.get();
                logger.info("Language server for {} terminated", lang.getName());
            } catch (CancellationException | ExecutionException e) {
                logger.error("Language server for {} terminated with an exception", lang.getName(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                synchronized (this) {
                    if (languageServers.remove(lang.getName(), initializedServer)) {
                        for (var ext : lang.getExtensions()) {
                            languagesByExtension.remove(ext, lang.getName());
                        }
                    }
                }
                try {
                    // Run exit hook
                    serverParams.getRight().run();
                } catch (Exception e) {
                    logger.error("Unexpected error while cleaning up connection to language server for {}", lang.getName(), e);
                }
            }
        });

        return initializedServer; // When initialization is done, we can use the server
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
        params.setTrace(clientParams.getTrace());
        // TODO Set open workspace folders at the time of starting the server
        try {
            params.setProcessId((int) ProcessHandle.current().pid());
        } catch (UnsupportedOperationException | SecurityException e) {
            logger.debug("Cannot set delegate server parent process ID", e);
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
        getTextDocumentService().setServerRouter(this);
        getWorkspaceService().setServerRouter(this);

        return super.initialize(params);
    }

    @Override
    public synchronized CompletableFuture<Void> sendRegisterLanguage(LanguageParameter lang) {
        logger.debug("rascal/sendRegisterLanguage({}, {})", lang.getName(), lang.getMainFunction());
        // If we do not have a parametric server running for this language, start and initialize it.
        var server = languageServers.computeIfAbsent(lang.getName(), (Function<String, @Nullable CompletableFuture<IBaseLanguageServerExtensions>>) ignored -> startServer(lang));
        if (server == null) {
            throw new ResponseErrorException(new ResponseError(ResponseErrorCode.RequestFailed, String.format("Connecting to LSP server for %s failed", lang.getName()), null));
        }
        for (var ext : lang.getExtensions()) {
            languagesByExtension.put(ext, lang.getName());
        }
        return server.thenCompose(s -> s.sendRegisterLanguage(lang));
    }

    @Override
    public synchronized CompletableFuture<Void> sendUnregisterLanguage(LanguageParameter lang) {
        logger.debug("rascal/sendUnregisterLanguage({})", lang.getName());

        var work = route(lang.getName())
            .thenCompose(s -> s.sendUnregisterLanguage(lang));

        // Note: this should be handled for the deployed scenario by the process onExit hook.
        boolean removeAll = lang.getMainModule() == null || lang.getMainModule().isEmpty();
        if (removeAll) {
            // clear the whole language
            logger.trace("unregisterLanguage({}) completely", lang.getName());

            for (var extension : lang.getExtensions()) {
                this.languagesByExtension.remove(extension);
            }
            var removed = languageServers.remove(lang.getName());
            if (removed != null) {
                work = work
                    .thenCompose(ignored -> removed)
                    .thenCompose(server -> server.shutdown().thenAccept(ignored -> server.exit()));
            }
        }

        return work;
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        return CompletableFutureUtils.reduce(allRoutes().stream().map(serverFut -> serverFut.thenCompose(LanguageServer::shutdown)), getExecutor())
            .thenCompose(ignored -> super.shutdown())
            .whenComplete((v, t) -> {
                try {
                    logForwarder.flush();
                } catch (IOException e) {
                    logger.catching(e);
                }
            });
    }

    @Override
    public void exit() {
        try {
            CompletableFutureUtils.reduce(allRoutes().stream().map(serverFut -> serverFut.thenAccept(LanguageServer::exit)), getExecutor())
                .whenComplete((v, t) -> {
                    try {
                        logForwarder.close();
                    } catch (IOException e) {
                        logger.catching(e);
                    }
                })
                .get(10, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException e) {
            logger.error("Error while exiting child processes", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            destroyChildProcesses();
            super.exit();
        }
    }

    @Override
    public void cancelProgress(WorkDoneProgressCancelParams params) {
        // Forward to everyone
        allRoutes().forEach(r -> r.thenAccept(s -> s.cancelProgress(params)));
    }

}
