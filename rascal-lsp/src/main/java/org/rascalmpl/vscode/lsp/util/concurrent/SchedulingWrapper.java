package org.rascalmpl.vscode.lsp.util.concurrent;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Wraps scheduling capabilities around an existing `ExecutorService` to be able
 * to schedule tasks to that service with a delay (use method `schedule`).
 */
public class SchedulingWrapper implements ScheduledExecutorService {

    private final ExecutorService service;
    private final ScheduledExecutorService wrapper = Executors.newSingleThreadScheduledExecutor();

    public SchedulingWrapper(ExecutorService service) {
        this.service = service;
    }

    @Override
    public void shutdown() {
        wrapper.shutdown();
        service.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        wrapper.shutdownNow();
        return service.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return service.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return service.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return service.awaitTermination(timeout, unit);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return service.submit(task);
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return service.submit(task, result);
    }

    @Override
    public Future<?> submit(Runnable task) {
        return service.submit(task);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return service.invokeAll(tasks);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException {
        return service.invokeAll(tasks, timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        return service.invokeAny(tasks);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return service.invokeAny(tasks, timeout, unit);
    }

    @Override
    public void execute(Runnable command) {
        service.execute(command);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return wrapper.schedule(() -> service.submit(command).get(), delay, unit);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        return wrapper.schedule(() -> service.submit(callable).get(), delay, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        throw new UnsupportedOperationException("Unimplemented method 'scheduleAtFixedRate'");
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        throw new UnsupportedOperationException("Unimplemented method 'scheduleWithFixedDelay'");
    }
}
