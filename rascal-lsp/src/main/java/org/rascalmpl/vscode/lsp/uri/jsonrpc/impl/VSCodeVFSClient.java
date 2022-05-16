package org.rascalmpl.vscode.lsp.uri.jsonrpc.impl;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.rascalmpl.uri.ISourceLocationWatcher;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.VSCodeUriResolverClient;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.VSCodeUriResolverServer;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.VSCodeVFS;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.messages.ISourceLocationChanged;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.messages.WatchRequest;
import io.usethesource.vallang.ISourceLocation;

public class VSCodeVFSClient implements VSCodeUriResolverClient, AutoCloseable {
    private static final Logger logger = LogManager.getLogger(VSCodeVFSClient.class);

    private final Map<ISourceLocation, Watchers> watchers = new ConcurrentHashMap<>();
    private final Map<String, Watchers> watchersById = new ConcurrentHashMap<>();
    private final Socket socket;

    private VSCodeVFSClient(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void close() {
        try {
            this.socket.close();
        } catch (IOException e) {
            logger.debug("Closing failed", e);
        }
    }

    @Override
    public void emitWatch(ISourceLocationChanged event) {
        logger.trace("emitWatch: {}", event);
        var watch = watchersById.get(event.getWatchId());
        if (watch != null) {
            watch.publish(event.translate());
        }
    }

    @Override
    public void addWatcher(ISourceLocation loc, Consumer<ISourceLocationWatcher.ISourceLocationChanged> callback, VSCodeUriResolverServer server) throws IOException {
        logger.trace("addWatcher: {}", loc);
        var watch = watchers.get(loc);
        if (watch == null) {
            logger.trace("Fresh watch, setting up request to server");
            watch = new Watchers();
            watch.addNewWatcher(callback);
            watchers.put(loc, watch);
            watchersById.put(watch.id, watch);
            try {
                server.watch(new WatchRequest(loc, watch.id)).join();
                return;
            } catch (CompletionException ce) {
                logger.error("Error setting up watch", ce.getCause());
                throw new IOException(ce.getCause());
            }
        }
        watch.addNewWatcher(callback);
    }

    @Override
    public void removeWatcher(ISourceLocation loc,
        Consumer<ISourceLocationWatcher.ISourceLocationChanged> callback,
        VSCodeUriResolverServer server) throws IOException {
        logger.trace("removeWatcher: {}", loc);
        var watch = watchers.get(loc);
        if (watch != null) {
            if (watch.removeWatcher(callback)) {
                logger.trace("No other watchers registered, so unregistering at server");
                watchers.remove(loc);
                if (!watch.callbacks.isEmpty()) {
                    logger.trace("Raced by another thread, canceling unregister");
                    watchers.put(loc, watch);
                    return;
                }
                watchersById.remove(watch.id);
                try {
                    server.unwatch(new WatchRequest(loc, watch.id)).join();
                } catch (CompletionException ce) {
                    logger.error("Error removing watch", ce.getCause());
                    throw new IOException(ce.getCause());
                }
            }
        }
    }




    private static final ExecutorService exec = Executors.newCachedThreadPool(r -> {
        SecurityManager s = System.getSecurityManager();
        ThreadGroup group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
        Thread t = new Thread(group, r, "FallbackResolver watcher thread-pool");
        t.setDaemon(true);
        return t;
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

    public static void buildAndRegister(int port) {
        try {
            var existingClient = VSCodeVFS.INSTANCE.getClient();
            if (existingClient instanceof AutoCloseable) {
                try {
                    ((AutoCloseable)existingClient).close();
                } catch (Exception e) {
                    logger.error("Error closing old client", e);
                }
            }

            logger.debug("Connecting to VFS: {}", port);
            var socket = new Socket(InetAddress.getLoopbackAddress(), port);
            var newClient = new VSCodeVFSClient(socket);
            Launcher<VSCodeUriResolverServer> clientLauncher = new Launcher.Builder<VSCodeUriResolverServer>()
                .setRemoteInterface(VSCodeUriResolverServer.class)
                .setLocalService(newClient)
                .setInput(socket.getInputStream())
                .setOutput(socket.getOutputStream())
                .setExecutorService(Executors.newCachedThreadPool())
                .create();

            clientLauncher.startListening();

            VSCodeVFS.INSTANCE.provideServer(clientLauncher.getRemoteProxy());
            VSCodeVFS.INSTANCE.provideClient(newClient);
        } catch (Throwable e) {
            logger.error("Error setting up VFS connection", e);
        }
    }
}
