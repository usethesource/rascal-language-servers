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
package org.rascalmpl.vscode.lsp;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
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
import org.rascalmpl.library.util.PathConfig;
import org.rascalmpl.library.util.PathConfig.RascalConfigMode;
import org.rascalmpl.shell.ShellEvaluatorFactory;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.vscode.lsp.extensions.InlayHint;
import org.rascalmpl.vscode.lsp.extensions.ProvideInlayHintsParams;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.LanguageParameter;
import org.rascalmpl.vscode.lsp.uri.ProjectURIResolver;
import org.rascalmpl.vscode.lsp.uri.TargetURIResolver;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.impl.VSCodeVFSClient;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.messages.VFSRegister;
import io.usethesource.vallang.IList;
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
        } catch (Throwable e) {
            logger.fatal("Unexpected exception", e);
            if (DEPLOY_MODE) {
                System.exit(1);
            }
        }
    }
    private static class ActualLanguageServer  implements IBaseLanguageServerExtensions, LanguageClientAware {
        static final Logger logger = LogManager.getLogger(ActualLanguageServer.class);
        private final IBaseTextDocumentService lspDocumentService;
        private final BaseWorkspaceService lspWorkspaceService;
        private final Runnable onExit;
        private IBaseLanguageClient client;
        private IDEServicesConfiguration ideServicesConfiguration;

        private ActualLanguageServer(Runnable onExit, IBaseTextDocumentService lspDocumentService) {
            this.onExit = onExit;
            this.lspDocumentService = lspDocumentService;
            this.lspWorkspaceService = new BaseWorkspaceService(lspDocumentService);
            URIResolverRegistry.getInstance().registerLogical(new ProjectURIResolver(this::resolveProjectLocation));
            URIResolverRegistry.getInstance().registerLogical(new TargetURIResolver(this::resolveProjectLocation));
        }

        private ISourceLocation resolveProjectLocation(ISourceLocation loc) {
            try {
                for (WorkspaceFolder folder : lspWorkspaceService.workspaceFolders()) {
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

        private static String[] classLoaderFiles(IList source) {
            return source.stream()
            .map(e -> (ISourceLocation) e)
            .filter(e -> e.getScheme().equals("file"))
            .map(e -> ((ISourceLocation) e).getPath())
            .toArray(String[]::new);
        }

        @Override
        public CompletableFuture<String[]> supplyProjectCompilationClasspath(IBaseLanguageServerExtensions.URIParameter projectFolder) {
            try {
                if (projectFolder.getUri() == null) {
                    return CompletableFuture.completedFuture(
                    classLoaderFiles(PathConfig.getDefaultClassloadersList())
                    );
                }
                ISourceLocation path = URIUtil.createFromURI(projectFolder.getUri());
                if (!URIResolverRegistry.getInstance().isDirectory(path)) {
                    path = URIUtil.getParentLocation(path);
                }

                ISourceLocation projectDir = ShellEvaluatorFactory.inferProjectRoot(new File(path.getPath()));
                if (projectDir == null) {
                    throw new RuntimeException("Project of file |" + projectFolder.getUri() + "| is missing a `META-INF/RASCAL.MF` file!");
                }
                PathConfig pcfg = PathConfig.fromSourceProjectRascalManifest(projectDir, RascalConfigMode.COMPILER);

                return CompletableFuture.completedFuture(classLoaderFiles(pcfg.getClassloaders()));
            }
            catch (IOException | URISyntaxException e) {
                logger.catching(e);
                throw new RuntimeException(e);
            }
        }

        @Override
        public CompletableFuture<Void> sendRegisterLanguage(LanguageParameter lang) {
            return CompletableFuture.runAsync(() -> lspDocumentService.registerLanguage(lang));
        }

        @Override
        public CompletableFuture<List<? extends InlayHint>> provideInlayHints(ProvideInlayHintsParams params) {
            return lspDocumentService.provideInlayHints(params);
        }

        @Override
        public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
            logger.info("LSP connection started");
            final InitializeResult initializeResult = new InitializeResult(new ServerCapabilities());
            lspDocumentService.initializeServerCapabilities(initializeResult.getCapabilities());
            lspWorkspaceService.initialize(params.getCapabilities(), params.getWorkspaceFolders(), initializeResult.getCapabilities());
            logger.debug("Initialized LSP connection with capabilities: {}", initializeResult);

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
        public BaseWorkspaceService getWorkspaceService() {
            return lspWorkspaceService;
        }

        @Override
        public void connect(LanguageClient client) {
            this.client = (IBaseLanguageClient) client;
            this.ideServicesConfiguration = IDEServicesThread.startIDEServices(this.client, lspDocumentService, lspWorkspaceService);
            lspDocumentService.connect(this.client);
            lspWorkspaceService.connect(this.client);
        }

        @Override
        public void registerVFS(VFSRegister registration) {
            VSCodeVFSClient.buildAndRegister(registration.getPort());
        }
    }
}
