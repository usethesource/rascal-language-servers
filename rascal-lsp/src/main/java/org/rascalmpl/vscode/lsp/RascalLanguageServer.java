package org.rascalmpl.vscode.lsp;

import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

/**
 * The main language server class for Rascal is build on top of the Eclipse
 * lsp4j library
 */
public class RascalLanguageServer implements LanguageServer {

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void exit() {
        // TODO Auto-generated method stub

    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return new RascalTextDocumentService();
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return new RascalWorkspaceService();
    }

}
