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

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.SymbolTag;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.rascalmpl.util.locations.LineColumnOffsetMap;
import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.vscode.lsp.util.locations.Locations;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IWithKeywordParameters;
import io.usethesource.vallang.type.Type;
import io.usethesource.vallang.type.TypeFactory;
import io.usethesource.vallang.type.TypeStore;

public class DocumentSymbols {
    private static final IRascalValueFactory VF = IRascalValueFactory.getInstance();
    private static final TypeFactory TF = TypeFactory.getInstance();
    private static final TypeStore store = new TypeStore();

    private static final Type symbolKindAdt = TF.abstractDataType(store, "DocumentSymbolKind");
    private static final Type symbolTagAdt = TF.abstractDataType(store, "DocumentSymbolTag");

    // hide constructor for static class
    private DocumentSymbols() {}

    private static String capitalize(String kindName) {
        return kindName.substring(0,1).toUpperCase() + kindName.substring(1);
    }

    /**
     * Converts a list of Rascal DocumentSymbols (@see util::IDE) to LSP DocumentSymbols
     * @param symbols list of Rascal DocumentSymbols
     * @param om      line/column offset map
     * @return list of LSP DocumentSymbols
     */
    public static List<Either<SymbolInformation, DocumentSymbol>> toLSP(IList symbols, LineColumnOffsetMap om) {
        return symbols.stream()
                .map(s -> toLSP((IConstructor) s, om))
                .map(Either::<SymbolInformation, DocumentSymbol>forRight)
                .collect(Collectors.toList());
    }

    /**
     * Converts a constructor tree of Rascal type DocumentSymbol (@see util::IDE) to an LSP DocumentSymbol
     * @param symbol IConstructor of Rascal type DocumentSymbol
     * @param om     line/column offset map
     * @return an LSP DocumentSymbol
     */
    public static DocumentSymbol toLSP(IConstructor symbol, final LineColumnOffsetMap om) {
        IWithKeywordParameters<?> kwp = symbol.asWithKeywordParameters();

        List<DocumentSymbol> children = KeywordParameter.get("children", kwp, Collections.emptyList(), c -> toLSP((IConstructor) c, om));
        SymbolKind kind = symbolKindToLSP((IConstructor) symbol.get("kind"));
        String symbolName = ((IString) symbol.get("name")).getValue();
        Range range = Locations.toRange((ISourceLocation) symbol.get("range"), om);
        Range selection = KeywordParameter.get("selection", kwp, range, om);
        String detail = KeywordParameter.get("detail", kwp, symbolName); // LSP default for detail is name
        List<SymbolTag> tags = KeywordParameter.get("tags", kwp, Collections.emptyList(), DocumentSymbols::symbolTagToLSP);

        var lspSymbol = new DocumentSymbol(symbolName, kind, range, selection, detail, children);
        lspSymbol.setTags(tags); // since 3.16
        return lspSymbol;
    }

    public static SymbolKind symbolKindToLSP(IConstructor kind) {
        return SymbolKind.valueOf(capitalize(kind.getName()));
    }

    public static IConstructor symbolKindToRascal(SymbolKind kind) {
        return VF.constructor(TF.constructor(store, symbolKindAdt, kind.name().toLowerCase()));
    }

    public static List<SymbolTag> symbolTagsToLSP(@Nullable ISet tags) {
        if (tags == null) {
            return Collections.emptyList();
        }
        return tags.stream().map(DocumentSymbols::symbolTagToLSP).collect(Collectors.toList());
    }

    static SymbolTag symbolTagToLSP(IValue tag) {
        var name = ((IConstructor) tag).getName();
        return SymbolTag.valueOf(DocumentSymbols.capitalize(name));
    }

    public static ISet symbolTagsToRascal(@Nullable List<SymbolTag> tags) {
        if (tags == null) {
            return VF.set();
        }
        return tags.stream()
            .map(t -> VF.constructor(TF.constructor(store, symbolTagAdt, t.name().toLowerCase())))
            .collect(VF.setWriter());
    }

    public static Type getSymbolKindType() {
        return symbolKindAdt;
    }

    public static TypeStore getStore() {
        return store;
    }
}
