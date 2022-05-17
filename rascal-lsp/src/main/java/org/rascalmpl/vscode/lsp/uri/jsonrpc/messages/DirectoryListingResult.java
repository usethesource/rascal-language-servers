package org.rascalmpl.vscode.lsp.uri.jsonrpc.messages;

import java.util.Arrays;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;

public class DirectoryListingResult extends IOResult {

    private @Nullable String[] entries;
    private @Nullable boolean[] areDirectory;

    public DirectoryListingResult(int errorCode, @Nullable String errorMessage, @Nullable String[] entries, @Nullable boolean[] areDirectory) {
        super(errorCode, errorMessage);
        this.entries = entries;
        this.areDirectory = areDirectory;
    }

    public DirectoryListingResult() {}

    public String[] getEntries() {
        return entries;
    }

    public boolean[] getAreDirectory() {
        return areDirectory;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof DirectoryListingResult) {
            return super.equals(obj)
                && Objects.deepEquals(entries, ((DirectoryListingResult)obj).entries)
                && Objects.deepEquals(areDirectory, ((DirectoryListingResult)obj).areDirectory)
                ;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return super.hashCode() + 11 * (Arrays.deepHashCode(entries) + 1) + 19 * (Arrays.hashCode(areDirectory) + 1);
    }

    @Override
    public String toString() {
        return "DirectoryListingResult [entries=" + Arrays.toString(entries) + "areDirectory=" +Arrays.toString(areDirectory) + " io=" + super.toString() + "]";
    }

}
