/*
 * Copyright (c) 2018-2023, NWO-I CWI and Swat.engineering
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
package org.rascalmpl.vscode.lsp.uri;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;

import org.rascalmpl.uri.ILogicalSourceLocationResolver;
import org.rascalmpl.uri.ISourceLocationInputOutput;
import org.rascalmpl.uri.ISourceLocationWatcher;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.vscode.lsp.IBaseTextDocumentService;
import org.rascalmpl.vscode.lsp.TextDocumentState;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.VSCodeUriResolverClient;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.VSCodeUriResolverServer;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.VSCodeVFS;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.messages.IOResult;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.messages.ISourceLocationRequest;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.messages.WriteFileRequest;
import org.rascalmpl.vscode.lsp.util.Lazy;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.usethesource.vallang.ISourceLocation;

public class FallbackResolver implements ISourceLocationInputOutput, ISourceLocationWatcher, ILogicalSourceLocationResolver {

    private static FallbackResolver instance = null;

    public static FallbackResolver getInstance() {
        if (instance == null) {
            throw new IllegalStateException("FallbackResolver accessed before initialization");
        }
        return instance;
    }

    public FallbackResolver() {
        instance = this;
    }

    private static VSCodeUriResolverServer getServer() throws IOException {
        var result = VSCodeVFS.INSTANCE.getServer();
        if (result == null) {
            throw new IOException("Missing VFS file server");
        }
        return result;
    }

    private static VSCodeUriResolverClient getClient() throws IOException {
        var result = VSCodeVFS.INSTANCE.getClient();
        if (result == null) {
            throw new IOException("Missing VFS file client");
        }
        return result;
    }

    private static <T extends IOResult> T call(Function<VSCodeUriResolverServer, CompletableFuture<T>> target) throws IOException {
        try {
            var waitingForServer = target.apply(getServer());
            var result = waitingForServer.get(5, TimeUnit.MINUTES);
            if (result.getErrorCode() != 0) {
                throw new IOException("" + result.getErrorCode() + ": " + result.getErrorMessage());
            }
            return result;
        }
        catch (TimeoutException te) {
            throw new IOException("VSCode took too long to reply, interruption to avoid deadlocks");
        }
        catch(InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new UnsupportedOperationException("Thread should have been interrupted");
        }
        catch (CompletionException | ExecutionException ce) {
            throw new IOException(ce.getCause());
        }
    }

    private static ISourceLocationRequest param(ISourceLocation uri) {
        return new ISourceLocationRequest(uri);
    }

    @Override
    public InputStream getInputStream(ISourceLocation uri) throws IOException {
        var fileBody = call(s -> s.readFile(param(uri))).getContents();

        // TODO: do the decoding in a stream, to avoid the extra intermediate
        // byte array
        return Base64.getDecoder().wrap(
            new ByteArrayInputStream(
                fileBody.getBytes(StandardCharsets.ISO_8859_1)));
    }

    @Override
    public boolean exists(ISourceLocation uri) {
        try {
            return call(s -> s.exists(param(uri))).getResult();
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public long lastModified(ISourceLocation uri) throws IOException {
        return TimeUnit.SECONDS.toMillis(call(s -> s.lastModified(param(uri))).getTimestamp());
    }

    @Override
    public long created(ISourceLocation uri) throws IOException {
        return TimeUnit.SECONDS.toMillis(call(s -> s.created(param(uri))).getTimestamp());
    }

    @Override
    public boolean isDirectory(ISourceLocation uri) {
        try {
            var cached = cachedDirectoryListing.getIfPresent(URIUtil.getParentLocation(uri));
            if (cached != null) {
                var result = cached.get().get(URIUtil.getLocationName(uri));
                if (result != null) {
                    return result;
                }
            }
            return call(s -> s.isDirectory(param(uri))).getResult();
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public boolean isFile(ISourceLocation uri) {
        try {
            var cached = cachedDirectoryListing.getIfPresent(URIUtil.getParentLocation(uri));
            if (cached != null) {
                var result = cached.get().get(URIUtil.getLocationName(uri));
                if (result != null) {
                    return !result;
                }
            }
            return call(s -> s.isFile(param(uri))).getResult();
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Rascal's current implementions sometimes ask for a directory listing
     * and then iterate over all the entries checking if they are a directory.
     * This is super slow for this jsonrcp, so we store tha last directory listing
     * and check insid
     */
    private final Cache<ISourceLocation, Lazy<Map<String, Boolean>>> cachedDirectoryListing
        = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(5))
            .maximumSize(1000)
            .build();

    @Override
    public String[] list(ISourceLocation uri) throws IOException {
        var result = call(s -> s.list(param(uri)));
        // we store the entries in a cache, for consecutive isDirectory/isFile calls
        cachedDirectoryListing.put(uri, Lazy.defer(() -> {
            var entries = result.getEntries();
            var areDirs = result.getAreDirectory();
            Map<String, Boolean> lookup = new HashMap<>(entries.length);
            assert entries.length == areDirs.length;
            for (int i = 0; i < entries.length; i++) {
                lookup.put(entries[i], areDirs[i]);
            }
            return lookup;
        }));
        return result.getEntries();
    }

    @Override
    public String scheme() {
        throw new UnsupportedOperationException("Scheme not supported on fallback resolver");
    }

    @Override
    public boolean supportsHost() {
        return false;
    }

    @Override
    public OutputStream getOutputStream(ISourceLocation uri, boolean append) throws IOException {
        // we have to collect all bytes into memory, there exist no streaming Base64 encoder in java jre
        // otherwise we could just store that base64 string.
        // when done with the outputstream, we can generate the base64 string and send it towards the LSP client
        return new ByteArrayOutputStream() {
            private boolean closed = false;

            @Override
            public void close() throws IOException {
                if (closed) {
                    return;
                }
                closed = true;
                var contents = Base64.getEncoder().encodeToString(this.toByteArray());
                call(s -> s.writeFile(new WriteFileRequest(uri, contents, append)));
                cachedDirectoryListing.invalidate(URIUtil.getParentLocation(uri));
            }
        };
    }

    @Override
    public void mkDirectory(ISourceLocation uri) throws IOException {
        call(s -> s.mkDirectory(param(uri)));
        cachedDirectoryListing.invalidate(URIUtil.getParentLocation(uri));
    }

    @Override
    public void remove(ISourceLocation uri) throws IOException {
        call(s -> s.remove(param(uri)));
        cachedDirectoryListing.invalidate(uri);
        cachedDirectoryListing.invalidate(URIUtil.getParentLocation(uri));
    }

    @Override
    public void setLastModified(ISourceLocation uri, long timestamp) throws IOException {
        throw new IOException("setLastModified not supported by vscode");
    }

    @Override
    public void watch(ISourceLocation root, Consumer<ISourceLocationChanged> watcher) throws IOException {
        getClient().addWatcher(root, watcher, getServer());
    }

    @Override
    public void unwatch(ISourceLocation root, Consumer<ISourceLocationChanged> watcher) throws IOException {
        getClient().removeWatcher(root, watcher, getServer());

    }
    
    public boolean isFileManaged(ISourceLocation file) {
        for (final var service : textDocumentServices) {
            if (service.isManagingFile(file)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ISourceLocation resolve(ISourceLocation input) throws IOException {
        if (isFileManaged(input)) {
            try {
                // The offset/length part of the source location is stripped off here.
                // This is reinstated by `URIResolverRegistry::resolveAndFixOffsets`
                // during logical resolution
                return URIUtil.changeScheme(input, "lsp+" + input.getScheme()).top();
            } catch (URISyntaxException e) {
                // fall through
            }
        }
        return input;
    }

    @Override
    public String authority() {
        throw new UnsupportedOperationException("'authority' not supported by fallback resolver");
    }

    private Set<IBaseTextDocumentService> textDocumentServices = new HashSet<>();

    public void registerTextDocumentService(IBaseTextDocumentService service) {
        textDocumentServices.add(service);
    }

    public TextDocumentState getDocumentState(ISourceLocation file) {
        for (final var service : textDocumentServices) {
            if (service.isManagingFile(file)) {
                return service.getDocumentState(file);
            }
        }
        return null;
    }
}
