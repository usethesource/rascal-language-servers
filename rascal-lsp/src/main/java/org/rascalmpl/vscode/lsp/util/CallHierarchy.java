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
import java.util.List;
import java.util.Map;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.vscode.lsp.util.locations.ColumnMaps;
import org.rascalmpl.vscode.lsp.util.locations.Locations;

import com.google.gson.JsonPrimitive;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.exceptions.FactTypeUseException;
import io.usethesource.vallang.io.StandardTextReader;
import io.usethesource.vallang.type.Type;
import io.usethesource.vallang.type.TypeFactory;
import io.usethesource.vallang.type.TypeStore;

public class CallHierarchy {
    private static final IRascalValueFactory VF = IRascalValueFactory.getInstance();
    private static final TypeFactory TF = TypeFactory.getInstance();

    public enum Direction {
        INCOMING,
        OUTGOING
    }

    private final IConstructor incoming;
    private final IConstructor outgoing;

    private final Type callHierarchyItemCons;
    private final @Nullable Type callHierarchyDataAdt;

    private static final String NAME = "name";
    private static final String KIND = "kind";
    private static final String DEFINITION = "src";
    private static final String SELECTION = "selection";
    private static final String TAGS = "tags";
    private static final String DETAIL = "detail";
    private static final String DATA = "data";
    private final TypeStore store;


    public CallHierarchy(TypeStore store) {
        this.store = store;
        Type directionAdt = store.lookupAbstractDataType("CallDirection");
        this.incoming = VF.constructor(store.lookupConstructor(directionAdt, "incoming", TF.tupleEmpty()));
        this.outgoing = VF.constructor(store.lookupConstructor(directionAdt, "outgoing", TF.tupleEmpty()));
        this.callHierarchyItemCons = store.lookupConstructor(store.lookupAbstractDataType("CallHierarchyItem"), "callHierarchyItem").iterator().next(); // first and only
        this.callHierarchyDataAdt = store.lookupAbstractDataType("CallHierarchyData");
    }

    public static IConstructor direction(TypeStore store, CallHierarchy.Direction direction) {
        var ch = new CallHierarchy(store);
        return ch.direction(direction);
    }

    public IConstructor direction(Direction dir) {
        switch (dir) {
            case INCOMING: return this.incoming;
            case OUTGOING: return this.outgoing;
            default: throw new IllegalArgumentException();
        }
    }

    public CallHierarchyItem toLSP(IConstructor cons, ColumnMaps columns) {
        var name = cons.get(NAME).toString();
        var kind = DocumentSymbols.symbolKindToLSP((IConstructor) cons.get(KIND));
        var def = (ISourceLocation) cons.get(DEFINITION);
        var definitionRange = Locations.toRange(def, columns);
        var selection = (ISourceLocation) cons.get(SELECTION);
        var selectionRange = Locations.toRange(selection, columns);

        var ci = new CallHierarchyItem(name, kind, def.top().getURI().toString(), definitionRange, selectionRange);
        var kws = cons.asWithKeywordParameters();
        if (kws.hasParameter(TAGS)) {
            ci.setTags(DocumentSymbols.symbolTagsToLSP((ISet) kws.getParameter(TAGS)));
        }
        if (kws.hasParameter(DETAIL)) {
            ci.setDetail(kws.getParameter(DETAIL).toString());
        }
        if (kws.hasParameter(DATA)) {
            ci.setData(serializeData((IConstructor) kws.getParameter(DATA)));
        }

        return ci;
    }

    private String serializeData(IConstructor data) {
        return data.toString();
    }

    private IConstructor deserializeData(Object data) {
        try {
            return (IConstructor) new StandardTextReader().read(VF, store, callHierarchyDataAdt, new StringReader(((JsonPrimitive) data).getAsString()));
        } catch (FactTypeUseException | IOException e) {
            throw new IllegalArgumentException("The call hierarchy item data could not be parsed", e);
        }
    }

    public static IConstructor toRascal(TypeStore store, CallHierarchyItem source, ColumnMaps columns) {
        var ch = new CallHierarchy(store);
        return ch.toRascal(source, columns);
    }

    public IConstructor toRascal(CallHierarchyItem ci, ColumnMaps columns) {
        return VF.constructor(callHierarchyItemCons, List.of(
            VF.string(ci.getName()),
            DocumentSymbols.symbolKindToRascal(ci.getKind()),
            Locations.setRange(Locations.toLoc(ci.getUri()), ci.getRange(), columns),
            Locations.setRange(Locations.toLoc(ci.getUri()), ci.getSelectionRange(), columns)
        ).toArray(new IValue[0]), Map.of(
            TAGS, DocumentSymbols.symbolTagsToRascal(ci.getTags()),
            DETAIL, VF.string(ci.getDetail()),
            DATA, deserializeData(ci.getData())
        ));
    }
}
