package org.rascalmpl.vscode.lsp.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A wrapper around CompletableFuture's that allow for us to replace the results
 * of a still running future, and call an specific closure if it is replaced by
 * a newer version.
 */
public class ExtendedFuture<T> {
    private static final Logger logger = LogManager.getLogger(ExtendedFuture.class);

    private static final class InterruptibleFuture<T> {
        private final Runnable interrupt;
        private final CompletableFuture<T> future;

        private InterruptibleFuture(Runnable interrupt, CompletableFuture<T> future) {
            this.interrupt = interrupt;
            this.future = future;
        }

    }

    private final AtomicBoolean valid;
    private final AtomicReference<InterruptibleFuture<T>> actual;

    public ExtendedFuture(CompletableFuture<T> start, Runnable interrupt) {
        actual = new AtomicReference<>(new InterruptibleFuture<>(interrupt, start));
        valid = new AtomicBoolean(true);
    }

    public CompletableFuture<T> get() {
        return actual.get().future;
    }

    public boolean isValid() {
        return valid.get();
    }

    public void invalidate() {
        valid.set(false);
    }

    @SuppressWarnings("java:S1181")
    public <U> CompletableFuture<T> replace(CompletableFuture<U> base, Function<U, T> apply, Runnable interrupt,
        Executor exec) {
        AtomicReference<InterruptibleFuture<T>> self = new AtomicReference<>(null);
        CompletableFuture<T> result = base.thenApplyAsync(u -> {
            try {
                T res = apply.apply(u);
                if (actual.get() == self.get()) {
                    return res;
                }
            } catch (Throwable t) {
                if (actual.get() == self.get()) {
                    throw new CompletionException(t);
                }
                else {
                    // we've been succeeded by a new one, so let's join on that one
                    // and swallow
                    logger.debug("Swallowing exception since we merge with next future", t);
                }
            }
            // before we finished (or got it interrupted) we've been replaced by a new future
            // so we chain onto that one and wait for that one to finish
            try {
                return actual.get().future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException(e.getCause());
            } catch (ExecutionException e) {
                throw new CompletionException(e.getCause());
            }
        }, exec);

        InterruptibleFuture<T> newActual = new InterruptibleFuture<>(interrupt, result);
        self.set(newActual);
        InterruptibleFuture<T> old = actual.getAndSet(newActual);
        exec.execute(old.interrupt::run);
        return result;
    }





}
