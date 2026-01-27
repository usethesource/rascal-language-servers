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

/**
 * Utility class to for common operations on keyword parameters.
 */
public class KeywordParameter {

    private KeywordParameter() { /* hide implicit constructor */ }

    /**
     * Get a keyword parameter of string type.
     * @param name The parameter name.
     * @param kws The value with keyword parameters.
     * @param defaultVal A default value to return when the parameter does not exist or is not a string.
     */
    public static @PolyNull String get(String name, IWithKeywordParameters<? extends IValue> kws, @PolyNull String defaultVal) {
        return getCastTransform(IString.class, name, kws, defaultVal, IString::getValue);
    }

    /**
     * Get a keyword parameter of boolean type.
     * @param name The parameter name.
     * @param kws The value with keyword parameters.
     * @param defaultVal A default value to return when the parameter does not exist or is not a boolean.
     */
    public static boolean get(String name, IWithKeywordParameters<? extends IValue> kws, boolean defaultVal) {
        return getCastTransform(IBool.class, name, kws, defaultVal, IBool::getValue);
    }

    /**
     * Get a keyword parameter of set type.
     * @param name The parameter name.
     * @param kws The value with keyword parameters.
     * @param defaultVal A default value to return when the parameter does not exist or is not a set.
     */
    public static @PolyNull ISet get(String name, IWithKeywordParameters<? extends IValue> kws, @PolyNull ISet defaultVal) {
        return getCastTransform(ISet.class, name, kws, defaultVal, Function.identity());
    }

    /**
     * Get a keyword parameter of list type.
     * @param name The parameter name.
     * @param kws The value with keyword parameters.
     * @param defaultVal A default value to return when the parameter does not exist or is not a list.
     */
    public static @PolyNull IList get(String name, IWithKeywordParameters<? extends IValue> kws, @PolyNull IList defaultVal) {
        return getCastTransform(IList.class, name, kws, defaultVal, Function.identity());
    }

    /**
     * Get a keyword parameter of map type.
     * @param name The parameter name.
     * @param kws The value with keyword parameters.
     * @param defaultVal A default value to return when the parameter does not exist or is not a map.
     */
    public static @PolyNull IMap get(String name, IWithKeywordParameters<? extends IValue> kws, @PolyNull IMap defaultVal) {
        return getCastTransform(IMap.class, name, kws, defaultVal, Function.identity());
    }

    /**
     * Get a keyword parameter of list type.
     * @param <T> The type of the elements in the result list.
     * @param name The parameter name.
     * @param kws The value with keyword parameters.
     * @param defaultVal A default value to return when the parameter does not exist or is not a list.
     * @param transform A function that maps Rascal list elements to Java objects of type {@link T}.
     */
    public static <T> @PolyNull List<T> get(String name, IWithKeywordParameters<? extends IValue> kws, @PolyNull List<T> defaultVal, Function<? super IValue, T> transform) {
        return get(IList.class, name, kws, defaultVal, transform, Collectors.toList());
    }

    /**
     * Get a keyword parameter of set type.
     * @param <T> The type of the elements in the result set.
     * @param name The parameter name.
     * @param kws The value with keyword parameters.
     * @param defaultVal A default value to return when the parameter does not exist or is not a set.
     * @param transform A function that maps Rascal set elements to Java objects of type {@link T}.
     */
    public static <T> @PolyNull Set<T> get(String name, IWithKeywordParameters<? extends IValue> kws, @PolyNull Set<T> defaultVal, Function<? super IValue, T> transform) {
        return get(ISet.class, name, kws, defaultVal, transform, Collectors.toSet());
    }

    /**
     * Get a keyword param of a Rascal {@link ICollection} type as a Java {@link Collection}.
     * @param <I> The type of the source Rascal collection.
     * @param <C> The type of the result Java collection.
     * @param <T> The type of the elements in the result collection.
     * @param ival The concrete collection type of the parameter.
     * @param name The name of the parameter.
     * @param kws The value with parameters.
     * @param defaultVal A default value to return when the parameter does not exist or does not match the requested type.
     * @param transform A function that maps {@link IValue}s to elements of type {@link T}.
     * @param collector A collectors that converts a stream of {@link T} to the result collection type.
     */
    public static <I extends ICollection<I>, C extends Collection<T>, T> @PolyNull C get(Class<I> ival, String name, IWithKeywordParameters<? extends IValue> kws, @PolyNull C defaultVal, Function<? super IValue, T> transform, Collector<T, ?, C> collector) {
        return getCastTransform(ival, name, kws, defaultVal, s -> s.stream().map(transform).collect(collector));
    }
    /**
     * Get a keyword parameter of loc type.
     * @param name The parameter name.
     * @param kws The value with keyword parameters.
     * @param defaultVal A default value to return when the parameter does not exist or is not a set.
     * @param cm The column map to use when converting {@link ISourceLocation} to {@link Range}.
     */
    public static @PolyNull Range get(String name, IWithKeywordParameters<? extends IValue> kws, @PolyNull Range defaultVal, LineColumnOffsetMap cm) {
        return getCastTransform(ISourceLocation.class, name, kws, defaultVal, r -> Locations.toRange(r, cm));
    }

    /**
     * Get a keyword parameter of constructor type.
     * @param <T> The type of the result value.
     * @param name The parameter name.
     * @param kws The value with parameters.
     * @param defaultVal A default value to return when the parameter does not exist or does not match the requested type.
     * @param transform A function that maps the constructor value to a {@link T}.
     */
    public static <T> @PolyNull T get(String name, IWithKeywordParameters<? extends IValue> kws, @PolyNull T defaultVal, Function<? super IConstructor, ? extends @PolyNull T> transform) {
        return getCastTransform(IConstructor.class, name, kws, defaultVal, transform);
    }

    /**
     * Get a keyword param of a Rascal value.
     * @param <V> The expected type of the parameter.
     * @param <T> The type of the result value.
     * @param ival The expected class of the parameter value.
     * @param name  The parameter name.
     * @param kws The value with parameters.
     * @param defaultVal A default value to return when the parameter does not exist or does not match the requested type.
     * @param transform A function that maps {@link IValue}s to elements of type {@link T}.
     * @return The transformed parameter value or default.
     */
    private static <V extends IValue, T> @PolyNull T getCastTransform(Class<V> ival, String name, IWithKeywordParameters<? extends IValue> kws, @PolyNull T defaultVal, Function<? super V, ? extends @PolyNull T> transform) {
        if (kws.hasParameter(name)) {
            var param = kws.getParameter(name);
            if (ival.isInstance(param)) {
                return transform.apply(ival.cast(param));
            }
        }
        return defaultVal;
    }
}
