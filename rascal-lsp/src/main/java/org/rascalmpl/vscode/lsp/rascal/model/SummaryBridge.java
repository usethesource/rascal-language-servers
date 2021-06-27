/*
 * Copyright (c) 2018-2021, NWO-I CWI and Swat.engineering
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
package org.rascalmpl.vscode.lsp.rascal.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.values.ValueFactoryFactory;
import org.rascalmpl.vscode.lsp.util.Lazy;
import org.rascalmpl.vscode.lsp.util.locations.ColumnMaps;
import org.rascalmpl.vscode.lsp.util.locations.IRangeMap;
import org.rascalmpl.vscode.lsp.util.locations.Locations;
import org.rascalmpl.vscode.lsp.util.locations.impl.TreeMapLookup;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IMap;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.ITuple;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IWithKeywordParameters;
import io.usethesource.vallang.type.Type;
import io.usethesource.vallang.type.TypeFactory;
import io.usethesource.vallang.type.TypeStore;

public class SummaryBridge {
    private static final ISet EMPTY_SET = ValueFactoryFactory.getValueFactory().set();
    private static final IMap EMPTY_MAP = ValueFactoryFactory.getValueFactory().map();
    private static final IConstructor EMPTY_SUMMARY;

    static {
        TypeFactory TF = TypeFactory.getInstance();
        TypeStore TS = new TypeStore();
        Type summaryCons = TF.constructor(TS, TF.abstractDataType(TS, "Summary"), "summary");
        EMPTY_SUMMARY = IRascalValueFactory.getInstance().constructor(summaryCons);
    }

    private final IWithKeywordParameters<? extends IConstructor> data;
    private final Lazy<IRangeMap<List<Location>>> definitions;
    private final Lazy<IRangeMap<String>> typeNames;


    public SummaryBridge() {
        this.data = EMPTY_SUMMARY.asWithKeywordParameters();
        this.definitions = TreeMapLookup::new;
        this.typeNames = TreeMapLookup::new;
    }

    public SummaryBridge(IConstructor summary, ColumnMaps cm) {
        this.data = summary.asWithKeywordParameters();
        definitions = Lazy.defer(() -> translateRelation(getKWFieldSet(data, "useDef"), v -> Locations.toLSPLocation((ISourceLocation)v, cm), cm));
        typeNames = Lazy.defer(() -> translateMap(getKWFieldMap(data, "locationTypes"), v -> ((IString)v).getValue(), cm));

    }

    private static <T> IRangeMap<List<T>> translateRelation(ISet binaryRel, Function<IValue, T> valueMapper, ColumnMaps cm) {
        TreeMapLookup<List<T>> result = new TreeMapLookup<>();
        for (IValue v: binaryRel) {
            ITuple row = (ITuple)v;
            Range from = Locations.toRange((ISourceLocation)row.get(0), cm);
            T to = valueMapper.apply(row.get(1));
            List<T> existing = result.getExact(from);
            if (existing == null) {
                // most cases there is only a single entry, to so save a lot of memory, we store a singleton list to start with
                result.put(from, Collections.singletonList(to));
            }
            else if (existing.size() == 1) {
                // we had a singleton list in there, so let's replace it with a regular last
                existing = new ArrayList<>(existing);
                result.put(from, existing);
                existing.add(to);
            }
            else {
                existing.add(to);
            }
        }
        return result;
    }

    private static <T> IRangeMap<T> translateMap(IMap binaryMap, Function<IValue, T> valueMapper, ColumnMaps cm) {
        TreeMapLookup<T> result = new TreeMapLookup<>();
        binaryMap.entryIterator().forEachRemaining(e -> {
            Range from = Locations.toRange((ISourceLocation)e.getKey(), cm);
            T to = valueMapper.apply(e.getValue());
            result.put(from, to);
        });
        return result;
    }

    private static ISet getKWFieldSet(IWithKeywordParameters<? extends IConstructor> data, String name) {
        if (data.hasParameter(name)) {
            return (ISet) data.getParameter(name);
        }
        return EMPTY_SET;
    }

    private static IMap getKWFieldMap(IWithKeywordParameters<? extends IConstructor> data, String name) {
        if (data.hasParameter(name)) {
            return (IMap) data.getParameter(name);
        }
        return EMPTY_MAP;
    }

    private static <T> T replaceNull(@Nullable T value, T defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return value;
    }

    public List<Location> getDefinition(Position cursor) {
        return getDefinition(new Range(cursor, cursor));
    }

    public List<Location> getDefinition(Range cursor) {
        return replaceNull(definitions.get().lookup(cursor), Collections.emptyList());
    }

    public String getTypeName(Position cursor) {
        return getTypeName(new Range(cursor, cursor));
    }

    public String getTypeName(Range cursor) {
        return replaceNull(typeNames.get().lookup(cursor), "");
    }
}
