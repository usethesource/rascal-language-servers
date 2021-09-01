package org.rascalmpl.vscode.lsp.util;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.CreateFile;
import org.eclipse.lsp4j.DeleteFile;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.RenameFile;
import org.eclipse.lsp4j.ResourceOperation;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.rascalmpl.vscode.lsp.IBaseLanguageClient;
import org.rascalmpl.vscode.lsp.IBaseTextDocumentService;
import org.rascalmpl.vscode.lsp.util.locations.LineColumnOffsetMap;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.IValue;

/**
 * Translates Rascal data-type representation of document edits to the LSP representation.
 * Note that here we map unicode codepoint (column) offsets to the 16-bit character encoding of the LSP (VScode,Java,Javascript)
 *
 * TODO: document versions feature
 */
public class DocumentChanges {
    private final IBaseTextDocumentService docService;

    public DocumentChanges(IBaseTextDocumentService docService) {
        this.docService = docService;
    }

    public List<Either<TextDocumentEdit, ResourceOperation>> translateDocumentChanges(IList list) {
        List<Either<TextDocumentEdit, ResourceOperation>> result = new ArrayList<>(list.size());

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
                            translateTextEdits((IList) edit.get("edits")))));
                    break;
            }
        }

        return result;
    }

    private List<TextEdit> translateTextEdits(IList edits) {
        return edits.stream()
            .map(e -> (IConstructor) e)
            .map(c -> new TextEdit(locationToRange((ISourceLocation) c.get("range")), ((IString) c.get("replacement")).getValue()))
            .collect(Collectors.toList());
    }

    private Range locationToRange(ISourceLocation loc) {
        LineColumnOffsetMap columnMap = docService.getColumnMap(loc);
        int beginLine = loc.getBeginLine();
        int endLine = loc.getEndLine();
        int beginColumn = loc.getBeginColumn();
        int endColumn = loc.getEndColumn();

        return new Range(new Position(beginLine, columnMap.translateColumn(beginLine, beginColumn, false)),
                         new Position(endLine, columnMap.translateColumn(endLine, endColumn, true)));
    }

    private static String getFileURI(IConstructor edit, String label) {
        return ((ISourceLocation) edit.get(label)).getURI().toString();
    }

}
