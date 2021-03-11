package org.rascalmpl.vscode.lsp;

import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;

/**
 * Server interface for remote implementation of @see IDEServices
 */
public interface ITerminalIDEServer {
    @JsonRequest
    default CompletableFuture<Void> browse(BrowseParameter uri) {
        throw new UnsupportedOperationException();
    }

    @JsonRequest
    default CompletableFuture<Void> edit(EditParameter edit)  {
        throw new UnsupportedOperationException();
    }

    public static class EditParameter {
        private String module;
    
        public EditParameter(String module) {
            this.module = module;
        }
    
        public String getModule() {
            return module;
        }
    }

    public static class BrowseParameter {
        private String uri;
    
        public BrowseParameter(String uri) {
            this.uri = uri;
        }
    
        public String getUri() {
            return uri;
        }
    }
}
