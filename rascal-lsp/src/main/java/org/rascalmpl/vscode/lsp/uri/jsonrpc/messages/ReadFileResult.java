package org.rascalmpl.vscode.lsp.uri.jsonrpc.messages;

import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;

public class ReadFileResult extends IOResult {

    private @Nullable String contents;

    public ReadFileResult(@NonNull int errorCode, @Nullable String errorMessage, @Nullable String contents) {
        super(errorCode, errorMessage);
        this.contents = contents;
    }

    public ReadFileResult() {}

    public String getContents() {
        return contents;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ReadFileResult) {
            return super.equals(obj)
                && Objects.equals(contents, ((ReadFileResult)obj).contents);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return super.hashCode() + 11 * (Objects.hashCode(contents) + 1);
    }

}
