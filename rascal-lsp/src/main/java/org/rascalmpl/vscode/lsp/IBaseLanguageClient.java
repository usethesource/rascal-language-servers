package org.rascalmpl.vscode.lsp;

import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.services.LanguageClient;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.BrowseParameter;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.LanguageParameter;

public interface IBaseLanguageClient extends LanguageClient {

	@JsonNotification("rascal/showContent")
    void showContent(BrowseParameter uri);

    @JsonNotification("rascal/receiveRegisterLanguage")
    void receiveRegisterLanguage(LanguageParameter lang);


}
