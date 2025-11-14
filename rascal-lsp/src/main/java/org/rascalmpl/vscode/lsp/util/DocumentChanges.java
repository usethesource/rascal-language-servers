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
package org.rascalmpl.vscode.lsp.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.AnnotatedTextEdit;
import org.eclipse.lsp4j.ChangeAnnotation;
import org.eclipse.lsp4j.CreateFile;
import org.eclipse.lsp4j.DeleteFile;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.RenameFile;
import org.eclipse.lsp4j.ResourceOperation;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.rascalmpl.util.locations.LineColumnOffsetMap;
import org.rascalmpl.vscode.lsp.IBaseTextDocumentService;
import org.rascalmpl.vscode.lsp.util.locations.Locations;

import io.usethesource.vallang.IBool;
import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IWithKeywordParameters;

/**
 * Translates Rascal data-type representation of document edits to the LSP representation.
 * Note that here we map unicode codepoint (column) offsets to the 16-bit character encoding of the LSP (VScode,Java,Javascript)
 *
 * TODO: document versions feature
 */
public class DocumentChanges {
    private DocumentChanges() { }

    public static WorkspaceEdit translateDocumentChanges(final IBaseTextDocumentService docService, IList list) {
        List<Either<TextDocumentEdit, ResourceOperation>> result = new ArrayList<>(list.size());
        Map<String, ChangeAnnotation> changeAnnotations = new HashMap<>();

        for (IValue elem : list) {
            IConstructor edit = (IConstructor) elem;
            var anno = extractAnnotation(edit, changeAnnotations);

            switch (edit.getName()) {
                case "removed": {
                    var delete = new DeleteFile(getFileURI(edit, "file"));
                    delete.setAnnotationId(anno);
                    result.add(Either.forRight(delete));
                    break;
                }
                case "created": {
                    var create = new CreateFile(getFileURI(edit, "file"));
                    create.setAnnotationId(anno);
                    result.add(Either.forRight(create));
                    break;
                }
                case "renamed": {
                    var rename = new RenameFile(getFileURI(edit, "from"), getFileURI(edit, "to"));
                    rename.setAnnotationId(anno);
                    result.add(Either.forRight(rename));
                    break;
                }
                case "changed":
                    // TODO: file document identifier version is unknown here. that may be problematic
                    // have to extend the entire/all LSP API with this information _per_ file?
                    result.add(Either.forLeft(
                        new TextDocumentEdit(new VersionedTextDocumentIdentifier(getFileURI(edit, "file"), null),
                            translateTextEdits(docService, (IList) edit.get("edits"), anno, changeAnnotations))));
                    break;
            }
        }

        WorkspaceEdit wsEdit = new WorkspaceEdit(result);
        wsEdit.setChangeAnnotations(changeAnnotations);

        return wsEdit;
    }

    private static boolean hasAnnotation(IWithKeywordParameters<? extends IConstructor> cons) {
        return cons.hasParameter("label")
            || cons.hasParameter("description")
            || cons.hasParameter("needsConfirmation");
    }

    private static @Nullable String extractAnnotation(IConstructor cons, Map<String, ChangeAnnotation> changeAnnotations) {
        var kws = cons.asWithKeywordParameters();
        if (!hasAnnotation(kws)) {
            return null;
        }

        // Mirror defaults in `util::LanguageServer`
        var label = kws.hasParameter("label")
            ? ((IString) kws.getParameter("label")).getValue()
            : "";
        var description = kws.hasParameter("description")
            ? ((IString) kws.getParameter("description")).getValue()
            : label;
        var needsConfirmation = kws.hasParameter("needsConfirmation")
            ? ((IBool) kws.getParameter("needsConfirmation")).getValue()
            : false;
        var key = String.format("%s_%s_%b", label, description, needsConfirmation);

        if (!changeAnnotations.containsKey(key)) {
            var anno = new ChangeAnnotation(label);
            anno.setDescription(description);
            anno.setNeedsConfirmation(needsConfirmation);
            changeAnnotations.put(key, anno);
        }

        return key;
    }

    private static List<TextEdit> translateTextEdits(final IBaseTextDocumentService docService, IList edits, @Nullable String parentAnno, Map<String, ChangeAnnotation> changeAnnotations) {
        return edits.stream()
            .map(IConstructor.class::cast)
            .map(c -> {
                var range = locationToRange(docService, (ISourceLocation) c.get("range"));
                var replacement = ((IString) c.get("replacement")).getValue();
                var anno = extractAnnotation(c, changeAnnotations);
                if (anno == null) {
                    // If this edit has no annotation, inherit from its parent.
                    anno = parentAnno;
                }
                if (anno != null) {
                    return new AnnotatedTextEdit(range, replacement, anno);
                }
                return new TextEdit(range, replacement);
            })
            .collect(Collectors.toList());
    }

    public static Range locationToRange(final IBaseTextDocumentService docService, ISourceLocation loc) {
        LineColumnOffsetMap columnMap = docService.getColumnMap(loc);
        return Locations.toRange(loc, columnMap);
    }

    private static String getFileURI(IConstructor edit, String label) {
        return Locations.toClientLocation((ISourceLocation) edit.get(label)).getURI().toString();
    }
}
