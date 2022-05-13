package org.rascalmpl.vscode.lsp.uri.jsonrpc.messages;

import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;

public class TimestampResult extends IOResult {
    private @Nullable Long timestamp;

    public TimestampResult(int errorCode, @Nullable String errorMessage, @Nullable Long timestamp) {
        super(errorCode, errorMessage);
        this.timestamp = timestamp;
    }

    public TimestampResult() {}

    public @Nullable Long getTimestamp() {
        return timestamp;
    }

    @Override
    public int hashCode() {
        return super.hashCode() + 11 * (Objects.hashCode(timestamp) + 1);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TimestampResult) {
            return super.equals(obj)
                && Objects.equals(timestamp, ((TimestampResult)obj).timestamp);
        }
        return false;
    }

    @Override
    public String toString() {
        return "TimestampResult [timestamp=" + timestamp + "io= " + super.toString() + "]";
    }


}
