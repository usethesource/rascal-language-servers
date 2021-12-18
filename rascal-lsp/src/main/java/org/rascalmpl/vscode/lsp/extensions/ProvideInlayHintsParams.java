package org.rascalmpl.vscode.lsp.extensions;

import com.google.common.base.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;

public class ProvideInlayHintsParams {
    @NonNull
    private TextDocumentIdentifier textDocument;

    @Nullable
    private Range range;

    public ProvideInlayHintsParams() {}

    public ProvideInlayHintsParams(TextDocumentIdentifier textDocument, @Nullable Range range) {
        this.textDocument = textDocument;
        this.range = range;
    }

    public TextDocumentIdentifier getTextDocument() {
        return textDocument;
    }

    public @Nullable Range getRange() {
        return range;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ProvideInlayHintsParams) {
            ProvideInlayHintsParams other = (ProvideInlayHintsParams)obj;
            return Objects.equal(other.textDocument, textDocument) && Objects.equal(other.range, range);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(textDocument, range);
    }


}
