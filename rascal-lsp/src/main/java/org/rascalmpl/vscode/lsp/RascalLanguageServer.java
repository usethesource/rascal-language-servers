package org.rascalmpl.vscode.lsp;

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
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.WorkspaceService;

/**
 * The main language server class for Rascal is build on top of the Eclipse lsp4j library
 */
public class RascalLanguageServer {
    private static final @Nullable PrintStream capturedOut;
    private static final @Nullable InputStream capturedIn;
    private static final boolean DEPLOY_MODE;

    static {
        DEPLOY_MODE = System.getProperty("rascal.lsp.deploy", "false").equalsIgnoreCase("true");
        if (DEPLOY_MODE){
            capturedIn = System.in;
            capturedOut = System.out;
            System.setIn(new ByteArrayInputStream(new byte[0]));
            System.setOut(System.err);
        }
        else {
            capturedIn = null;
            capturedOut = null;
        }
        System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
    }

    private static final Logger logger = LogManager.getLogger(RascalLanguageServer.class);

    private static int portNumber = 8888;

    private static Launcher<LanguageClient> constructLSPClient(Socket client, ActualServer server)
        throws IOException {
        return constructLSPClient(client.getInputStream(), client.getOutputStream(), server);
    }

    private static Launcher<LanguageClient> constructLSPClient(InputStream in, OutputStream out,
        ActualServer server) {
        Launcher<LanguageClient> clientLauncher = LSPLauncher.createServerLauncher(server, in, out);
        server.connect(clientLauncher.getRemoteProxy());
        return clientLauncher;
    }

    @SuppressWarnings({"java:S2189", "java:S106"})
    public static void main(String[] args) {
        logger.info("Starting Rascal Language Server");

        if (DEPLOY_MODE) {
            startLSP(constructLSPClient(capturedIn, capturedOut, new ActualServer(() -> System.exit(0))));
        }
        else {
            try (ServerSocket serverSocket = new ServerSocket(portNumber, 0, InetAddress.getByName("127.0.0.1"))) {
                logger.info("Rascal LSP server listens on port number: {}", portNumber);
                while (true) {
                    startLSP(constructLSPClient(serverSocket.accept(), new ActualServer(() -> {})));
                }
            } catch (IOException e) {
                logger.fatal("Failure to start TCP server", e);
            }
        }
    }

    private static void startLSP(Launcher<LanguageClient> server) {
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



    private static final class ActualServer implements LanguageServer, LanguageClientAware {
        private static final Logger logger = LogManager.getLogger(ActualServer.class);
        private final RascalTextDocumentService lspDocumentService;
        private final RascalWorkspaceService lspWorkspaceService = new RascalWorkspaceService();
        private final Runnable onExit;

        private ActualServer(Runnable onExit) {
            this.onExit = onExit;
            lspDocumentService = new RascalTextDocumentService(new RascalLanguageServices());
        }

        @Override
        public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
            logger.info("LSP connection started");
            final InitializeResult initializeResult = new InitializeResult(new ServerCapabilities());
            lspDocumentService.initializeServerCapabilities(initializeResult.getCapabilities());
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
        public RascalTextDocumentService getTextDocumentService() {
            return lspDocumentService;
        }

        @Override
        public WorkspaceService getWorkspaceService() {
            return lspWorkspaceService;
        }

        @Override
        public void connect(LanguageClient client) {
            getTextDocumentService().connect(client);
        }

    }
}
