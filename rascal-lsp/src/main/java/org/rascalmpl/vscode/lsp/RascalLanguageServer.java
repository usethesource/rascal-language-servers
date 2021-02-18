package org.rascalmpl.vscode.lsp;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;

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

    private LanguageClient client;

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        final InitializeResult initializeResult = new InitializeResult(new ServerCapabilities());

        RASCAL_TEXT_DOCUMENT_SERVICE.initializeServerCapabilities(initializeResult.getCapabilities());

        return CompletableFuture.supplyAsync(() -> initializeResult);
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        this.errorCode = 0;
        return null;
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
        this.client = client;
        getTextDocumentService().connect(client);
    }

    private static Launcher<LanguageClient> constructLSPClient(Socket client, RascalLanguageServer server)
            throws IOException {
        Launcher<LanguageClient> clientLauncher = new Builder<LanguageClient>().setLocalService(server)
                .setRemoteInterface(LanguageClient.class)
                .setInput(client.getInputStream())
                .setOutput(client.getOutputStream())
                .create();
        server.connect(clientLauncher.getRemoteProxy());
        return clientLauncher;
    }

    public static void main(String[] args) {
        logger.info("Starting Rascal Language Server");
        setLoggingLevel(Level.INFO);

        for (int i = 0; i < args.length; i++) {
            switch (args[i])  {
                case "--port":
                    if (i + 1 < args.length) {
                        portNumber = Integer.parseInt(args[++i]);
                    }
                    break;
                case "--debug":
                    setLoggingLevel(Level.DEBUG);
                    break;
                case "--trace":
                    setLoggingLevel(Level.TRACE);
                    break;
            }
        }


        try (ServerSocket serverSocket = new ServerSocket(portNumber, 0, InetAddress.getByName("127.0.0.1"))) {
            logger.info("Rascal LSP server listens on port number: {}", portNumber);
            constructLSPClient(serverSocket.accept(), new RascalLanguageServer()).startListening();
        }
        catch (IOException e) {
            logger.fatal("Failure to start TCP server", e);
        }
    }

    private static void setLoggingLevel(Level level) {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration config = context.getConfiguration();
        LoggerConfig rootConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
        rootConfig.setLevel(level);
    }
}
