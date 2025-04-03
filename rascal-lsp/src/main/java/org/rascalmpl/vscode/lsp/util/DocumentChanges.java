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
import org.rascalmpl.vscode.lsp.IBaseTextDocumentService;
import org.rascalmpl.vscode.lsp.util.locations.LineColumnOffsetMap;
import org.rascalmpl.vscode.lsp.util.locations.Locations;

import io.usethesource.vallang.IBool;
import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.IMap;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.ITuple;
import io.usethesource.vallang.IValue;

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

            switch (edit.getName()) {
                case "removed":
                    result.add(Either.forRight(new DeleteFile(getFileURI(edit, "file"))));
                    break;
                case "created":
                    result.add(Either.forRight(new CreateFile(getFileURI(edit, "file"))));
                    break;
                case "renamed":
                    result.add(Either.forRight(new RenameFile(getFileURI(edit, "from"), getFileURI(edit, "to"))));
                    break;
                case "changed":
                    // TODO: file document identifier version is unknown here. that may be problematic
                    // have to extend the entire/all LSP API with this information _per_ file?
                    result.add(Either.forLeft(
                        new TextDocumentEdit(new VersionedTextDocumentIdentifier(getFileURI(edit, "file"), null),
                            translateTextEdits(docService, (IList) edit.get("edits"), changeAnnotations))));
                    break;
            }
        }

        WorkspaceEdit wsEdit = new WorkspaceEdit(result);
        wsEdit.setChangeAnnotations(changeAnnotations);

        return wsEdit;
    }

    private static List<TextEdit> translateTextEdits(final IBaseTextDocumentService docService, IList edits, Map<String, ChangeAnnotation> changeAnnotations) {
        return edits.stream()
            .map(IConstructor.class::cast)
            .map(c -> {
                var range = locationToRange(docService, (ISourceLocation) c.get("range"));
                var replacement = ((IString) c.get("replacement")).getValue();
                // Check annotation
                var kw = c.asWithKeywordParameters();
                if (kw.hasParameter("annotation")) {
                    var anno = (IConstructor) kw.getParameter("annotation");
                    var label = ((IString) anno.get("label")).getValue();
                    var description = ((IString) anno.get("description")).getValue();
                    var needsConfirmation = ((IBool) anno.asWithKeywordParameters().getParameter("needsConfirmation")).getValue();
                    var annoKey = String.format("%s_%s_%b", label, description, needsConfirmation);

                    if (!changeAnnotations.containsKey(annoKey)) {
                        var annotation = new ChangeAnnotation(label);
                        annotation.setDescription(description);;
                        annotation.setNeedsConfirmation(needsConfirmation);
                        changeAnnotations.put(annoKey, annotation);
                    }
                    return new AnnotatedTextEdit(range, replacement, annoKey);
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
        return ((ISourceLocation) edit.get(label)).getURI().toString();
    }

    private static Map<String, ChangeAnnotation> translateChangeAnnotations(IMap annos) {
        return annos.stream()
            .map(ITuple.class::cast)
            .map(entry -> {
                String annoId = ((IString) entry.get(0)).getValue();
                ChangeAnnotation anno = new ChangeAnnotation();
                IConstructor c = (IConstructor) entry.get(1);
                anno.setLabel(((IString) c.get("label")).getValue());
                anno.setDescription(((IString) c.get("description")).getValue());
                anno.setNeedsConfirmation(((IBool) c.get("needsConfirmation")).getValue());
                return Map.entry(annoId, anno);
            })
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
