/*
 * Copyright (c) 2018-2023, NWO-I CWI and Swat.engineering
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
package org.rascalmpl.vscode.lsp.util.locations;

import java.io.IOException;
import java.net.URISyntaxException;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.uri.URIUtil;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValue;

public class Locations {
    public static ISourceLocation toPhysicalIfPossible(ISourceLocation loc) {
        ISourceLocation physical;
        try {
            physical = URIResolverRegistry.getInstance().logicalToPhysical(loc);
            if (physical == null) {
                return loc;
            }
            return physical;
        } catch (IOException e) {
            return loc;
        }
    }

    public static ISourceLocation toLoc(TextDocumentItem doc) {
        return toLoc(doc.getUri());
    }

    public static ISourceLocation toLoc(TextDocumentIdentifier doc) {
        return toLoc(doc.getUri());
    }

    public static ISourceLocation toLoc(String uri) {
        try {
            return URIUtil.createFromURI(uri);
        } catch (UnsupportedOperationException e) {
            if (e.getMessage().contains("Opaque URI schemes are not supported")) {
                int colonPos = uri.indexOf(':');
                try {
                    return URIUtil.createFromURI(uri.substring(0, colonPos) + ":///" + uri.substring(colonPos));
                } catch (URISyntaxException e1) {
                    throw new RuntimeException(e);
                }
            }
            else {
                throw e;
            }
        }
        catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public static Location mapValueToLocation(IValue v, ColumnMaps cm) {
        if (v instanceof ISourceLocation) {
            return Locations.toLSPLocation((ISourceLocation)v, cm);
        }
        throw new RuntimeException(v + "is not a ISourceLocation");
    }

    public static Location toLSPLocation(ISourceLocation sloc, ColumnMaps cm) {
        return new Location(sloc.getURI().toString(), toRange(sloc, cm));
    }

    public static Range toRange(ISourceLocation sloc, ColumnMaps cm) {
        return toRange(sloc, cm.get(sloc));
    }

    public static Range toRange(ISourceLocation sloc, LineColumnOffsetMap map) {
        if (sloc.hasLineColumn()) {
            return new Range(
                toPosition(sloc, map, false),
                toPosition(sloc, map, true)
            );
        }
        else {
            return new Range(new Position(0, 0), new Position(0,0));
        }
    }

    public static Position toPosition(ISourceLocation loc, ColumnMaps cm) {
        return toPosition(loc, cm, false);
    }

    public static Position toPosition(ISourceLocation loc, ColumnMaps cm, boolean atEnd) {
        return toPosition(loc, cm.get(loc), atEnd);
    }

    public static Position toPosition(ISourceLocation loc, LineColumnOffsetMap map, boolean atEnd) {
        var line = atEnd ? loc.getEndLine() : loc.getBeginLine();
        var column = atEnd? loc.getEndColumn() : loc.getBeginColumn();
        line -= 1; // lines in LSP are 0 based, IValue are 1 based
        return new Position(line, map.translateColumn(line, column, atEnd));
    }


}
