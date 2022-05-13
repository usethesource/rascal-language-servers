package org.rascalmpl.vscode.lsp.uri.jsonrpc.messages;

import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;

public class IOResult {
    @NonNull
    private int errorCode;

    @Nullable
    private String errorMessage;

    public IOResult() {
    }

    public IOResult(@NonNull int errorCode, @Nullable String errorMessage) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public @Nullable String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof IOResult) {
            var other = (IOResult)obj;
            return errorCode == other.errorCode
                && Objects.equals(errorMessage, other.errorMessage);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return errorCode
            + 7 * (errorMessage == null ? 4 : errorMessage.hashCode());
    }

    @Override
    public String toString() {
        return "IOResult [errorCode=" + errorCode + ", errorMessage=" + errorMessage + "]";
    }

}
