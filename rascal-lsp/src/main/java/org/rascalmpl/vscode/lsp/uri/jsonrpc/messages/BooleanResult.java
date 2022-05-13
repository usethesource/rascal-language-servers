package org.rascalmpl.vscode.lsp.uri.jsonrpc.messages;

import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;

public class BooleanResult extends IOResult {
    private @Nullable Boolean result;

    public BooleanResult(@NonNull int errorCode, @Nullable String errorMessage, @Nullable Boolean result) {
        super(errorCode, errorMessage);
        this.result = result;
    }

    public BooleanResult() {}

    public @Nullable Boolean getResult() {
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof BooleanResult) {
            return super.equals(obj)
                && Objects.equals(result, ((BooleanResult)obj).result);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return super.hashCode() + 11 * (Objects.hashCode(result) + 1);
    }

    @Override
    public String toString() {
        return "BooleanResult [result=" + result + " io=" + super.toString() + "]";
    }

}
