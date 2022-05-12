package org.rascalmpl.vscode.lsp.uri.jsonrpc.messages;

import java.util.Arrays;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;

public class DirectoryListingResult extends IOResult {

    private @Nullable String[] entries;

    public DirectoryListingResult(int errorCode, @Nullable String errorMessage, @Nullable String[] entries) {
        super(errorCode, errorMessage);
        this.entries = entries;
    }

    public DirectoryListingResult() {}

    public String[] getEntries() {
        return entries;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof DirectoryListingResult) {
            return super.equals(obj)
                && Objects.deepEquals(entries, ((DirectoryListingResult)obj).entries);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return super.hashCode() + 11 * (Arrays.deepHashCode(entries) + 1);
    }

}
