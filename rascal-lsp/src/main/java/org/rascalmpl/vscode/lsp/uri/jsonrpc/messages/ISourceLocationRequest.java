package org.rascalmpl.vscode.lsp.uri.jsonrpc.messages;

import java.util.Objects;
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;
import io.usethesource.vallang.ISourceLocation;

public class ISourceLocationRequest {
    @NonNull
    private String uri;

    public ISourceLocationRequest() {
    }

    public ISourceLocationRequest(@NonNull final String uri) {
        this.uri = uri;
    }

    public ISourceLocationRequest(ISourceLocation loc) {
        this(loc.getURI().toString());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ISourceLocationRequest) {
            return Objects.equals(uri, ((ISourceLocationRequest)obj).uri);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return 7 * uri.hashCode();
    }

    public String getUri() {
        return uri;
    }

}
