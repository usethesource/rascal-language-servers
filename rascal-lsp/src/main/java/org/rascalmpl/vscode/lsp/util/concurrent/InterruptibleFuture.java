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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.checkerframework.checker.nullness.qual.PolyNull;
import org.rascalmpl.values.IRascalValueFactory;

import io.usethesource.vallang.IList;

public class InterruptibleFuture<T> {

    private final CompletableFuture<T> future;
    private final Runnable interrupt;
    private volatile boolean interrupted = false;

    public InterruptibleFuture(CompletableFuture<T> future, Runnable interrupt) {
        this.future = future;
        this.interrupt = interrupt;
    }

    public CompletableFuture<T> get() {
        return future;
    }

    public void interrupt() {
        if (!future.isDone()) {
            interrupt.run();
            interrupted = true;
        }
    }

    public boolean isInterrupted() {
        return interrupted;
    }

    public <U> InterruptibleFuture<U> thenApply(Function<T, U> func) {
        return new InterruptibleFuture<>(future.thenApply(func), interrupt);
    }

    public <U> InterruptibleFuture<U> thenApplyAsync(Function<T, U> func, Executor exec) {
        return new InterruptibleFuture<>(future.thenApplyAsync(func, exec), interrupt);
    }

    public InterruptibleFuture<Void> thenAccept(Consumer<T> func) {
        return new InterruptibleFuture<>(future.thenAccept(func), interrupt);
    }

    public CompletableFuture<Void> thenAcceptIfUninterrupted(Consumer<? super T> action) {
        return future.thenAccept(t -> {
            if (!interrupted) {
                action.accept(t);
            }
        });
    }

    public <U> CompletableFuture<Void> thenAcceptBothIfUninterrupted(InterruptibleFuture<? extends U> other, BiConsumer<? super T, ? super U> action) {
        return future.thenAcceptBoth(other.future, (t, u) -> {
            if (!interrupted && !other.interrupted) {
                action.accept(t, u);
            }
        });
    }

    public <U, V> InterruptibleFuture<V> thenCombineAsync(
        CompletableFuture<? extends U> other,
        BiFunction<? super T, ? super U, ? extends V> fn, Executor executor) {
        return new InterruptibleFuture<>(future.thenCombineAsync(other, fn, executor), interrupt);
    }

    public <U, V> InterruptibleFuture<V> thenCombineAsync(
        InterruptibleFuture<? extends U> other,
        BiFunction<? super T, ? super U, ? extends V> fn, Executor executor) {
        return new InterruptibleFuture<>(future.thenCombineAsync(other.get(), fn, executor), () -> {
            interrupt();
            other.interrupt();
        });
    }

    public static <T> InterruptibleFuture<T> completedFuture(T result) {
        return new InterruptibleFuture<>(CompletableFuture.completedFuture(result), () -> {});
    }

    /**
     * Turn an completable future with a interruptible future inside into a
     * normal interruptible future by inlining them
     */
    public static <T> InterruptibleFuture<@PolyNull T> flatten(CompletableFuture<InterruptibleFuture<@PolyNull T>> f, Executor exec) {
        return new InterruptibleFuture<>(
            f.<@PolyNull T>thenCompose(InterruptibleFuture::get),
            () -> f.thenAcceptAsync(InterruptibleFuture::interrupt, exec) // schedule interrupt async so that we don't deadlock during interrupt
        );
    }

    /**
     * Flattens an {@link Iterable} of {@link InterruptibleFuture} that produces values of type {@link IList} to a single future that produces an {@link IList}.
     * @param futures The futures of which to reduce the result lists.
     * @param exec The {@link Executor} to execute the future on.
     * @return A future that yields a list of all the elements in the lists from the reduced futures.
     */
    public static InterruptibleFuture<IList> flatten(Iterable<InterruptibleFuture<IList>> futures, Executor exec) {
        return flatten(futures,
            IRascalValueFactory.getInstance()::list,
            IList::concat,
            exec
        );
    }

    /**
     * Flattens an {@link Iterable} of {@link InterruptibleFuture} that produces values of type {@link Iterable} to a single future that produces an {@link Iterable}.
     * @param <I> The type of the result of the futures.
     * @param futures The futures of which to reduce the result lists.
     * @param identity The identity function of {@link I}.
     * @param concat A function that merges two values of {@link I}.
     * @param exec The {@link Executor} to execute the future on.
     * @return A future that yields a list of all the elements in the lists from the reduced futures.
     */
    public static <I extends Iterable<?>> InterruptibleFuture<I> flatten(Iterable<InterruptibleFuture<I>> futures, Supplier<? extends I> identity, BiFunction<? super I, ? super I, ? extends I> concat, Executor exec) {
        return reduce(futures,
            identity,
            Function.identity(),
            concat,
            exec
        );
    }

    /**
     * Reduces a {@link Iterable} of {@link InterruptibleFuture} into a single future that yields a {@link C}.
     * @param <I> The type of the results of the input futures.
     * @param <C> The type of the result of the reduced future.
     * @param futures An {@link Iterable} of futures to reduce.
     * @param identity The identity function of {@link C}.
     * @param map A function that maps an {@link I} to a {@link C}.
     * @param concat A function that merges two values of {@link C}.
     * @return A single future that, if it completes, yields the reduced result.
     */
    public static <I, C> InterruptibleFuture<C> reduce(Iterable<InterruptibleFuture<I>> futures,
            Supplier<? extends C> identity, Function<? super I, ? extends C> map, BiFunction<? super C, ? super C, ? extends C> concat, Executor exec) {
        InterruptibleFuture<C> result = InterruptibleFuture.completedFuture(identity.get());
        for (var fut : futures) {
            result = result.thenCombineAsync(fut, (acc, t) -> concat.apply(acc, map.apply(t)), exec);
        }

        return result;
    }

}
