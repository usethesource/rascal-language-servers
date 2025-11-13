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

import com.google.gson.JsonPrimitive;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.rascalmpl.util.locations.ColumnMaps;
import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.vscode.lsp.parametric.model.RascalADTs.CallHierarchyFields;
import org.rascalmpl.vscode.lsp.util.locations.Locations;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.type.Type;
import io.usethesource.vallang.type.TypeFactory;
import io.usethesource.vallang.type.TypeStore;

/**
 * Converts call hierarchy items from Rascal to LSP and vice versa.
 *
 * LSP requires and produces {@link CallHierarchyItem} and {@link CallHierarchyItem.Direction}.
 * In Rascal, those are modeled as {@link IConstructor}, possibly with keyword fields.
 * This class serves to convert one to the other.
 */
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

    private final Executor exec;

    public CallHierarchy(Executor exec) {
        this.exec = exec;

        var store = new TypeStore();
        Type directionAdt = TF.abstractDataType(store, "CallDirection");
        this.incoming = VF.constructor(TF.constructor(store, directionAdt, "incoming"));
        this.outgoing = VF.constructor(TF.constructor(store, directionAdt, "outgoing"));
        var callHierarchyItemAdt = TF.abstractDataType(store, "CallHierarchyItem");
        this.callHierarchyItemCons = TF.constructor(store, callHierarchyItemAdt, "callHierarchyItem",
            TF.stringType(), CallHierarchyFields.NAME,
            DocumentSymbols.getSymbolKindType(), CallHierarchyFields.KIND,
            TF.sourceLocationType(), CallHierarchyFields.DEFINITION,
            TF.sourceLocationType(), CallHierarchyFields.SELECTION
        );
    }

    public IConstructor direction(Direction dir) {
        switch (dir) {
            case INCOMING: return this.incoming;
            case OUTGOING: return this.outgoing;
            default: throw new IllegalArgumentException("Unknown call direction: " + dir.name().toLowerCase());
        }
    }

    public CallHierarchyItem toLSP(IConstructor cons, ColumnMaps columns) {
        var name = ((IString) cons.get(CallHierarchyFields.NAME)).getValue();
        var kind = DocumentSymbols.symbolKindToLSP((IConstructor) cons.get(CallHierarchyFields.KIND));
        var def = (ISourceLocation) cons.get(CallHierarchyFields.DEFINITION);
        var definitionRange = Locations.toRange(def, columns);
        var selection = (ISourceLocation) cons.get(CallHierarchyFields.SELECTION);
        var selectionRange = Locations.toRange(selection, columns);

        var ci = new CallHierarchyItem(name, kind, def.top().getURI().toString(), definitionRange, selectionRange);
        var kws = cons.asWithKeywordParameters();
        if (kws.hasParameter(CallHierarchyFields.TAGS)) {
            ci.setTags(DocumentSymbols.symbolTagsToLSP((ISet) kws.getParameter(CallHierarchyFields.TAGS)));
        }
        if (kws.hasParameter(CallHierarchyFields.DETAIL)) {
            ci.setDetail(((IString) kws.getParameter(CallHierarchyFields.DETAIL)).getValue());
        }
        if (kws.hasParameter(CallHierarchyFields.DATA)) {
            ci.setData(serializeData((IConstructor) kws.getParameter(CallHierarchyFields.DATA)));
        }

        return ci;
    }

    /**
     * Serializes the call hierarchy item data field as a {@link String}.
     * The deserialization counterpart is {@link org.rascalmpl.vscode.lsp.parametric.ILanguageContributions#parseCallHierarchyData}.
     */
    private String serializeData(IConstructor data) {
        return data.toString();
    }

    public CompletableFuture<IConstructor> toRascal(CallHierarchyItem ci, Function<String, CompletableFuture<IConstructor>> dataParser, ColumnMaps columns) {
        CompletableFuture<@Nullable IConstructor> parseData = ci.getData() != null
            ? dataParser.apply(((JsonPrimitive) ci.getData()).getAsString())
            : CompletableFuture.completedFuture(null);

        return parseData.thenApply(data -> {
            Map<String, IValue> kwArgs = new HashMap<>();
            var tags = ci.getTags();
            if (tags != null) {
                kwArgs.put(CallHierarchyFields.TAGS, DocumentSymbols.symbolTagsToRascal(tags));
            }
            var detail = ci.getDetail();
            if (detail != null) {
                kwArgs.put(CallHierarchyFields.DETAIL, VF.string(detail));
            }
            if (data != null) {
                kwArgs.put(CallHierarchyFields.DATA, data);
            }
            return VF.constructor(callHierarchyItemCons, List.of(
                VF.string(ci.getName()),
                DocumentSymbols.symbolKindToRascal(ci.getKind()),
                Locations.setRange(Locations.toLoc(ci.getUri()), ci.getRange(), columns),
                Locations.setRange(Locations.toLoc(ci.getUri()), ci.getSelectionRange(), columns)
            ).toArray(new IValue[0]), kwArgs);
        });
    }
}
