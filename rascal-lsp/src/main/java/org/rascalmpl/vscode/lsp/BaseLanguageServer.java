/*
 * Copyright (c) 2018-2021, NWO-I CWI and Swat.engineering
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.rascalmpl.vscode.lsp;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.FileAlreadyExistsException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.rascalmpl.library.Prelude;
import org.rascalmpl.library.util.PathConfig;
import org.rascalmpl.library.util.PathConfig.RascalConfigMode;
import org.rascalmpl.shell.ShellEvaluatorFactory;
import org.rascalmpl.uri.ISourceLocationWatcher.ISourceLocationChangeType;
import org.rascalmpl.uri.ISourceLocationWatcher.ISourceLocationChanged;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.LanguageParameter;
import org.rascalmpl.vscode.lsp.uri.ProjectURIResolver;
import org.rascalmpl.vscode.lsp.uri.TargetURIResolver;

import io.usethesource.vallang.ISourceLocation;

/**
 * The main language server class for Rascal is build on top of the Eclipse lsp4j library
 */
@SuppressWarnings("java:S106") // we are using system.in/system.out correctly in this class
public abstract class BaseLanguageServer {
    private static final @Nullable PrintStream capturedOut;
    private static final @Nullable InputStream capturedIn;
    private static final boolean DEPLOY_MODE;

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
            capturedIn = null;
            capturedOut = null;
        }
        System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
    }

    private static final Logger logger = LogManager.getLogger(BaseLanguageServer.class);

    private static Launcher<IBaseLanguageClient> constructLSPClient(Socket client, ActualLanguageServer server)
        throws IOException {
        return constructLSPClient(client.getInputStream(), client.getOutputStream(), server);
    }

    private static Launcher<IBaseLanguageClient> constructLSPClient(InputStream in, OutputStream out, ActualLanguageServer server) {
        Launcher<IBaseLanguageClient> clientLauncher = new Launcher.Builder<IBaseLanguageClient>()
            .setLocalService(server)
            .setRemoteInterface(IBaseLanguageClient.class)
            .setInput(in)
            .setOutput(out)
            .create();

        server.connect(clientLauncher.getRemoteProxy());

        return clientLauncher;
    }

    @SuppressWarnings({"java:S2189", "java:S106"})
    public static void startLanguageServer(Supplier<IBaseTextDocumentService> service, int portNumber) {
        logger.info("Starting Rascal Language Server: {}", getVersion());

        if (DEPLOY_MODE) {
            startLSP(constructLSPClient(capturedIn, capturedOut, new ActualLanguageServer(() -> System.exit(0), service.get())));
        }
        else {
            try (ServerSocket serverSocket = new ServerSocket(portNumber, 0, InetAddress.getByName("127.0.0.1"))) {
                logger.info("Rascal LSP server listens on port number: {}", portNumber);
                while (true) {
                    startLSP(constructLSPClient(serverSocket.accept(), new ActualLanguageServer(() -> {}, service.get())));
                }
            } catch (IOException e) {
                logger.fatal("Failure to start TCP server", e);
            }
        }
    }

    private static String getVersion() {
        try (InputStream prop = ActualLanguageServer.class.getClassLoader().getResourceAsStream("project.properties")) {
            Properties properties = new Properties();
            properties.load(prop);
            return properties.getProperty("rascal.lsp.version", "unknown") + " at "
                    + properties.getProperty("rascal.lsp.build.timestamp", "unknown");
        }
        catch (IOException e) {
            logger.debug("Cannot find lsp version", e);
            return "unknown";
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
        }
    }
    private static class ActualLanguageServer  implements IBaseLanguageServerExtensions, LanguageClientAware {
        static final Logger logger = LogManager.getLogger(ActualLanguageServer.class);
        private final IBaseTextDocumentService lspDocumentService;
        private final BaseWorkspaceService lspWorkspaceService = new BaseWorkspaceService();
        private final Runnable onExit;
        private IBaseLanguageClient client;
        private IDEServicesConfiguration ideServicesConfiguration;
        private List<WorkspaceFolder> workspaceFolders = Collections.emptyList();

        private ActualLanguageServer(Runnable onExit, IBaseTextDocumentService lspDocumentService) {
            this.onExit = onExit;
            this.lspDocumentService = lspDocumentService;
            URIResolverRegistry.getInstance().registerLogical(new ProjectURIResolver(this::resolveProjectLocation));
            URIResolverRegistry.getInstance().registerLogical(new TargetURIResolver(this::resolveProjectLocation));
        }

        private ISourceLocation resolveProjectLocation(ISourceLocation loc) {
            try {
                for (WorkspaceFolder folder : workspaceFolders) {
                    if (folder.getName().equals(loc.getAuthority())) {
                        ISourceLocation root = URIUtil.createFromURI(folder.getUri());
                        return URIUtil.getChildLocation(root, loc.getPath());
                    }
                }

                return loc;
            }
            catch (URISyntaxException e) {
                logger.catching(e);
                return loc;
            }
        }

        @Override
        public CompletableFuture<IDEServicesConfiguration> supplyIDEServicesConfiguration() {
            if (ideServicesConfiguration != null) {
                return CompletableFuture.completedFuture(ideServicesConfiguration);
            }

            throw new RuntimeException("no IDEServicesConfiguration is set?");
        }

        @Override
        public CompletableFuture<String[]> supplyProjectCompilationClasspath(URIParameter projectFolder) {
            try {
                ISourceLocation path = URIUtil.createFromURI(projectFolder.getUri());
                if (!URIResolverRegistry.getInstance().isDirectory(path)) {
                    path = URIUtil.getParentLocation(path);
                }

                ISourceLocation projectDir = ShellEvaluatorFactory.inferProjectRoot(new File(path.getPath()));
                PathConfig pcfg = PathConfig.fromSourceProjectRascalManifest(projectDir, RascalConfigMode.COMPILER);

                return CompletableFuture.completedFuture(pcfg.getClassloaders().stream()
                    .map(e -> (ISourceLocation) e)
                    .filter(e -> e.getScheme().equals("file"))
                    .map(e -> ((ISourceLocation) e).getPath())
                    .toArray(String[]::new));
            }
            catch (IOException | URISyntaxException e) {
                logger.catching(e);
                throw new RuntimeException(e);
            }
        }

        @Override
        public CompletableFuture<Void> sendRegisterLanguage(LanguageParameter lang) {
            CompletableFuture.runAsync(() -> lspDocumentService.registerLanguage(lang));
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
            logger.info("LSP connection started");
            final InitializeResult initializeResult = new InitializeResult(new ServerCapabilities());
            lspDocumentService.initializeServerCapabilities(initializeResult.getCapabilities());
            logger.debug("Initialized LSP connection with capabilities: {}", initializeResult);
            this.workspaceFolders = params.getWorkspaceFolders();

            return CompletableFuture.completedFuture(initializeResult);
        }

        @Override
        public CompletableFuture<Object> shutdown() {
            lspDocumentService.shutdown();
            return CompletableFuture.completedFuture(null);
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
        public WorkspaceService getWorkspaceService() {
            return lspWorkspaceService;
        }

        @Override
        public void connect(LanguageClient client) {
            this.client = (IBaseLanguageClient) client;
            this.ideServicesConfiguration = IDEServicesThread.startIDEServices(this.client);
            getTextDocumentService().connect(this.client);
        }

        private final URIResolverRegistry reg = URIResolverRegistry.getInstance();

        // BELOW THE FILESYSTEM SERVICE:

        @Override
        public CompletableFuture<Void> watch(URIParameter uri, boolean recursive, String[] excludes) throws IOException, URISyntaxException {
            URIResolverRegistry.getInstance().watch(URIUtil.createFromURI(uri.getUri()), recursive, changed -> {
                try {
                    onDidChangeFile(convertChangeEvent(changed));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

            return CompletableFuture.completedFuture(null);
        }

        private static FileChangeEvent convertChangeEvent(ISourceLocationChanged changed) throws IOException {
            return new FileChangeEvent(convertFileChangeType(changed.getChangeType()), changed.getLocation().getURI().toASCIIString());
        }

        private static FileChangeType convertFileChangeType(ISourceLocationChangeType changeType) throws IOException {
            switch (changeType) {
                case CREATED:
                    return FileChangeType.Created;
                case DELETED:
                    return FileChangeType.Deleted;
                case MODIFIED:
                    return FileChangeType.Changed;
                default:
                    throw new IOException("unknown change type: " + changeType);
            }
        }

        @Override
        public CompletableFuture<FileStat> stat(URIParameter uri) throws IOException, URISyntaxException {
            ISourceLocation loc = uri.getLocation();
            return CompletableFuture.completedFuture(new FileStat(
                reg.isDirectory(loc) ? FileType.Directory : FileType.File,
                reg.created(loc),
                reg.lastModified(loc),
                reg.supportsReadableFileChannel(loc)
                    ? reg.getReadableFileChannel(loc).size()
                    : Prelude.__getFileSize(IRascalValueFactory.getInstance(), loc).longValue()
                    ));
        }

        @Override
        public CompletableFuture<FileWithType[]> readDirectory(URIParameter uri) throws URISyntaxException, IOException {
            ISourceLocation loc = uri.getLocation();
            return CompletableFuture.completedFuture(Arrays.stream(reg.list(loc))
                .map(l -> new FileWithType(URIUtil.getLocationName(l), reg.isDirectory(l) ? FileType.Directory : FileType.File))
                .toArray(FileWithType[]::new));
        }

        @Override
        public CompletableFuture<Void> createDirectory(URIParameter uri) throws IOException, URISyntaxException {
            ISourceLocation loc = uri.getLocation();
            reg.mkDirectory(loc);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<LocationContent> readFile(URIParameter uri) throws URISyntaxException {
            ISourceLocation loc = uri.getLocation();
            return CompletableFuture.completedFuture(new LocationContent(Prelude.readFile(IRascalValueFactory.getInstance(), false, loc).getValue()));
        }

        @Override
        public CompletableFuture<Void> writeFile(URIParameter uri, String content, boolean create, boolean overwrite) throws URISyntaxException, IOException {
            ISourceLocation loc = uri.getLocation();

            if (!reg.exists(loc) && !create) {
                throw new FileNotFoundException(loc.toString());
            }

            if (!reg.exists(URIUtil.getParentLocation(loc)) && create) {
                throw new FileNotFoundException(URIUtil.getParentLocation(loc).toString());
            }

            if (reg.exists(loc) && create && !overwrite) {
                throw new FileAlreadyExistsException(loc.toString());
            }

            reg.getOutputStream(loc, false).write(content.getBytes(Charset.forName("UTF8")));
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> delete(URIParameter uri, boolean recursive) throws IOException, URISyntaxException {
            ISourceLocation loc = uri.getLocation();
            reg.remove(loc, recursive);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> rename(String oldUri, String newUri, boolean overwrite) throws IOException, URISyntaxException {
           ISourceLocation oldLoc = URIUtil.createFromURI(oldUri);
           ISourceLocation newLoc = URIUtil.createFromURI(newUri);
           reg.rename(oldLoc, newLoc, overwrite);
           return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<String[]> fileSystemSchemes() {
            Set<String> inputs = reg.getRegisteredInputSchemes();
            Set<String> logicals = reg.getRegisteredLogicalSchemes();

            return CompletableFuture.completedFuture(
                Stream.concat(inputs.stream(), logicals.stream()).toArray(String[]::new)
            );
        }
    }
}
