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
