package org.rascalmpl.vscode.lsp.util;

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

    public static Location toLSPLocation(ISourceLocation sloc) {
        return new Location(sloc.getURI().toString(), toRange(sloc));
    }

    public static Range toRange(ISourceLocation sloc) {
        return new Range(new Position(sloc.getBeginLine() - 1, sloc.getBeginColumn()), new Position(sloc.getEndLine() - 1, sloc.getEndColumn()));
    }

}
