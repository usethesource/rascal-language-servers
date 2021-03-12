package org.rascalmpl.vscode.lsp;

import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.services.LanguageClient;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.BrowseParameter;

public interface IRascalLanguageClient extends LanguageClient {

	@JsonNotification("rascal/showContent")
    void showContent(BrowseParameter uri);
}