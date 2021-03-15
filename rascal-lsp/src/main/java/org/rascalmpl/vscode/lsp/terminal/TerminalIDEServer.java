package org.rascalmpl.vscode.lsp.terminal;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.vscode.lsp.IRascalLanguageClient;

import io.usethesource.vallang.ISourceLocation;

/**
 * This server forwards IDE services requests by a Rascal terminal
 * directly to the LSP language client.
 */
public class TerminalIDEServer implements ITerminalIDEServer {
    private static final Logger logger = LogManager.getLogger(TerminalIDEServer.class);

    private final IRascalLanguageClient languageClient;

    public TerminalIDEServer(IRascalLanguageClient client) {
        this.languageClient = client;
    }

    @Override
    public CompletableFuture<Void> browse(BrowseParameter uri) {
        languageClient.showContent(uri);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> edit(EditParameter edit) {
        languageClient.showMessage(new MessageParams(MessageType.Info, "trying to edit: " + edit.getModule()));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<SourceLocationParameter> resolveProjectLocation(SourceLocationParameter loc) {
        try {
            ISourceLocation input = loc.getLocation();

            for (WorkspaceFolder folder : languageClient.workspaceFolders().get()) {
                // TODO check if everything goes ok encoding-wise
                if (folder.getName().equals(input.getAuthority())) {
                    ISourceLocation root = URIUtil.createFromURI(folder.getUri());
                    ISourceLocation newLoc = URIUtil.getChildLocation(root, input.getPath());
                    return CompletableFuture.completedFuture(new SourceLocationParameter(newLoc));
                }
            }

            return CompletableFuture.completedFuture(loc);
        }  
        catch (URISyntaxException | InterruptedException | ExecutionException | IOException e) {
            logger.error(e);
            return CompletableFuture.completedFuture(loc);
        } 
    }
}
