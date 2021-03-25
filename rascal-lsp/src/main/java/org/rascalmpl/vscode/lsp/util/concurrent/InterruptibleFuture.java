package org.rascalmpl.vscode.lsp.util.concurrent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

public class InterruptibleFuture<T> {

    private final CompletableFuture<T> future;
    private final Runnable interrupt;

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
        }
    }

    public <U> InterruptibleFuture<U> thenApply(Function<T, U> func) {
        return new InterruptibleFuture<>(future.thenApply(func), interrupt);
    }

    public InterruptibleFuture<Void> thenAccept(Consumer<T> func) {
        return new InterruptibleFuture<>(future.thenAccept(func), interrupt);
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
