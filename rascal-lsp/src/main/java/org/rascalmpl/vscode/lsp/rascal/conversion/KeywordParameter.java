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

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import org.checkerframework.checker.nullness.qual.PolyNull;
import org.eclipse.lsp4j.Range;
import org.rascalmpl.util.locations.LineColumnOffsetMap;
import org.rascalmpl.vscode.lsp.util.locations.Locations;

import io.usethesource.vallang.IBool;
import io.usethesource.vallang.ICollection;
import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.IMap;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IWithKeywordParameters;

public class KeywordParameter {

    private KeywordParameter() { /* hide implicit constructor */ }

    public static @PolyNull String get(String name, IWithKeywordParameters<? extends IValue> kws, @PolyNull String defaultVal) {
        return getCastTransform(IString.class, name, kws, defaultVal, IString::getValue);
    }

    public static boolean get(String name, IWithKeywordParameters<? extends IValue> kws, boolean defaultVal) {
        return getCastTransform(IBool.class, name, kws, defaultVal, IBool::getValue);
    }

    public static @PolyNull ISet get(String name, IWithKeywordParameters<? extends IValue> kws, @PolyNull ISet defaultVal) {
        return getCastTransform(ISet.class, name, kws, defaultVal, Function.identity());
    }

    public static @PolyNull IList get(String name, IWithKeywordParameters<? extends IValue> kws, @PolyNull IList defaultVal) {
        return getCastTransform(IList.class, name, kws, defaultVal, Function.identity());
    }

    public static @PolyNull IMap get(String name, IWithKeywordParameters<? extends IValue> kws, @PolyNull IMap defaultVal) {
        return getCastTransform(IMap.class, name, kws, defaultVal, Function.identity());
    }

    public static <T> @PolyNull List<T> get(String name, IWithKeywordParameters<? extends IValue> kws, @PolyNull List<T> defaultVal, Function<? super IValue, T> transform) {
        return get(IList.class, name, kws, defaultVal, transform, Collectors.toList());
    }

    public static <T> @PolyNull Set<T> get(String name, IWithKeywordParameters<? extends IValue> kws, @PolyNull Set<T> defaultVal, Function<? super IValue, T> transform) {
        return get(ISet.class, name, kws, defaultVal, transform, Collectors.toSet());
    }

    public static <T, C extends Collection<T>, I extends ICollection<I>> @PolyNull C get(Class<I> ival, String name, IWithKeywordParameters<? extends IValue> kws, @PolyNull C defaultVal, Function<? super IValue, T> transform, Collector<T, ?, C> collector) {
        return getCastTransform(ival, name, kws, defaultVal, s -> s.stream().map(transform).collect(collector));
    }

    public static @PolyNull Range get(String name, IWithKeywordParameters<? extends IValue> kws, @PolyNull Range defaultVal, LineColumnOffsetMap cm) {
        return getCastTransform(ISourceLocation.class, name, kws, defaultVal, r -> Locations.toRange(r, cm));
    }

    public static <T> @PolyNull T get(String name, IWithKeywordParameters<? extends IValue> kws, @PolyNull T defaultVal, Function<? super IConstructor, ? extends @PolyNull T> transform) {
        return getCastTransform(IConstructor.class, name, kws, defaultVal, transform);
    }

    private static <T, V extends IValue> @PolyNull T getCastTransform(Class<V> ival, String name, IWithKeywordParameters<? extends IValue> kws, @PolyNull T defaultVal, Function<? super V, ? extends @PolyNull T> transform) {
        if (kws.hasParameter(name)) {
            var param = kws.getParameter(name);
            if (ival.isInstance(param)) {
                return transform.apply(ival.cast(param));
            }
        }
        return defaultVal;
    }
}
