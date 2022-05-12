package org.rascalmpl.vscode.lsp.uri.jsonrpc.messages;

import java.util.Objects;
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;
import io.usethesource.vallang.ISourceLocation;

public class WriteFileRequest extends ISourceLocationRequest {

    @NonNull
    private String content;
    @NonNull
    private boolean append;

    public WriteFileRequest() {}

    public WriteFileRequest(@NonNull String uri, @NonNull String content, @NonNull boolean append) {
        super(uri);
        this.content = content;
        this.append = append;
    }

    public WriteFileRequest(ISourceLocation loc, @NonNull String content, @NonNull boolean append) {
        super(loc);
        this.content = content;
        this.append = append;
    }

    public String getContent() {
        return content;
    }

    public boolean getAppend() {
        return append;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof WriteFileRequest) {
            var other = (WriteFileRequest)obj;
            return super.equals(obj)
                && Objects.equals(content, other.content)
                && append == other.append;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return super.hashCode() + 11 * Objects.hash(content, append);
    }

}
