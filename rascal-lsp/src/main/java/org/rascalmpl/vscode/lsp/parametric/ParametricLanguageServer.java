package org.rascalmpl.vscode.lsp.parametric;

import org.rascalmpl.vscode.lsp.BaseLanguageServer;

public class ParametricLanguageServer extends BaseLanguageServer {
    public static void main(String[] args) {
        main(args, new ParametricTextDocumentService());
    }
}
