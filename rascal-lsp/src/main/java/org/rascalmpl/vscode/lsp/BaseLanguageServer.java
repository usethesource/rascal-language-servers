package org.rascalmpl.vscode.lsp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Properties;
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
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.LanguageParameter;

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
    public static void main(String[] args, IBaseTextDocumentService service, int portNumber) {
        logger.info("Starting Rascal Language Server: {}", getVersion());

        if (DEPLOY_MODE) {
            startLSP(constructLSPClient(capturedIn, capturedOut, new ActualLanguageServer(() -> System.exit(0), service)));
        }
        else {
            try (ServerSocket serverSocket = new ServerSocket(portNumber, 0, InetAddress.getByName("127.0.0.1"))) {
                logger.info("Rascal LSP server listens on port number: {}", portNumber);
                while (true) {
                    startLSP(constructLSPClient(serverSocket.accept(), new ActualLanguageServer(() -> {}, service)));
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

    private static class ActualLanguageServer implements LanguageServer, LanguageClientAware, IBaseLanguageServerExtensions {
        static final Logger logger = LogManager.getLogger(ActualLanguageServer.class);
        private final IBaseTextDocumentService lspDocumentService;
        private final BaseWorkspaceService lspWorkspaceService = new BaseWorkspaceService();
        private final Runnable onExit;
        private IBaseLanguageClient client;
        private IDEServicesConfiguration ideServicesConfiguration;

        private ActualLanguageServer(Runnable onExit, IBaseTextDocumentService lspDocumentService) {
            this.onExit = onExit;
            this.lspDocumentService = lspDocumentService;
        }

        @Override
        public CompletableFuture<IDEServicesConfiguration> supplyIDEServicesConfiguration() {
            if (ideServicesConfiguration != null) {
                return CompletableFuture.completedFuture(ideServicesConfiguration);
            }
            
            throw new RuntimeException("no IDEServicesConfiguration is set?");
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
    }
}
