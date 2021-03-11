package org.rascalmpl.vscode.lsp;

import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.rascalmpl.vscode.lsp.IRascalLanguageClient;

/**
 * This server forwards IDE services requests by a Rascal terminal
 * directly to the LSP language client.
 */
public class TerminalIDEServer implements ITerminalIDEServer {
    private final IRascalLanguageClient languageClient;

    public TerminalIDEServer(IRascalLanguageClient client) {
        this.languageClient = client;
    }

    @Override
    public CompletableFuture<Void> browse(BrowseParameter uri) {
        languageClient.showMessage(new MessageParams(MessageType.Info, "trying to browse: " + uri.getUri()));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> edit(EditParameter edit) {
        languageClient.showMessage(new MessageParams(MessageType.Info, "trying to edit: " + edit.getModule()));
        return CompletableFuture.completedFuture(null);
    }
}
