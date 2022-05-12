package org.rascalmpl.vscode.lsp.uri.jsonrpc;

import java.io.IOException;
import java.util.function.Consumer;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.rascalmpl.uri.ISourceLocationWatcher;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.messages.ISourceLocationChanged;
import io.usethesource.vallang.ISourceLocation;

public interface VSCodeUriResolverClient {

    @JsonNotification("rascal/vfs/watcher/emitWatch")
    void emitWatch(ISourceLocationChanged event);

    void addWatcher(ISourceLocation loc, Consumer<ISourceLocationWatcher.ISourceLocationChanged> callback, VSCodeUriResolverServer server) throws IOException;
    void removeWatcher(ISourceLocation loc, Consumer<ISourceLocationWatcher.ISourceLocationChanged> callback, VSCodeUriResolverServer server) throws IOException;
}
