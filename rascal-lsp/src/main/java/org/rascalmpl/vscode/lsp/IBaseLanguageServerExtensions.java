package org.rascalmpl.vscode.lsp;

import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.BrowseParameter;
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

    @JsonRequest("rascal/locationContents")
    default CompletableFuture<LocationContent> locationContents(URIParameter lang) {
        throw new UnsupportedOperationException();
    }

    public static class LocationContent {
        private String content;

        public LocationContent(String content) {
            this.content = content;
        }

        public String getContent() {
            return content;
        }
    }

    public static class URIParameter {
        private String uri;

        public URIParameter(String uri) {
            this.uri = uri;
        }

        public String getUri() {
            return uri;
        }
    }

    
}
