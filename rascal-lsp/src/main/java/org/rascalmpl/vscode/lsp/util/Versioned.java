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

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.checkerframework.checker.nullness.qual.PolyNull;
import org.rascalmpl.vscode.lsp.util.concurrent.CompletableFutureUtils;

public class Versioned<T> {
    private final int version;
    private final T object;
    private final long timestamp;

    public Versioned(int version, T object) {
        this(version, object, System.currentTimeMillis());
    }

    public Versioned(int version, T object, long timestamp) {
        this.version = version;
        this.object = object;
        this.timestamp = timestamp;
    }

    public int version() {
        return version;
    }

    public T get() {
        return object;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return String.format("%s [version %d]", object, version);
    }

    public static <T> AtomicReference<Versioned<T>> atomic(int version, T object) {
        return new AtomicReference<>(new Versioned<>(version, object));
    }

    public static <T> boolean replaceIfNewer(AtomicReference<@PolyNull Versioned<T>> current, Versioned<T> maybeNewer) {
        while (true) {
            var old = current.get();
            if (old == null || old.version() < maybeNewer.version()) {
                if (current.compareAndSet(old, maybeNewer)) {
                    return true;
                }
            } else {
                return false;
            }
        }
    }

    public <U> Versioned<U> map(Function<? super T, ? extends U> func) {
        return new Versioned<>(this.version, func.apply(this.object), this.timestamp);
    }

    /**
     * Debounce a certain calculation. It assumes that `debounce` is not called in parallel,
     * and that version is in always increasing monotonically.
     *
     * @param activeVersion state for all call for this debounce to share
     * @param delay the duration after which the current request for summary
     * calculation will be granted, unless another request is made in the
     * meantime (in which case the current request is abandoned)
     * @param onFire the actual calculation
     * @param onDiscard in case a debounce happened, this value will be reported on the completable future
     * @param exec executor to use for the futures
     *
     * @return a future that supplies the calculated result if the current
     * request was granted, or an debounceReplacementValue if the calcution was skipped due
     * to a new version before delay was passed.
     */
    public <U> CompletableFuture<Versioned<U>> debounce(
        AtomicReference<Versioned<T>> activeVersion, Duration delay,
        Function<T, CompletableFuture<U>> onFire, U onDiscard, Executor exec) {
        // we store our debounce start value first
        activeVersion.set(this);

        var delayed = CompletableFuture.delayedExecutor(delay.toMillis(), TimeUnit.MILLISECONDS, exec);
        return CompletableFuture.supplyAsync(activeVersion::get, delayed)
            .thenCompose(active -> {
                if (active == this) {
                    // If no new call to `debounce` has been made after `delay` has
                    // passed (i.e., `activeVersion` hasn't changed in the meantime),
                    // then run the calculation.
                    return onFire.apply(object).thenApply(u -> new Versioned<>(version, u, timestamp));
                }
                // otherwise we return an empty result
                // as a later closure will be running on this new version of `activeVersion`
                return CompletableFutureUtils.completedFuture(new Versioned<>(version, onDiscard, timestamp), exec);
            });
    }
}
