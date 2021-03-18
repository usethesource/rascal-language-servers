package org.rascalmpl.vscode.lsp.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.vscode.lsp.util.locations.LineColumnOffsetMap;
import org.rascalmpl.vscode.lsp.util.locations.Locations;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.IMap;
import io.usethesource.vallang.INode;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IWithKeywordParameters;

public class Outline {

    public static List<Either<SymbolInformation, DocumentSymbol>> buildRascalOutlineTree(INode outline, LineColumnOffsetMap om) {

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
                    result.add(buildOutlineEntry(kind, (INode) e, om));
                }
            }
            else if (arg instanceof IMap) {
                ((IMap) arg).valueIterator().forEachRemaining(v -> {
                    for (IValue e : (IList) v) {
                        result.add(buildOutlineEntry(kind, (INode) e, om));
                    }
                });
            }
        }
        return result;
    }

    private static Either<SymbolInformation, DocumentSymbol> buildOutlineEntry(SymbolKind kind, INode element, LineColumnOffsetMap om) {
        IWithKeywordParameters<? extends IValue> kwParams = element.asWithKeywordParameters();
        ISourceLocation loc = (ISourceLocation) kwParams.getParameter("loc");
        if (loc == null) {
            loc = URIUtil.invalidLocation();
        }
        Range target = loc != null ? Locations.toRange(loc, om) : new Range(new Position(0, 0), new Position(0, 0));
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
            case "Functions":
                return SymbolKind.Function;
            case "Tests":
                return SymbolKind.Method;
            case "Variables":
                return SymbolKind.Variable;
            case "Aliases":
                return SymbolKind.Class;
            case "Data":
                return SymbolKind.Struct;
            case "Tags":
                return SymbolKind.Property;
            case "Imports":
                return null;
            case "Syntax":
                return SymbolKind.Interface;
        }
        return null;
    }

    private static String capitalize(String kindName) {
       return kindName.substring(0,1).toUpperCase() + kindName.substring(1);
    }

    public static List<Either<SymbolInformation, DocumentSymbol>> buildParametricOutline(IList symbols, LineColumnOffsetMap om) {
        return symbols.stream()
                .map(s -> buildParametricOutline((IConstructor) s, om))
                .map(r -> Either.<SymbolInformation, DocumentSymbol>forRight(r))
                .collect(Collectors.toList());
    }

    public static DocumentSymbol buildParametricOutline(IConstructor symbol, final LineColumnOffsetMap om) {
        IWithKeywordParameters<?> kwp = symbol.asWithKeywordParameters();

        List<DocumentSymbol> children = kwp.hasParameter("children") ? 
            ((IList) kwp.getParameter("children"))
                .stream()
                .map(c -> buildParametricOutline((IConstructor) c, om))
                .collect(Collectors.toList())
            : Collections.emptyList();

        String kindName = ((IConstructor) symbol.get("kind")).getName();
        SymbolKind kind = SymbolKind.valueOf(capitalize(kindName));
        String symbolName = ((IString) symbol.get("name")).getValue();
        Range range = Locations.toRange((ISourceLocation) symbol.get("range"), om);
        Range selection = kwp.hasParameter("selection") 
            ? Locations.toRange(((ISourceLocation) kwp.getParameter("selection")), om) 
            : range;
        String detail = kwp.hasParameter("detail") ? ((IString) kwp.getParameter("detail")).getValue() : symbolName;

        return new  DocumentSymbol(symbolName, kind, range, selection, detail, children);
    }
}
