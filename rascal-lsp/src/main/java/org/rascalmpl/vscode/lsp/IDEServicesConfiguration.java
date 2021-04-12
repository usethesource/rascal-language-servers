package org.rascalmpl.vscode.lsp;

public class IDEServicesConfiguration {
    private final int port;

    public IDEServicesConfiguration(int port) {
        this.port = port;
    }

    public int getPort() {
        return port;
    }
}
