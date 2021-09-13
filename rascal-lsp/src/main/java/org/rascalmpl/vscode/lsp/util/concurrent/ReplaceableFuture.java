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
package org.rascalmpl.vscode.lsp.util.concurrent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A wrapper around CompletableFuture's that allow for us to replace the results of a still running future, and call an
 * specific closure if it is replaced by a newer version.
 */
public class ReplaceableFuture<T> {
    private static final Logger logger = LogManager.getLogger(ReplaceableFuture.class);

    private final AtomicReference<Runnable> interrupt;
    private final AtomicReference<CompletableFuture<T>> actual;

    public ReplaceableFuture(InterruptibleFuture<T> start) {
        this(start.get());
        this.interrupt.set(start::interrupt);
    }

    public ReplaceableFuture(CompletableFuture<T> start) {
        AtomicReference<CompletableFuture<T>> actRef = new AtomicReference<>(start);
        actRef.set(wrap(start, actRef));
        actual = actRef;
        interrupt = new AtomicReference<>(() -> {
        });
    }

    /**
     * Wrap a completable future with one that can be replaced but the newer version. We swallow exceptions if indeed
     * it's replaced by a more up to date version
     *
     * @param <T>
     * @param original
     * @param current
     * @return
     */
    private static <T> CompletableFuture<T> wrap(CompletableFuture<T> original,
        AtomicReference<CompletableFuture<T>> current) {
        // we use this self pointer to be able to compare to ourself from within the future
        AtomicReference<@Nullable CompletableFuture<T>> self = new AtomicReference<>();
        CompletableFuture<T> result = original.handle((r, t) -> {
            CompletableFuture<T> activeFuture = current.get();
            CompletableFuture<T> actualSelf = self.get();
            if (actualSelf != null && activeFuture != actualSelf) {
                // someone ran past us, so we have to join on that result
                try {
                    return activeFuture.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new CompletionException(e);
                } catch (ExecutionException e) {
                    throw new CompletionException(e.getCause());
                }
            }
            if (t != null) {
                throw new CompletionException(t);
            }
            return r;
        });
        self.set(result);
        return result;
    }

    public CompletableFuture<T> get() {
        return actual.get();
    }

    public CompletableFuture<T> replace(CompletableFuture<T> with) {
        CompletableFuture<T> result = wrap(with, actual);
        Runnable oldInterrupt = interrupt.getAndSet(() -> {});
        actual.set(result);
        oldInterrupt.run();
        return result;
    }

    public InterruptibleFuture<T> replace(InterruptibleFuture<T> with) {
        CompletableFuture<T> result = replace(with.get());
        interrupt.set(with::interrupt);
        return new InterruptibleFuture<>(result, with::interrupt);
    }
}
