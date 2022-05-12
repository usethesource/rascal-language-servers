package org.rascalmpl.vscode.lsp.uri.jsonrpc.messages;

import java.util.Objects;
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;
import io.usethesource.vallang.ISourceLocation;

public class RenameRequest {
    @NonNull
    private String from;
    @NonNull
    private String to;
    @NonNull
    private boolean overwrite;

    public RenameRequest() {
    }

    public RenameRequest(String from, String to, boolean overwrite) {
        this.from = from;
        this.to = to;
        this.overwrite = overwrite;
    }

    public RenameRequest(ISourceLocation from, ISourceLocation to, boolean overwrite) {
        this.from = from.getURI().toString();
        this.to = to.getURI().toString();
        this.overwrite = overwrite;
    }

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }

    public boolean getOverwrite() {
        return overwrite;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof RenameRequest) {
            var other = (RenameRequest)obj;
            return Objects.equals(from, other.from)
                && Objects.equals(to, other.to)
                && overwrite == other.overwrite
                ;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(from, to, overwrite);
    }

}
