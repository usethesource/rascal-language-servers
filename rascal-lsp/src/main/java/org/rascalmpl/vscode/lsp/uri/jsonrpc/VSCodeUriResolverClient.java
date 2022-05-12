package org.rascalmpl.vscode.lsp.uri.jsonrpc;

import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.messages.ISourceLocationChanged;

public interface VSCodeUriResolverClient {

    @JsonNotification("rascal/vfs/watcher/emitWatch")
    void emitWatch(ISourceLocationChanged event);
}
