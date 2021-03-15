package org.rascalmpl.vscode.lsp;

class IDEServicesConfiguration {
    private final int port;

    public IDEServicesConfiguration(int port) {
        this.port = port;
    }

    public int getPort() {
        return port;
    }
}