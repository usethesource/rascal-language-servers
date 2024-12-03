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

import java.util.concurrent.atomic.AtomicReference;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

import org.checkerframework.checker.nullness.qual.Nullable;

public class DebouncedSupplier<T> {
    private final Supplier<CompletableFuture<T>> supplier;
    private final AtomicReference<CompletableFuture<@Nullable CompletableFuture<T>>> latest;

    public DebouncedSupplier(Supplier<CompletableFuture<T>> supplier) {
        this.supplier = supplier;
        this.latest = new AtomicReference<>(CompletableFuture.completedFuture(null));
    }

    /**
     * Gets the result of `supplier` immediately. Previous uncompleted gets are
     * completed.
     */
    public CompletableFuture<T> get() {
        return get(Duration.ZERO);
    }

    /**
     * Gets the result of `supplier` with the given debouncing delay.
     */
    public CompletableFuture<T> get(Duration delay) {
        return get(delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * Gets the result of `supplier` with the given timeout, as follows:
     *   - if the next get *doesn't* happen before the given timeout, then the
     *     current get completes with the result of `supplier` upon timeout;
     *   - if the next get *does* happen before the given timeout, then the
     *     current get completes with the result of the next get upon
     *     availability.
     */
    public CompletableFuture<T> get(long timeout, TimeUnit unit) {
        var newLatest = new CompletableFuture<CompletableFuture<T>>();
        var oldLatest = latest.getAndSet(newLatest);

        var newLatestResult = newLatest
            .thenApply(result -> result == null ? supplier.get() : result)
            .thenCompose(Function.identity());

        // Complete the current get with the result of `supplier` upon timeout.
        // Complete the previous get with the result of the current get upon
        // availability.
        newLatest.completeOnTimeout(null, timeout, unit);
        oldLatest.complete(newLatestResult);

        return newLatestResult;
    }
}
