package org.rascalmpl.vscode.lsp.uri.jsonrpc.impl;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;
import org.rascalmpl.uri.ISourceLocationWatcher;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.VSCodeUriResolverClient;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.VSCodeUriResolverServer;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.messages.ISourceLocationChanged;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.messages.WatchRequest;
import io.usethesource.vallang.ISourceLocation;

public class Client implements VSCodeUriResolverClient {

    private final Map<ISourceLocation, Watchers> watchers = new ConcurrentHashMap<>();
    private final Map<String, Watchers> watchersById = new ConcurrentHashMap<>();

    @Override
    public void emitWatch(ISourceLocationChanged event) {
        var watch = watchersById.get(event.getWatchId());
        if (watch != null) {
            watch.publish(event.translate());
        }
    }

    @Override
    public void addWatcher(ISourceLocation loc, Consumer<ISourceLocationWatcher.ISourceLocationChanged> callback, VSCodeUriResolverServer server) throws IOException {
        var watch = watchers.get(loc);
        if (watch == null) {
            // we got a new one
            watch = new Watchers();
            watch.addNewWatcher(callback);
            watchers.put(loc, watch);
            watchersById.put(watch.id, watch);
            try {
                server.watch(new WatchRequest(loc, watch.id)).join();
                return;
            } catch (CompletionException ce) {
                throw new IOException(ce.getCause());
            }
        }
        watch.addNewWatcher(callback);
    }

    @Override
    public void removeWatcher(ISourceLocation loc,
        Consumer<ISourceLocationWatcher.ISourceLocationChanged> callback,
        VSCodeUriResolverServer server) throws IOException {
        var watch = watchers.get(loc);
        if (watch != null) {
            if (watch.removeWatcher(callback)) {
                // last watcher done
                watchers.remove(loc);
                if (!watch.callbacks.isEmpty()) {
                    // not empty anymore, add it back and forget clearing it
                    watchers.put(loc, watch);
                    return;
                }
                watchersById.remove(watch.id);
                try {
                    server.unwatch(new WatchRequest(loc, watch.id)).join();
                } catch (CompletionException ce) {
                    throw new IOException(ce.getCause());
                }
            }
        }
    }




    private static final ExecutorService exec = Executors.newCachedThreadPool(new ThreadFactory() {
        public Thread newThread(Runnable r) {
            SecurityManager s = System.getSecurityManager();
            ThreadGroup group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
            Thread t = new Thread(group, r, "FallbackResolver watcher thread-pool");
            t.setDaemon(true);
            return t;
        }
    });

    private static class Watchers {
        private final String id;
        private final List<Consumer<ISourceLocationWatcher.ISourceLocationChanged>> callbacks = new CopyOnWriteArrayList<>();

        public Watchers() {
            this.id = UUID.randomUUID().toString();
        }

        public void addNewWatcher(Consumer<ISourceLocationWatcher.ISourceLocationChanged> watcher) {
            this.callbacks.add(watcher);
        }

        public boolean removeWatcher(Consumer<ISourceLocationWatcher.ISourceLocationChanged> watcher) {
            this.callbacks.remove(watcher);
            return this.callbacks.isEmpty();
        }

        public void publish(ISourceLocationWatcher.ISourceLocationChanged changed) {
            for (Consumer<ISourceLocationWatcher.ISourceLocationChanged> c : callbacks) {
                // schedule callbacks on different thread
                exec.submit(() -> c.accept(changed));
            }
        }


    }
}
