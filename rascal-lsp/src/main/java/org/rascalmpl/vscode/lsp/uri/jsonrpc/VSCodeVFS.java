package org.rascalmpl.vscode.lsp.uri.jsonrpc;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * This singleton keeps track of the current VFS server instance
 *
 * The FallbackResolvers uses this, and the LSP client should make sure to setup
 * the right connection
 */
public enum VSCodeVFS {
    INSTANCE;

    private volatile @MonotonicNonNull VSCodeUriResolverServer server = null;
    private volatile @MonotonicNonNull VSCodeUriResolverClient client = null;

    public @MonotonicNonNull VSCodeUriResolverServer getServer() {
        return server;
    }

    public void provideServer(VSCodeUriResolverServer server) {
        this.server = server;
    }

    public @MonotonicNonNull VSCodeUriResolverClient getClient() {
        return client;
    }

    public void provideClient(VSCodeUriResolverClient client) {
        this.client = client;
    }


}
