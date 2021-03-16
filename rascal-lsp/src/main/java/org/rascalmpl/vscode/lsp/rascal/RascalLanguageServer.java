package org.rascalmpl.vscode.lsp.rascal;

import org.rascalmpl.vscode.lsp.BaseLanguageServer;

public class RascalLanguageServer extends BaseLanguageServer {
    public static void main(String[] args) {
        main(args, new RascalTextDocumentService(new RascalLanguageServices()), 8888);
    }
}