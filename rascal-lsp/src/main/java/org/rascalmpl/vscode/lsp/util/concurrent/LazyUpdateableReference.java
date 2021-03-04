package org.rascalmpl.vscode.lsp.util.concurrent;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;


/**
 * A reference that allows for Lazy updates of the contained reference
 *
 * This is useful for delayed calculations
 */
public class LazyUpdateableReference<T> {
    private final Function<T, T> updater;
    private final AtomicBoolean valid;
    private final AtomicReference<T> value;

    public LazyUpdateableReference(T emptyValue, Function<T, T> updater) {
        this.updater = updater;
        value = new AtomicReference<>(emptyValue);
        valid = new AtomicBoolean(false);
    }

    public T get() {
        T result = value.get();
        if (!valid.get()) {
            synchronized(this) {
                if (!valid.get()) {
                    // we've won the race, so we update th reference
                    result = updater.apply(value.get());
                    value.set(result);
                    valid.set(true);
                }
            }
        }
        return result;
    }

    public void invalidate() {
        valid.set(false);
    }

}
