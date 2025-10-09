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
package org.rascalmpl.vscode.lsp.util.concurrent;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.rascalmpl.values.IRascalValueFactory;

import io.usethesource.vallang.IList;

public class CompletableFutureUtils {
    private CompletableFutureUtils() {/* hidden */ }

    /**
     * Combines a {@link List} of futures as a single future that produces a list.
     * @param <T> The type of the values that the futures yield.
     * @param futures The futures to combine.
     * @return A future that yields a list of the results of the combined futures.
     */
    public static <T> CompletableFuture<List<T>> combineAll(List<CompletableFuture<T>> futures) {
        return combineAll(futures,
            LinkedList::new, // fast concatenation
            Collections::singletonList, // unmodifiable, but never added to
            CompletableFutureUtils::concat
        );
    }

    /**
     * Combines a {@link Stream} of futures as a single future that produces a {@link Collection}.
     * @param <T> The type of the values that the futures yield.
     * @param futures The futures to combine.
     * @return A future that yields a collection of the results of the combined futures.
     */
    public static <T> CompletableFuture<List<T>> combineAll(Stream<CompletableFuture<T>> futures) {
        return combineAll(futures, LinkedList::new, Collections::singletonList, CompletableFutureUtils::concat);
    }

    /**
     * Flattens a {@link Stream} of futures that produces values of type {@link IList} as a single future that produces an {@link IList}.
     * @param futures The futures of which to combine the result lists.
     * @return A future that yields a list of all the elements in the lists from the combined futures.
     */
    public static CompletableFuture<IList> flattenAll(Stream<CompletableFuture<IList>> futures) {
        return combineAll(futures,
            IRascalValueFactory.getInstance()::list,
            Function.identity(),
            IList::concat
        );
    }

    /**
     * Combines a {@link Stream} of {@link CompletableFuture} into a single future that yields a {@link C}.
     * @param <I> The type of the results of the input futures.
     * @param <C> The type of the result of the combined future.
     * @param futures A {@link Stream} of futures to combine.
     * @param identity The identity function of {@link C}.
     * @param wrap A function that converts an {@link I} to an {@link C}.
     * @param concat A function that merges two values of {@link C}.
     * @return A single future that, if it completes, yields the combined result.
     */
    public static <I, C> CompletableFuture<C> combineAll(Stream<CompletableFuture<I>> futures,
            Supplier<C> identity, Function<I, C> wrap, BinaryOperator<C> concat) {
        return futures
                .map(t -> t.thenApply(wrap))
                .reduce(CompletableFuture.completedFuture(identity.get()),
                        (lf, rf) -> lf.thenCombine(rf, concat));
    }

    public static <I, C> CompletableFuture<C> combineAll(Iterable<CompletableFuture<I>> futures,
            Supplier<C> identity, Function<I, C> wrap, BinaryOperator<C> concat) {
        CompletableFuture<C> result = CompletableFuture.completedFuture(identity.get());
        for (var fut : futures) {
            result = result.thenCombine(fut, (acc, t) -> concat.apply(acc, wrap.apply(t)));
        }

        return result;
    }

    private static <T> List<T> concat(Collection<T> l, Collection<T> r) {
        var ls = new LinkedList<>(l);
        ls.addAll(r);
        return ls;
    }
}
