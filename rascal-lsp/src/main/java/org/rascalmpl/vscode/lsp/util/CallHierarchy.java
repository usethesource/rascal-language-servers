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

import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.lsp4j.CallHierarchyItem;
import org.rascalmpl.interpreter.NullRascalMonitor;
import org.rascalmpl.library.lang.json.internal.JsonValueReader;
import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.vscode.lsp.util.locations.ColumnMaps;
import org.rascalmpl.vscode.lsp.util.locations.Locations;

import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.type.Type;
import io.usethesource.vallang.type.TypeFactory;
import io.usethesource.vallang.type.TypeStore;

public class CallHierarchy {
    private static final IRascalValueFactory VF = IRascalValueFactory.getInstance();
    private static final TypeFactory TF = TypeFactory.getInstance();
    private static final TypeStore store = new TypeStore(DocumentSymbols.getStore());

    private static final Type directionAdt = TF.abstractDataType(store, "CallDirection");

    public static final IConstructor INCOMING = VF.constructor(TF.constructor(store, directionAdt, "incoming"));
    public static final IConstructor OUTGOING = VF.constructor(TF.constructor(store, directionAdt, "outgoing"));

    private static final Type callHierarchyItemAdt = TF.abstractDataType(store, "CallHierarchyItem");
    private static final Type callHierarchyItemCons = TF.constructor(store, callHierarchyItemAdt, "callHierarchyItem",
        TF.stringType(), DocumentSymbols.getSymbolKindType(), TF.sourceLocationType(), TF.sourceLocationType());

    private static final String NAME = "name";
    private static final String KIND = "kind";
    private static final String DEFINITION = "src";
    private static final String SELECTION = "selection";
    private static final String TAGS = "tags";
    private static final String DETAIL = "detail";
    private static final String DATA = "data";

    private CallHierarchy() { /* hide constructor */}

    static IConstructor lastCallItem = null;

    public static CallHierarchyItem toLSP(IConstructor cons, ColumnMaps columns) {
        var name = cons.get(NAME).toString();
        var kind = DocumentSymbols.symbolKindToLSP((IConstructor) cons.get(KIND));
        var def = (ISourceLocation) cons.get(DEFINITION);
        var definitionRange = Locations.toRange(def, columns);
        var selection = (ISourceLocation) cons.get(SELECTION);
        var selectionRange = Locations.toRange(selection, columns);

        var ci = new CallHierarchyItem(name, kind, def.top().getURI().toString(), definitionRange, selectionRange);
        var kws = cons.asWithKeywordParameters();
        ci.setTags(kws.hasParameter(TAGS)
            ? DocumentSymbols.symbolTagsToLSP((ISet) kws.getParameter(TAGS))
            : Collections.emptyList());
        ci.setDetail(kws.hasParameter(DETAIL)
            ? kws.getParameter(DETAIL).toString()
            : "");
        ci.setData(kws.hasParameter(DATA)
            ? kws.getParameter(DATA)
            : VF.tuple());

        lastCallItem = cons;

        return ci;
    }

    public static IConstructor toRascal(CallHierarchyItem ci, ColumnMaps columns) {
        JsonValueReader reader = new JsonValueReader(VF, store, new NullRascalMonitor(), null);
        IValue data;
        try {
            data = reader.read(new JsonReader(new StringReader(ci.getData().toString())), TF.valueType());
        } catch (IOException e) {
            data = VF.tuple();
        }
        if (data == null) data = VF.tuple();

        final var cons = VF.constructor(callHierarchyItemCons,
            VF.string(ci.getName()),
            DocumentSymbols.symbolKindToRascal(ci.getKind()),
            Locations.setRange(Locations.toLoc(ci.getUri()), ci.getRange(), columns),
            Locations.setRange(Locations.toLoc(ci.getUri()), ci.getSelectionRange(), columns)
        );

        final var consWithArgs = cons.asWithKeywordParameters().setParameters(Map.of(
            TAGS, DocumentSymbols.symbolTagsToRascal(ci.getTags()),
            DETAIL, VF.string(ci.getDetail()),
            DATA, data
        ));

        if (consWithArgs.equals(lastCallItem)) {
            System.out.println("Conversion succeeded for " + consWithArgs.get(NAME));
        } else {
            System.out.println("Conversion not equal...");
        }

        return consWithArgs;
    }
}
