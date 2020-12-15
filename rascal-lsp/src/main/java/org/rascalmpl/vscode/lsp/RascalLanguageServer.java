package org.rascalmpl.vscode.lsp;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
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

    private static final RascalTextDocumentService RASCAL_TEXT_DOCUMENT_SERVICE = new RascalTextDocumentService();
    private static final RascalWorkspaceService RASCAL_WORKSPACE_SERVICE = new RascalWorkspaceService();
    private int errorCode = 1;
    private static int portNumber = 9001;

    private LanguageClient client;

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        final InitializeResult initializeResult = new InitializeResult(new ServerCapabilities());

        RASCAL_TEXT_DOCUMENT_SERVICE.setCapabilities(initializeResult.getCapabilities());
        
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
        LogManager.getLogManager().reset();
        Logger globalLogger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
        globalLogger.setLevel(Level.INFO);
        globalLogger.log(Level.INFO, "Starting Rascal Language Server");

        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("-port") && i + 1 < args.length) {
                portNumber = Integer.parseInt(args[++i]);
            }
        }

        try (ServerSocket serverSocket = new ServerSocket(portNumber, 0, InetAddress.getByName("localhost"))) {
            Socket client;
            while ((client = serverSocket.accept()) != null) {
                constructLSPClient(client, new RascalLanguageServer()).startListening();
            }
        } catch (UnknownHostException e1) {
            throw new RuntimeException(e1);
        } catch (IOException e1) {
            ;
        }
    }
}