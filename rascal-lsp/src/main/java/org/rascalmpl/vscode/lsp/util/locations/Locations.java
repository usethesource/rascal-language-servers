package org.rascalmpl.vscode.lsp.util.locations;

import java.net.URISyntaxException;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.rascalmpl.uri.URIUtil;

import io.usethesource.vallang.ISourceLocation;

public class Locations {
    public static ISourceLocation toLoc(TextDocumentItem doc) {
        return toLoc(doc.getUri());
    }

    public static ISourceLocation toLoc(TextDocumentIdentifier doc) {
        return toLoc(doc.getUri());
    }

    public static ISourceLocation toLoc(String uri) {
        try {
            return URIUtil.createFromURI(uri);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public static Location toLSPLocation(ISourceLocation sloc, ColumnMaps cm) {
        return new Location(sloc.getURI().toString(), toRange(sloc, cm));
    }

    public static Range toRange(ISourceLocation sloc, ColumnMaps cm) {
        return toRange(sloc, cm.get(sloc));
    }

    public static Range toRange(ISourceLocation sloc, LineColumnOffsetMap map) {
        return new Range(
            toPosition(sloc.getBeginLine() - 1, sloc.getBeginColumn(), map, false),
            toPosition(sloc.getEndLine() - 1, sloc.getEndColumn(), map, true)
        );
    }

    public static Position toPosition(int line, int column, LineColumnOffsetMap map, boolean atEnd) {
        return new Position(line, map.translateColumn(line, column, atEnd));
    }

}
