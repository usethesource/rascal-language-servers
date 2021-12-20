package org.rascalmpl.vscode.lsp.extensions;

import java.util.Objects;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;

public class InlayHint {
    @NonNull
    private final String label;

    @NonNull
    private final Range range;

    @MonotonicNonNull
    private final String category;

    @NonNull
    private final boolean before;



    public InlayHint(String label, Range range, @Nullable String category, boolean before) {
        this.label = label;
        this.range = range;
        this.category = category;
        this.before = before;
    }

    public @Nullable String getCategory() {
        return category;
    }

    public String getLabel() {
        return label;
    }

    public Range getRange() {
        return range;
    }

    public boolean isBefore() {
        return before;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof InlayHint) {
            InlayHint other = (InlayHint)obj;
            return other.label.equals(label)
                && other.range.equals(range)
                && Objects.equals(other.category, category)
                && other.before == before
                ;
        }
        return false;
    }
    @Override
    public int hashCode() {
        return Objects.hash(label, range, category, before);
    }

}
