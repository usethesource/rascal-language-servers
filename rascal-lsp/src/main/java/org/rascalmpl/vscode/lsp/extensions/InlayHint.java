package org.rascalmpl.vscode.lsp.extensions;

import java.util.Objects;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;

public class InlayHint {
    @NonNull
    private final String label;

    @NonNull
    private final Position position;

    @MonotonicNonNull
    private final String category;


    public InlayHint(String label, Position position, @Nullable String category) {
        this.label = label;
        this.position = position;
        this.category = category;
    }

    public @Nullable String getCategory() {
        return category;
    }

    public String getLabel() {
        return label;
    }
    public Position getPosition() {
        return position;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof InlayHint) {
            InlayHint other = (InlayHint)obj;
            return other.label.equals(label)
                && other.position.equals(position)
                && Objects.equals(other.category, category)
                ;
        }
        return false;
    }
    @Override
    public int hashCode() {
        return Objects.hash(label, position, category);
    }

}
