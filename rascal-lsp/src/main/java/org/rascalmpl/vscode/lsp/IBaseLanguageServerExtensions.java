package org.rascalmpl.vscode.lsp;

import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.LanguageParameter;

public interface IBaseLanguageServerExtensions {
    @JsonRequest("rascal/supplyIDEServicesConfiguration")
    default CompletableFuture<IDEServicesConfiguration> supplyIDEServicesConfiguration() {
        throw new UnsupportedOperationException();
    }

    @JsonRequest("rascal/sendRegisterLanguage")
    default CompletableFuture<Void> sendRegisterLanguage(LanguageParameter lang) {
        throw new UnsupportedOperationException();
    }
}
