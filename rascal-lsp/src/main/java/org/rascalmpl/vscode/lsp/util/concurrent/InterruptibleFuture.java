/*
 * Copyright (c) 2018-2023, NWO-I CWI and Swat.engineering
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
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

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
            interrupted = true;
            interrupt.run();
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

    public <U, V> InterruptibleFuture<V> thenCombineAsync(
        CompletableFuture<? extends U> other,
        BiFunction<? super T, ? super U, ? extends V> fn, Executor executor) {
        return new InterruptibleFuture<>(future.thenCombineAsync(other, fn, executor), interrupt);
    }

    public static <T> InterruptibleFuture<T> completedFuture(T result) {
        return new InterruptibleFuture<>(CompletableFuture.completedFuture(result), () -> {});
    }

    /**
     * Turn an completable future with a interruptible future inside into a
     * normal interruptible future by inlining them
     */
    public static <T> InterruptibleFuture<T> flatten(CompletableFuture<InterruptibleFuture<T>> f, Executor exec) {
        return new InterruptibleFuture<>(
            f.thenCompose(InterruptibleFuture::get),
            () -> f.thenAcceptAsync(InterruptibleFuture::interrupt, exec) // schedule interrupt async so that we don't deadlock during interrupt
        );
    }

}
