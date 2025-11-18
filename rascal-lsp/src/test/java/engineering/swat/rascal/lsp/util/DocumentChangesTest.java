/*
 * Copyright (c) 2018-2025, NWO-I CWI and Swat.engineering
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
package engineering.swat.rascal.lsp.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.TextEdit;
import org.junit.Test;
import org.rascalmpl.util.locations.ColumnMaps;
import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.vscode.lsp.util.DocumentChanges;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.type.Type;
import io.usethesource.vallang.type.TypeFactory;
import io.usethesource.vallang.type.TypeStore;

public class DocumentChangesTest {
    private static final IRascalValueFactory VF = IRascalValueFactory.getInstance();
    private static final TypeStore store = new TypeStore();
    private static final TypeFactory TF = TypeFactory.getInstance();

    private static final ColumnMaps columns = new ColumnMaps(l -> "");

    private ISourceLocation randomSourceLocation() {
        return VF.sourceLocation("foo.rsc", 0, 0, 1, 1, 0, 0);
    }

    private static final Type editType = TF.abstractDataType(store, "TextEdit");
    private static final Type replaceCons = TF.constructor(store, editType, "replace", TF.sourceLocationType(), "range", TF.stringType(), "replacement");
    private static final Type changeType = TF.abstractDataType(store, "FileSystemChange");
    private static final Type changedCons = TF.constructor(store, changeType, "changed", TF.sourceLocationType(), "file", TF.listType(editType), "edits");

    private IConstructor replace(ISourceLocation loc, String replacement, @Nullable String label, @Nullable String description, @Nullable Boolean needsConfirmation) {
        Map<String, IValue> kwArgs = new HashMap<>();
        if (label != null) {
            kwArgs.put("label", VF.string(label));
        }
        if (description != null) {
            kwArgs.put("description", VF.string(description));
        }
        if (needsConfirmation != null) {
            kwArgs.put("needsConfirmation", VF.bool(needsConfirmation));
        }


        return VF.constructor(replaceCons, new IValue[] {randomSourceLocation(), VF.string(replacement)}, kwArgs);
    }

    private IConstructor change(ISourceLocation file, String... replacements) {
        return change(file, Arrays.stream(replacements).map(r -> replace(file, r, null, null, null)).toArray(s -> new IConstructor[s]));
    }

    private IConstructor change(ISourceLocation file, IConstructor... replacements) {
        return change(file, null, null, null, replacements);
    }

    private IConstructor change(ISourceLocation file, @Nullable String label, @Nullable String description, @Nullable Boolean needsConfirmation, IConstructor... edits) {
        return VF.constructor(changedCons, file.top(), VF.list(edits));
    }

    @Test
    public void basicEdits() {
        var rascalEdits = VF.list(change(randomSourceLocation(), "a", "b", "c"));
        var wsEdit = DocumentChanges.translateDocumentChanges(rascalEdits, columns);
        assertEquals(1, wsEdit.getDocumentChanges().size());

        var docEdit = wsEdit.getDocumentChanges().get(0).getLeft();
        assertEquals(randomSourceLocation().getURI().toString(), docEdit.getTextDocument().getUri());
        assertEquals(3, docEdit.getEdits().size());
        assertArrayEquals(new String [] {"a", "b", "c"}, docEdit.getEdits().stream().map(TextEdit::getNewText).collect(Collectors.toList()).toArray());
    }
}
