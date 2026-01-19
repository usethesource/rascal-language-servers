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
package org.rascalmpl.vscode.lsp.rascal.conversion;

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
import org.eclipse.lsp4j.RenameFile;
import org.eclipse.lsp4j.ResourceOperation;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.rascalmpl.util.locations.ColumnMaps;
import org.rascalmpl.vscode.lsp.parametric.model.RascalADTs;
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

    public static WorkspaceEdit translateDocumentChanges(IList list, final ColumnMaps columns) {
        List<Either<TextDocumentEdit, ResourceOperation>> result = new ArrayList<>(list.size());
        Map<String, ChangeAnnotation> changeAnnotations = new HashMap<>();

        for (IValue elem : list) {
            IConstructor edit = (IConstructor) elem;
            var anno = extractAnnotation(edit, changeAnnotations);

            switch (edit.getName()) {
                case RascalADTs.FileSystemChangeFields.REMOVED: {
                    var delete = new DeleteFile(getFileURI(edit, RascalADTs.FileSystemChangeFields.FILE));
                    delete.setAnnotationId(anno);
                    result.add(Either.forRight(delete));
                    break;
                }
                case RascalADTs.FileSystemChangeFields.CREATED: {
                    var create = new CreateFile(getFileURI(edit, RascalADTs.FileSystemChangeFields.FILE));
                    create.setAnnotationId(anno);
                    result.add(Either.forRight(create));
                    break;
                }
                case RascalADTs.FileSystemChangeFields.RENAMED: {
                    var rename = new RenameFile(getFileURI(edit, RascalADTs.FileSystemChangeFields.FROM), getFileURI(edit, RascalADTs.FileSystemChangeFields.TO));
                    rename.setAnnotationId(anno);
                    result.add(Either.forRight(rename));
                    break;
                }
                case RascalADTs.FileSystemChangeFields.CHANGED:
                    // TODO: file document identifier version is unknown here. that may be problematic
                    // have to extend the entire/all LSP API with this information _per_ file?
                    result.add(Either.forLeft(
                        new TextDocumentEdit(new VersionedTextDocumentIdentifier(getFileURI(edit, RascalADTs.FileSystemChangeFields.FILE), null),
                            translateTextEdits((IList) edit.get(RascalADTs.FileSystemChangeFields.EDITS), anno, columns, changeAnnotations))));
                    break;
            }
        }

        WorkspaceEdit wsEdit = new WorkspaceEdit(result);
        wsEdit.setChangeAnnotations(changeAnnotations);

        return wsEdit;
    }

    private static boolean hasAnnotation(IWithKeywordParameters<? extends IConstructor> cons) {
        return cons.hasParameter(RascalADTs.TextEditFields.LABEL)
            || cons.hasParameter(RascalADTs.TextEditFields.DESCRIPTION)
            || cons.hasParameter(RascalADTs.TextEditFields.NEEDS_CONFIRMATION);
    }

    private static @Nullable String extractAnnotation(IConstructor cons, Map<String, ChangeAnnotation> changeAnnotations) {
        var kws = cons.asWithKeywordParameters();
        if (!hasAnnotation(kws)) {
            return null;
        }

        // Mirror defaults in `util::LanguageServer`
        // Setting any of those, means setting the defaults for the remaing ones
        var label = kws.hasParameter(RascalADTs.TextEditFields.LABEL)
            ? ((IString) kws.getParameter(RascalADTs.TextEditFields.LABEL)).getValue()
            : "";
        var description = kws.hasParameter(RascalADTs.TextEditFields.DESCRIPTION)
            ? ((IString) kws.getParameter(RascalADTs.TextEditFields.DESCRIPTION)).getValue()
            : label;
        var needsConfirmation = kws.hasParameter(RascalADTs.TextEditFields.NEEDS_CONFIRMATION)
            && ((IBool) kws.getParameter(RascalADTs.TextEditFields.NEEDS_CONFIRMATION)).getValue();
        var key = String.format("%s_%s_%b", label, description, needsConfirmation);

        changeAnnotations.computeIfAbsent(key, k -> {
            var anno = new ChangeAnnotation(label);
            anno.setDescription(description);
            anno.setNeedsConfirmation(needsConfirmation);
            return anno;
        });

        return key;
    }

    public static List<TextEdit> translateTextEdits(IList edits, final ColumnMaps columns) {
        return translateTextEdits(edits, null, columns, new HashMap<>());
    }

    private static List<TextEdit> translateTextEdits(IList edits, @Nullable String parentAnno, final ColumnMaps columns, Map<String, ChangeAnnotation> changeAnnotations) {
        return edits.stream()
            .map(IConstructor.class::cast)
            .map(c -> {
                var range = Locations.toRange((ISourceLocation) c.get(RascalADTs.TextEditFields.RANGE), columns);
                var replacement = ((IString) c.get(RascalADTs.TextEditFields.REPLACEMENT)).getValue();
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

    private static String getFileURI(IConstructor edit, String label) {
        return Locations.toUri(Locations.toClientLocation((ISourceLocation) edit.get(label))).toString();
    }
}
