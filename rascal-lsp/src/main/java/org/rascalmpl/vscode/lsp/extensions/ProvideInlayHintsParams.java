/*
 * Copyright (c) 2018-2021, NWO-I CWI and Swat.engineering
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
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
