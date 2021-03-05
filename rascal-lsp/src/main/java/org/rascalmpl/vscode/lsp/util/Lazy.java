package org.rascalmpl.vscode.lsp.util;

import java.util.function.Supplier;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

public interface Lazy<T> extends Supplier<T> {
    public static <T> Lazy<T> defer(Supplier<T> generator) {
        return new Lazy<T>(){
            private volatile @MonotonicNonNull T result = null;

            @Override
            public T get() {
                if (result == null) {
                    result = generator.get();
                }
                return result;
            }

        };

    }

}
