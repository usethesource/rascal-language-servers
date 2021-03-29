package org.rascalmpl.vscode.lsp.rascal;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.rascalmpl.vscode.lsp.BaseLanguageServer;

public class RascalLanguageServer extends BaseLanguageServer {
    public static void main(String[] args) {
        startLanguageServer(() -> {
            ExecutorService threadPool = Executors.newCachedThreadPool();
            return new RascalTextDocumentService(new RascalLanguageServices(threadPool), threadPool);
        }, 8888);
    }
}
