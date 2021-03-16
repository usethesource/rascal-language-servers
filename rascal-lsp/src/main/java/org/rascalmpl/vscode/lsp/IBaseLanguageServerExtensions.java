package org.rascalmpl.vscode.lsp;

import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;

public interface IBaseLanguageServerExtensions {
    @JsonRequest("rascal/supplyIDEServicesConfiguration")
    default CompletableFuture<IDEServicesConfiguration> supplyIDEServicesConfiguration() {
        throw new UnsupportedOperationException();
    }
}
