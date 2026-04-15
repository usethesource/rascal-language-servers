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

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.PolyNull;

/**
 * Utility class for deeply nested nullable values.
 */
public class Nullables {

    private Nullables () { /* hide implicit constructor */}

    /**
     * Check a boolean value in a nullable object.
     * @param <A> The type of the containing object.
     * @param a The nullable containing object.
     * @param hasFunc The boolean getter.
     * @return True if the objects are non-null and the boolean is true, false otherwise.
     */
    public static <A> boolean has(@Nullable A a, Function<A, @Nullable Boolean> hasFunc) {
        return get(a, hasFunc, false);
    }

    /**
     * Check a boolean value nested in a nullable object hierarchy.
     * @param <A> The type of the outer containing object.
     * @param <B> The type of the inner containing object, contained in {@link A}.
     * @param a The nullable containing object.
     * @param getFunc The getter for the inner object.
     * @param hasFunc The boolean getter.
     * @return True if both objects are non-null and the boolean is true, false otherwise.
     */
    public static <A, B> boolean has(@Nullable A a, Function<A, @Nullable B> getFunc, Function<B, @Nullable Boolean> hasFunc) {
        if (a == null) {
            return false;
        }
        return has(getFunc.apply(a), hasFunc);
    }

    /**
     * Get a value from a nullable object.
     * @param <A> The type of the containing object.
     * @param <B> The type of the value.
     * @param a The nullable containing object.
     * @param getFunc The value getter.
     * @param defaultVal The value to return when either `a` or the return value of the getter is `null`.
     * @return The gotten value if everything is non-null, `defaultVal` otherwise.
     */
    public static <A, B> @PolyNull B get(@Nullable A a, Function<A, @Nullable B> getFunc, @PolyNull B defaultVal) {
        if (a == null) {
            return defaultVal;
        }
        var b = getFunc.apply(a);
        if (b == null) {
            return defaultVal;
        }
        return b;
    }

    /**
     * Get a value from a nullable object hierarchy.
     * @param <A> The type of the outer containing object.
     * @param <B> The type of the inner containing object.
     * @param <C> The type of the value to get.
     * @param a The nullable outer containing object.
     * @param getFunc1 The inner object getter.
     * @param getFunc2 The value getter.
     * @param defaultVal The value to return when the outer or inner object is null, or the value is null.
     * @return The gotten value if everything is non-null, `defaultVal` otherwise.
     */
    public static <A, B, C> @PolyNull C get(@Nullable A a, Function<A, @Nullable B> getFunc1, Function<B, @Nullable C> getFunc2, @PolyNull C defaultVal) {
        return get(get(a, getFunc1, null), getFunc2, defaultVal);
    }

    /**
     * Get a value from an object. If it was not set yet (i.e. `null`), initialize and set it before returning.
     * @param <C> The type of the containing object.
     * @param <T> The type of the value to get.
     * @param container The containing object.
     * @param getter The value getter.
     * @param setter The value setter. Only called if the value is not initialized yet.
     * @param initializer The value initializer (e.g. a constructor). Only called if the value is not initialized yet.
     * @return The gotten value, or the initialized value if was not initialized yet.
     */
    public static <C, T> T ensureNonNullAndGet(C container, Function<C, @Nullable T> getter, BiConsumer<C, T> setter, Supplier<T> initializer) {
        var t = getter.apply(container);
        if (t == null) {
            t = initializer.get();
            setter.accept(container, t);
            assert getter.apply(container) != null : "Setter should set same value as getter gets";
        }
        return t;
    }

}
