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
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.launch.LSPLauncher.Builder;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.WorkspaceService;

/**
 * The main language server class for Rascal is build on top of the Eclipse
 * lsp4j library
 */
public class RascalLanguageServer implements LanguageServer, LanguageClientAware {
    private static final Logger logger = LogManager.getLogger(RascalLanguageServer.class);

    private static final RascalTextDocumentService RASCAL_TEXT_DOCUMENT_SERVICE = new RascalTextDocumentService();
    private static final RascalWorkspaceService RASCAL_WORKSPACE_SERVICE = new RascalWorkspaceService();
    private int errorCode = 1;
    private static int portNumber = 8888;

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        logger.info("LSP connection started");
        final InitializeResult initializeResult = new InitializeResult(new ServerCapabilities());

        RASCAL_TEXT_DOCUMENT_SERVICE.initializeServerCapabilities(initializeResult.getCapabilities());
        return CompletableFuture.supplyAsync(() -> initializeResult);
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        this.errorCode = 0;
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void exit() {
        System.exit(errorCode);
    }

    @Override
    public RascalTextDocumentService getTextDocumentService() {
        return RASCAL_TEXT_DOCUMENT_SERVICE;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return RASCAL_WORKSPACE_SERVICE;
    }

    @Override
    public void connect(LanguageClient client) {
        getTextDocumentService().connect(client);
    }

    private static Launcher<LanguageClient> constructLSPClient(Socket client, RascalLanguageServer server)
            throws IOException {
        return constructLSPClient(client.getInputStream(), client.getOutputStream(), server);
    }

    private static Launcher<LanguageClient> constructLSPClient(InputStream in, OutputStream out, RascalLanguageServer server) {
        Launcher<LanguageClient> clientLauncher = LSPLauncher.createServerLauncher(server, in, out);
        server.connect(clientLauncher.getRemoteProxy());
        return clientLauncher;
    }

    @SuppressWarnings({"java:S2189", "java:S106"})
    public static void main(String[] args) {
        boolean deployMode = false;
        Level logLevel = Level.INFO;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--deploy":
                    deployMode = true;
                    break;
                case "--debug":
                    logLevel = Level.DEBUG;
                    break;
                case "--trace":
                    logLevel = Level.TRACE;
                    break;
            }
        }
        PrintStream out = System.out;
        InputStream in = System.in;

        System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
        if (deployMode) {
            // redirect streams to protect them
            System.setIn(new ByteArrayInputStream(new byte[0]));
            System.setOut(System.err);
        }
        else {
            setLoggingLevel(logLevel);
        }
        logger.info("Starting Rascal Language Server");

        if (deployMode) {
            startLSP(constructLSPClient(in, out, new RascalLanguageServer()));
        }
        else {
            try (ServerSocket serverSocket = new ServerSocket(portNumber, 0, InetAddress.getByName("127.0.0.1"))) {
                logger.info("Rascal LSP server listens on port number: {}", portNumber);
                while (true) {
                    startLSP(constructLSPClient(serverSocket.accept(), new RascalLanguageServer()));
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
                System.exit(1);
            }
    }



    private static void setLoggingLevel(Level level) {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration config = context.getConfiguration();
        LoggerConfig rootConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
        rootConfig.setLevel(level);
        context.updateLoggers();
    }
}
