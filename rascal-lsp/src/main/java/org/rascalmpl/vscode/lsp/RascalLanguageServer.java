package org.rascalmpl.vscode.lsp;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

/**
 * The main language server class for Rascal is build on top of the Eclipse
 * lsp4j library
 */
public class RascalLanguageServer implements LanguageServer, LanguageClientAware {

    private static final RascalTextDocumentService RASCAL_TEXT_DOCUMENT_SERVICE = new RascalTextDocumentService();
    private static final RascalWorkspaceService RASCAL_WORKSPACE_SERVICE = new RascalWorkspaceService();
	private int errorCode = 1;
	private LanguageClient client;

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        final InitializeResult initializeResult = new InitializeResult(new ServerCapabilities());

        initializeResult.getCapabilities().setTextDocumentSync(TextDocumentSyncKind.Full);
        CompletionOptions completionOptions = new CompletionOptions();
        initializeResult.getCapabilities().setCompletionProvider(completionOptions);
        return CompletableFuture.supplyAsync(()->initializeResult);
    }

    @Override
    public void initialized(InitializedParams params) {
        // TODO Auto-generated method stub
        LanguageServer.super.initialized(params);
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
    public TextDocumentService getTextDocumentService() {
        return RASCAL_TEXT_DOCUMENT_SERVICE;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return RASCAL_WORKSPACE_SERVICE;
    }

    @Override
    public void connect(LanguageClient client) {
        this.client = client;
    }

    public static void main(String[] args) {
        LogManager.getLogManager().reset();
        Logger globalLogger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
        globalLogger.setLevel(Level.INFO);
        globalLogger.log(Level.INFO, "Starting Rascal Language Server");
        
        RascalLanguageServer server = new RascalLanguageServer() ;
        Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(server, System.in, System.out);
        server.connect(launcher.getRemoteProxy());
        Future<Void> startListening = launcher.startListening();
        try {
			startListening.get();
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
    }
}