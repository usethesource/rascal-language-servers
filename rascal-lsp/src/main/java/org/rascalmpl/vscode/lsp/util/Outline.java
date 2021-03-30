package org.rascalmpl.vscode.lsp.util;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.rascalmpl.vscode.lsp.util.locations.LineColumnOffsetMap;
import org.rascalmpl.vscode.lsp.util.locations.Locations;
import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.IWithKeywordParameters;

public class Outline {

    private static String capitalize(String kindName) {
       return kindName.substring(0,1).toUpperCase() + kindName.substring(1);
    }

    /**
     * Converts a list of Rascal DocumentSymbols (@see util::IDE) to LSP DocumentSymbols
     * @param symbols list of Rascal DocumentSymbols
     * @param om line/column offset map
     * @return list of LSP DocumentSymbols
     */
    public static List<Either<SymbolInformation, DocumentSymbol>> buildOutline(IList symbols, LineColumnOffsetMap om) {
        return symbols.stream()
                .map(s -> buildParametricOutline((IConstructor) s, om))
                .map(Either::<SymbolInformation, DocumentSymbol>forRight)
                .collect(Collectors.toList());
    }

    /**
     * Converts a constructor tree of Rascal type DocumentSymbol from util::IDE to an LSP DocumentSymbol
     * @param symbol IConstructor of Rascal type DocumentSymbol
     * @param om     line/column offset map
     * @return a DocumentSymbol
     */
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
        String detail = kwp.hasParameter("detail") ? ((IString) kwp.getParameter("detail")).getValue() : null;

        return new  DocumentSymbol(symbolName, kind, range, selection, detail, children);
    }
}
