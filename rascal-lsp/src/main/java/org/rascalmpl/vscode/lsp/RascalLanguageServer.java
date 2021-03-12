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

import org.apache.logging.log4j.Level;
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
import org.rascalmpl.vscode.lsp.terminal.*;

/**
 * The main language server class for Rascal is build on top of the Eclipse lsp4j library
 */
@SuppressWarnings("java:S106") // we are using system.in/system.out correctly in this class
public class RascalLanguageServer {
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

    private static final Logger logger = LogManager.getLogger(RascalLanguageServer.class);

    private static int portNumber = 8888;

    private static Launcher<IRascalLanguageClient> constructLSPClient(Socket client, ActualLanguageServer server)
        throws IOException {
        return constructLSPClient(client.getInputStream(), client.getOutputStream(), server);
    }

    private static Launcher<IRascalLanguageClient> constructLSPClient(InputStream in, OutputStream out, ActualLanguageServer server) {
        Launcher<IRascalLanguageClient> clientLauncher = new Launcher.Builder<IRascalLanguageClient>()
            .setLocalService(server)
            .setRemoteInterface(IRascalLanguageClient.class)
            .setInput(in)
            .setOutput(out)
            .create();

        server.connect(clientLauncher.getRemoteProxy());
        
        return clientLauncher;
    }

    @SuppressWarnings({"java:S2189", "java:S106"})
    public static void main(String[] args) {
        logger.info("Starting Rascal Language Server");

        if (DEPLOY_MODE) {
            startLSP(constructLSPClient(capturedIn, capturedOut, new ActualLanguageServer(() -> System.exit(0))));
        }
        else {
            try (ServerSocket serverSocket = new ServerSocket(portNumber, 0, InetAddress.getByName("127.0.0.1"))) {
                logger.info("Rascal LSP server listens on port number: {}", portNumber);
                while (true) {
                    startLSP(constructLSPClient(serverSocket.accept(), new ActualLanguageServer(() -> {})));
                }
            } catch (IOException e) {
                logger.fatal("Failure to start TCP server", e);
            }
        }
    }

    private static void startLSP(Launcher<IRascalLanguageClient> server) {
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

    public static final class ActualLanguageServer implements LanguageServer, LanguageClientAware, IRascalLanguageServerExtensions {
        private static final Logger logger = LogManager.getLogger(ActualLanguageServer.class);
        private final RascalTextDocumentService lspDocumentService;
        private final RascalWorkspaceService lspWorkspaceService = new RascalWorkspaceService();
        private final Runnable onExit;
        private IRascalLanguageClient client;
        private IDEServicesConfiguration ideServicesConfiguration;

        private ActualLanguageServer(Runnable onExit) {
            this.onExit = onExit;
            lspDocumentService = new RascalTextDocumentService(new RascalLanguageServices());
        }

        @Override
        public CompletableFuture<IDEServicesConfiguration> supplyIDEServicesConfiguration() {
            if (ideServicesConfiguration != null) {
                return CompletableFuture.completedFuture(ideServicesConfiguration);
            }
            
            logger.log(Level.ERROR, "no IDEServicesConfiguration is set?");
            throw new RuntimeException("no IDEServicesConfiguration is set?");
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
            this.client = (IRascalLanguageClient) client;
            this.ideServicesConfiguration = startIDEServices(this.client);
            getTextDocumentService().connect(client);
        }

        /**
         * Start a server for remote IDE services. These are used
         * by an implementation of @see IDEServices that is given to Rascal terminal REPLS
         * when they are started in the context of the LSP. This way
         * Rascal REPLs get access to @see IDEServices to provide browsers 
         * for interactive visualizations, starting editors and resolving IDE project URI.
         * 
         * @return the port number that the IDE services are running on
         * @throws IOException when a new server socket can not be established.
         */
        private IDEServicesConfiguration startIDEServices(IRascalLanguageClient client) {
            try {
                ServerSocket socket = new ServerSocket(0);
                IDEServicesThread service = new IDEServicesThread(client, socket);
                service.start();
            
                return new IDEServicesConfiguration(socket.getLocalPort());
            } catch (IOException e) {
                logger.error(e);
                throw new RuntimeException(e);
            }
        }

        private static class IDEServicesThread extends Thread {
            private final IRascalLanguageClient ideClient;
            private final ServerSocket serverSocket;

            public IDEServicesThread(IRascalLanguageClient client, ServerSocket socket) {
                super("Terminal IDE Services Thread");
                this.serverSocket = socket;
                this.ideClient = client;
            }

            @Override
            public void run() {
                try {
                    while(true) {
                        Socket connection = serverSocket.accept();

                        Launcher<ITerminalIDEServer> ideServicesServerLauncher = new Launcher.Builder<ITerminalIDEServer>()
                            .setLocalService(new TerminalIDEServer(ideClient))
                            .setInput(connection.getInputStream())
                            .setOutput(connection.getOutputStream())
                            .create();

                        ideServicesServerLauncher.startListening();
                    }
                }
                catch (IOException e) {
                    logger.error(e);
                    throw new RuntimeException(e);
                }
                finally {
                    try {
                        serverSocket.close();
                    } catch (IOException e) {
                        logger.error(e);
                    }
                }
            }
        }
    }
}