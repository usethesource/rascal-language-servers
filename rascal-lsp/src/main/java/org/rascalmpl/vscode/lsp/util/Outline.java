package org.rascalmpl.vscode.lsp.util;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.rascalmpl.uri.URIUtil;

import io.usethesource.vallang.IList;
import io.usethesource.vallang.IMap;
import io.usethesource.vallang.INode;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IWithKeywordParameters;

public class Outline {

    public static List<Either<SymbolInformation, DocumentSymbol>> buildOutlineTree(INode outline) {

        List<Either<SymbolInformation, DocumentSymbol>> result = new ArrayList<>();
        for (IValue g : outline) {
            INode group = (INode) g;
            SymbolKind kind = translateKind(group.getName());
            if (kind == null) {
                continue;
            }
            IValue arg = group.get(0);
            if (arg instanceof IList) {
                for (IValue e : (IList) arg) {
                    result.add(buildOutlineEntry(kind, (INode) e));
                }
            } else if (arg instanceof IMap) {
                ((IMap)arg).valueIterator().forEachRemaining(v -> {
                    for (IValue e: (IList)v) {
                        result.add(buildOutlineEntry(kind, (INode) e));
                    }
                });
            }
        }
        return result;
    }

    private static Either<SymbolInformation, DocumentSymbol> buildOutlineEntry( SymbolKind kind, INode element) {
        IWithKeywordParameters<? extends IValue> kwParams = element.asWithKeywordParameters();
        ISourceLocation loc = (ISourceLocation) kwParams.getParameter("loc");
        if (loc == null) {
            loc =  URIUtil.invalidLocation();
        }
        Range target = loc != null ? Locations.toRange(loc) : new Range(new Position(0,0), new Position(0,0));
        IString details = (IString) kwParams.getParameter("label");
        DocumentSymbol result;
        if (details == null) {
            result = new DocumentSymbol(element.getName(), kind, target, target);
        }
        else {
            result = new DocumentSymbol(element.getName(), kind, target, target, details.getValue());
        }
        return Either.forRight(result);
    }

    private static SymbolKind translateKind(String name) {
        switch (name) {
            case "Functions": return SymbolKind.Function;
            case "Tests": return SymbolKind.Method;
            case "Variables": return SymbolKind.Variable;
            case "Aliases": return SymbolKind.Class;
            case "Data": return SymbolKind.Struct;
            case "Tags": return SymbolKind.Property;
            case "Imports": return null;
            case "Syntax": return SymbolKind.Interface;
        }
        return null;
    }
}
