package org.rascalmpl.vscode.lsp.parametric;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.rascalmpl.vscode.lsp.BaseLanguageServer;

public class ParametricLanguageServer extends BaseLanguageServer {
    public static void main(String[] args) {
        startLanguageServer(() -> {
            ExecutorService threadPool = Executors.newCachedThreadPool();
            return new ParametricTextDocumentService(threadPool);
        }, 9999);
    }
}
