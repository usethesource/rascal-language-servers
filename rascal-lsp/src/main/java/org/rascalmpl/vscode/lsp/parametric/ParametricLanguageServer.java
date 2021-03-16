package org.rascalmpl.vscode.lsp.parametric;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.rascalmpl.vscode.lsp.IDEServicesConfiguration;
import org.rascalmpl.vscode.lsp.IDEServicesThread;
import org.rascalmpl.vscode.lsp.IBaseLanguageClient;
import org.rascalmpl.vscode.lsp.IBaseLanguageServerExtensions;
import org.rascalmpl.vscode.lsp.BaseWorkspaceService;
import org.rascalmpl.vscode.lsp.rascal.RascalLanguageServices;

/**
 * The main language server class for Rascal is build on top of the Eclipse lsp4j library
 */
@SuppressWarnings("java:S106") // we are using system.in/system.out correctly in this class
public class ParametricLanguageServer {
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

    private static final Logger logger = LogManager.getLogger(ParametricLanguageServer.class);

    private static int portNumber = 8888;

    private static Launcher<IBaseLanguageClient> constructParametricLSPClient(Socket client, ActualParametricLanguageServer server)
        throws IOException {
        return constructParametricLSPClient(client.getInputStream(), client.getOutputStream(), server);
    }

    private static Launcher<IBaseLanguageClient> constructParametricLSPClient(InputStream in, OutputStream out, ActualParametricLanguageServer server) {
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
    public static void main(String[] args) {
        logger.info("Starting Language Parametric Server");

        if (DEPLOY_MODE) {
            startParametricLSP(constructParametricLSPClient(capturedIn, capturedOut, new ActualParametricLanguageServer(() -> System.exit(0))));
        }
        else {
            try (ServerSocket serverSocket = new ServerSocket(portNumber, 0, InetAddress.getByName("127.0.0.1"))) {
                logger.info("Parametric LSP server listens on port number: {}", portNumber);
                while (true) {
                    startParametricLSP(constructParametricLSPClient(serverSocket.accept(), new ActualParametricLanguageServer(() -> {})));
                }
            } catch (IOException e) {
                logger.fatal("Failure to start TCP server", e);
            }
        }
    }

    private static void startParametricLSP(Launcher<IBaseLanguageClient> server) {
        try {
            server.startListening().get();
        } catch (InterruptedException e) {
            logger.trace("Interrupted language parametric server", e);
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            logger.fatal("Unexpected exception", e.getCause());
            if (DEPLOY_MODE) {
                System.exit(1);
            }
        }
    }

    private static final class ActualParametricLanguageServer implements LanguageServer, LanguageClientAware, IBaseLanguageServerExtensions {
        private static final Logger logger = LogManager.getLogger(ActualParametricLanguageServer.class);
        private final ParametricTextDocumentService lspDocumentService;
        private final BaseWorkspaceService lspWorkspaceService = new BaseWorkspaceService();
        private final Runnable onExit;
        private IBaseLanguageClient client;
        private IDEServicesConfiguration ideServicesConfiguration;

        private ActualParametricLanguageServer(Runnable onExit) {
            this.onExit = onExit;
            lspDocumentService = new ParametricTextDocumentService();
        }

        @Override
        public CompletableFuture<IDEServicesConfiguration> supplyIDEServicesConfiguration() {
            if (ideServicesConfiguration != null) {
                return CompletableFuture.completedFuture(ideServicesConfiguration);
            }
            
            throw new RuntimeException("no IDEServicesConfiguration is set?");
        }

        @Override
        public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
            logger.info("LSP connection started");
            final InitializeResult initializeResult = new InitializeResult(new ServerCapabilities());
            lspDocumentService.initializeServerCapabilities(initializeResult.getCapabilities());
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
        public ParametricTextDocumentService getTextDocumentService() {
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
            getTextDocumentService().connect(client);
        }
    }
}
