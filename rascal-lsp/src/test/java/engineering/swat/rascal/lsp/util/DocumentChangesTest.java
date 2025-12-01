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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.AnnotatedTextEdit;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.junit.Test;
import org.rascalmpl.util.locations.ColumnMaps;
import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.vscode.lsp.util.DocumentChanges;
import org.rascalmpl.vscode.lsp.util.locations.Locations;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
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

    private static final ISourceLocation TEST_LOC = VF.sourceLocation("foo.rsc", 0, 0, 1, 1, 0, 0);

    private static final Type editType = TF.abstractDataType(store, "TextEdit");
    private static final Type replaceCons = TF.constructor(store, editType, "replace", TF.sourceLocationType(), "range", TF.stringType(), "replacement");
    private static final Type changeType = TF.abstractDataType(store, "FileSystemChange");
    private static final Type changedCons = TF.constructor(store, changeType, "changed", TF.sourceLocationType(), "file", TF.listType(editType), "edits");

    private IConstructor replace(ISourceLocation loc, String replacement) {
        return replace(loc, replacement, null, null, null);
    }

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

        return VF.constructor(replaceCons, new IValue[] {loc, VF.string(replacement)}, kwArgs);
    }

    private IConstructor change(ISourceLocation file, String... replacements) {
        return change(file, null, null, null, Arrays.stream(replacements)
            .map(r -> replace(file, r, null, null, null))
            .toArray(size -> new IConstructor[size]));
    }

    private IConstructor change(ISourceLocation file, IConstructor... edits) {
        return change(file, null, null, null, edits);
    }

    private IConstructor change(ISourceLocation file, @Nullable String label, @Nullable String description, @Nullable Boolean needsConfirmation, IConstructor[] edits) {
        Map<String, IValue> kws = new HashMap<>();
        if (label != null) {
            kws.put("label", VF.string(label));
        }
        if (description != null) {
            kws.put("description", VF.string(description));
        }
        if (needsConfirmation != null) {
            kws.put("needsConfirmation", VF.bool(needsConfirmation));
        }

        return VF.constructor(changedCons, file.top(), VF.list(edits))
            .asWithKeywordParameters()
            .setParameters(kws);
    }

    @Test
    public void basicEdits() {
        var rascalEdits = VF.list(change(TEST_LOC, "a", "b", "c"));
        assertEditStructure(rascalEdits, Map.of(Locations.toUri(TEST_LOC).toString(), new String[] {"a", "b", "c"}));
    }

    @Test
    public void individualAnnotations() {
        var rascalEdits = VF.list(change(TEST_LOC, replace(TEST_LOC, "a"), replace(TEST_LOC, "b", "bar", "barDesc", true), replace(TEST_LOC, "c")));
        var wsEdit = DocumentChanges.translateDocumentChanges(rascalEdits, columns);
        assertEditStructure(rascalEdits, Map.of(Locations.toUri(TEST_LOC).toString(), new String[] {"a", "b", "c"}));

        var docEdit = wsEdit.getDocumentChanges().get(0).getLeft();
        assertNotAnnotated(docEdit.getEdits().get(0));
        assertAnnotated(docEdit.getEdits().get(1), "bar", "barDesc", true, wsEdit);
        assertNotAnnotated(docEdit.getEdits().get(2));
    }

    @Test
    public void pushAnnotationsDown() {
        var rascalEdits = VF.list(change(TEST_LOC, "foo", null, null, new IConstructor[] {replace(TEST_LOC, "a"), replace(TEST_LOC, "b"), replace(TEST_LOC, "c")}));
        var wsEdit = DocumentChanges.translateDocumentChanges(rascalEdits, columns);
        assertEditStructure(rascalEdits, Map.of(Locations.toUri(TEST_LOC).toString(), new String[] {"a", "b", "c"}));

        var docEdit = wsEdit.getDocumentChanges().get(0).getLeft();
        for (var e : docEdit.getEdits()) {
            assertAnnotated(e, "foo", "foo", false, wsEdit);
        }
    }

    @Test
    public void keepAnnotationsOnIndividualEdits() {
        var rascalEdits = VF.list(change(TEST_LOC, "foo", null, null, new IConstructor[] {replace(TEST_LOC, "a"), replace(TEST_LOC, "b", "bar", "barDesc", true), replace(TEST_LOC, "c")}));
        var wsEdit = DocumentChanges.translateDocumentChanges(rascalEdits, columns);
        assertEditStructure(rascalEdits, Map.of(Locations.toUri(TEST_LOC).toString(), new String[] {"a", "b", "c"}));

        var docEdit = wsEdit.getDocumentChanges().get(0).getLeft();
        assertAnnotated(docEdit.getEdits().get(0), "foo", "foo", false, wsEdit);
        assertAnnotated(docEdit.getEdits().get(1), "bar", "barDesc", true, wsEdit);
        assertAnnotated(docEdit.getEdits().get(2), "foo", "foo", false, wsEdit);
    }

    // Utility methods

    private void assertNotAnnotated(TextEdit e) {
        assertFalse(String.format("Edit that replaces '%s' should not be annotated", e.getNewText()), e instanceof AnnotatedTextEdit);
    }

    private void assertAnnotated(TextEdit e, String label, String description, boolean needsConfirmation, WorkspaceEdit wsEdit) {
        assertTrue(String.format("Edit that replaces '%s' should be annotated", e.getNewText()), e instanceof AnnotatedTextEdit);
        var annoId = ((AnnotatedTextEdit) e).getAnnotationId();
        var anno = wsEdit.getChangeAnnotations().get(annoId);

        assertEquals(label, anno.getLabel());
        assertEquals(description, anno.getDescription());
        assertEquals(needsConfirmation, anno.getNeedsConfirmation());
    }

    private void assertEditStructure(IList rascalEdits, Map<String, String[]> edits) {
        var wsEdit = DocumentChanges.translateDocumentChanges(rascalEdits, columns);
        assertEquals(edits.size(), wsEdit.getDocumentChanges().size());

        var expectedEdits = new HashMap<>(edits);
        for (var either : wsEdit.getDocumentChanges()) {
            var docEdit = either.getLeft();
            var expected = expectedEdits.remove(docEdit.getTextDocument().getUri());
            assertNotNull("Unexpected TextDocumentEdit for " + docEdit.getTextDocument().getUri(), expected);
            assertEquals(expected.length, docEdit.getEdits().size());
            assertArrayEquals(expected, docEdit.getEdits().stream().map(TextEdit::getNewText).collect(Collectors.toList()).toArray());
        }
        assertTrue(String.format("Expected edits for %s, but got none", expectedEdits.keySet()), expectedEdits.isEmpty());
    }
}
