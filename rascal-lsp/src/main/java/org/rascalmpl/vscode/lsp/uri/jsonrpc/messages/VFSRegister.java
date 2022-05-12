package org.rascalmpl.vscode.lsp.uri.jsonrpc.messages;

import org.eclipse.lsp4j.jsonrpc.validation.NonNull;

public class VFSRegister {
    @NonNull
    private int port;

    public VFSRegister() {
    }

    public VFSRegister(int port) {
        this.port = port;
    }

    public int getPort() {
        return port;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof VFSRegister) {
            return port == ((VFSRegister)obj).port;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return port;
    }

}
