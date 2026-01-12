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
import java.util.concurrent.Executor;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.checkerframework.checker.nullness.qual.PolyNull;

public class CompletableFutureUtils {
    private CompletableFutureUtils() {/* hidden */ }

    public static <T> CompletableFuture<T> completedFuture(T value, Executor exec) {
        return CompletableFuture.supplyAsync(() -> value, exec);
    }

    /**
     * Reduces a {@link List} of {@link CompletableFuture} to a single future that produces a list.
     * @param <T> The type of the values that the futures yield.
     * @param futures The futures to reduce.
     * @return A future that yields a list of the results of the reduced futures.
     */
    public static <T> CompletableFuture<List<T>> reduce(List<CompletableFuture<T>> futures, Executor exec) {
        return reduce(futures,
            completedFuture(new LinkedList<>(), exec),
            Collections::singletonList, // unmodifiable, but never added to
            CompletableFutureUtils::concat
        );
    }

    /**
     * Reduces a {@link List} of {@link CompletableFuture} to a single future that yields a {@link T}.
     * @param <T> The type of the values that the futures yield.
     * @param futures The futures to reduce.
     * @return A future that yields a list of the results of the reduced futures.
     * @throws IllegalArgumentException when the list of futures is empty
     */
    public static <T> CompletableFuture<T> reduce(List<CompletableFuture<T>> futures, BinaryOperator<T> merge) {
        if (futures.isEmpty()) {
            throw new IllegalArgumentException("Cannot reduce empty list without identity value.");
        }
        return reduce(futures.subList(1, futures.size()), futures.get(0), Function.identity(), merge);
    }

    /**
     * Reduces a {@link Stream} of {@link CompletableFuture} to a single future that produces a {@link Collection}.
     * @param <T> The type of the values that the futures yield.
     * @param futures The futures to reduce.
     * @return A future that yields a collection of the results of the reduced futures.
     */
    public static <T> CompletableFuture<List<T>> reduce(Stream<CompletableFuture<T>> futures, Executor exec) {
        return reduce(futures,
            completedFuture(new LinkedList<>(), exec),
            Collections::singletonList, // unmodifiable, but never added to
            CompletableFutureUtils::concat
        );
    }

    /**
     * Flattens a {@link Stream} of {@link CompletableFuture} that produces values of type {@link Iterable} to a single future that produces an {@link Iterable}.
     * @param <I> The type of the result of the futures.
     * @param futures The futures of which to reduce the result lists.
     * @param identity The identity function of {@link I}.
     * @param concat A function that merges two values of {@link I}.
     * @return A future that yields a list of all the elements in the lists from the reduced futures.
     */
    public static <I extends Iterable<?>> CompletableFuture<I> flatten(Stream<CompletableFuture<I>> futures, CompletableFuture<I> identity, BinaryOperator<I> concat) {
        return reduce(futures,
            identity,
            Function.identity(),
            concat
        );
    }

    /**
     * Reduces a {@link Stream} of {@link CompletableFuture} into a single future that yields a {@link C}.
     * @param <I> The type of the results of the input futures.
     * @param <C> The type of the result of the reduced future.
     * @param futures A {@link Stream} of futures to reduce.
     * @param identity The identity function of {@link C}.
     * @param map A function that maps an {@link I} to a {@link C}.
     * @param concat A function that merges two values of {@link C}.
     * @return A single future that, if it completes, yields the reduced result.
     *
     */
    public static <I, C> CompletableFuture<C> reduce(Stream<CompletableFuture<I>> futures,
            CompletableFuture<C> identity, Function<I, C> map, BinaryOperator<C> concat) {
        return futures
                .map(t -> t.thenApply(map))
                .reduce(identity, (lf, rf) -> lf.thenCombine(rf, concat));
    }

    /**
     * Reduces a {@link Iterable} of {@link CompletableFuture} into a single future that yields a {@link C}.
     * @param <I> The type of the results of the input futures.
     * @param <C> The type of the result of the reduced future.
     * @param futures An {@link Iterable} of futures to reduce.
     * @param identity The identity function of {@link CompletableFuture} of {@link C}.
     * @param map A function that maps an {@link I} to a {@link C}.
     * @param concat A function that merges two values of {@link C}.
     * @return A single future that, if it completes, yields the reduced result.
     */
    public static <I, C> CompletableFuture<C> reduce(Iterable<CompletableFuture<I>> futures,
            CompletableFuture<C> identity, Function<I, C> map, BinaryOperator<C> concat) {
        CompletableFuture<C> result = identity;
        for (var fut : futures) {
            result = result.thenCombine(fut, (acc, t) -> concat.apply(acc, map.apply(t)));
        }

        return result;
    }

    private static <T> List<@PolyNull T> concat(List<@PolyNull T> l, List<@PolyNull T> r) {
        if (r.isEmpty()) {
            return l;
        }
        if (l.isEmpty()) {
            return r;
        }

        var ls = new LinkedList<@PolyNull T>(l);
        ls.addAll(r);
        return ls;
    }

    /**
     * Filters a collection of items on a predicate returning a future.
     * @param <T> The type of the elements in the collection.
     * @param items The collection to filter.
     * @param predicate The future predicate to filter on.
     * @return A single future that, if it completes, yields the filtered collection.
     */
    public static <T> CompletableFuture<Collection<T>> filter(Collection<T> items, Function<T, CompletableFuture<Boolean>> predicate) {
        return reduce(
            items.stream()
                .map(i -> predicate.apply(i).thenApply(b -> b.booleanValue() ? List.of(i) : List.<T>of()))
                .collect(Collectors.toList()),
            CompletableFutureUtils::concat
        ).thenApply(Function.identity());
    }
}
