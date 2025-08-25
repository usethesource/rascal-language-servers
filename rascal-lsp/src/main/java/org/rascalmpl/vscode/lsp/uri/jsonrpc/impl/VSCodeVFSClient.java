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
package org.rascalmpl.vscode.lsp.uri.jsonrpc.impl;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import engineering.swat.watch.DaemonThreadPool;
import io.usethesource.vallang.ISourceLocation;

public class VSCodeVFSClient implements VSCodeUriResolverClient, AutoCloseable {
    private static final Logger logger = LogManager.getLogger(VSCodeVFSClient.class);

    private final Map<WatchSubscriptionKey, Watchers> watchers = new ConcurrentHashMap<>();
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
    public void addWatcher(ISourceLocation loc, boolean recursive, Consumer<ISourceLocationWatcher.ISourceLocationChanged> callback, VSCodeUriResolverServer server) throws IOException {
        logger.trace("addWatcher: {}", loc);
        try {
            var watch = watchers.computeIfAbsent(new WatchSubscriptionKey(loc, recursive), k -> {
                logger.trace("Fresh watch, setting up request to server");
                var result = new Watchers();
                result.addNewWatcher(callback);
                watchersById.put(result.id, result);
                server.watch(new WatchRequest(loc, recursive, result.id)).join();
                return result;
            });
            watch.addNewWatcher(callback);
        } catch (CompletionException ce) {
            logger.error("Error setting up watch", ce.getCause());
            throw new IOException(ce.getCause());
        }
    }

    @Override
    public void removeWatcher(ISourceLocation loc, boolean recursive,
        Consumer<ISourceLocationWatcher.ISourceLocationChanged> callback,
        VSCodeUriResolverServer server) throws IOException {
        logger.trace("removeWatcher: {}", loc);
        var watchKey = new WatchSubscriptionKey(loc, recursive);
        var watch = watchers.get(watchKey);
        if (watch != null && watch.removeWatcher(callback)) {
            logger.trace("No other watchers registered, so unregistering at server");
            watchers.remove(watchKey);
            if (!watch.callbacks.isEmpty()) {
                logger.trace("Raced by another thread, canceling unregister");
                watchers.put(watchKey, watch);
                return;
            }
            watchersById.remove(watch.id);
            try {
                server.unwatch(new WatchRequest(loc, recursive, watch.id)).join();
            } catch (CompletionException ce) {
                logger.error("Error removing watch", ce.getCause());
                throw new IOException(ce.getCause());
            }
        }
    }

    private static class WatchSubscriptionKey {
        private final ISourceLocation loc;
        private final boolean recursive;
        public WatchSubscriptionKey(ISourceLocation loc, boolean recursive) {
            this.loc = loc;
            this.recursive = recursive;
        }

        @Override
        public int hashCode() {
            return Objects.hash(loc, recursive);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if ((obj instanceof WatchSubscriptionKey)) {
                WatchSubscriptionKey other = (WatchSubscriptionKey) obj;
                return recursive == other.recursive
                    && Objects.equals(loc, other.loc)
                    ;
            }
            return false;
        }

    }



    private static final ExecutorService exec = DaemonThreadPool.buildConstrainedCached("FallbackResolver watcher thread-pool", 4);

    /**
    * The watch api in rascal uses closures identity to keep track of watches.
    * Since we cannot share the instance via the json-rpc bridge, we keep the
    * closure around in this collection class.
    * If there are no more callbacks registered, we unregister the watch at the
    * VSCode side.
    */
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
            @SuppressWarnings("java:S2095") // we don't have to close the socket, we are passing it off to the lsp4j framework
            var socket = new Socket(InetAddress.getLoopbackAddress(), port);
            socket.setTcpNoDelay(true);
            @SuppressWarnings("java:S2095") // we don't have to close the client, we are passing it off to the VSCodeVFS singleton
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
