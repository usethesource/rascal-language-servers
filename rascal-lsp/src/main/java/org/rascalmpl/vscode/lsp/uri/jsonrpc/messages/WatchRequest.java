package org.rascalmpl.vscode.lsp.uri.jsonrpc.messages;

import java.util.Objects;
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;
import io.usethesource.vallang.ISourceLocation;

public class WatchRequest extends ISourceLocationRequest {

    @NonNull
    private String watcher;

    public WatchRequest(ISourceLocation loc, String watcher) {
        super(loc);
        this.watcher = watcher;
    }

    public WatchRequest() {}

    public WatchRequest(String uri, String watcher) {
        super(uri);
        this.watcher = watcher;
    }

    public String getWatcher() {
        return watcher;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof WatchRequest) {
            return super.equals(obj)
                && Objects.equals(watcher, ((WatchRequest)obj).watcher);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return super.hashCode() + 11 * watcher.hashCode();
    }

}
