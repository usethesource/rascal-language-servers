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
package org.rascalmpl.vscode.lsp.uri.jsonrpc;

import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.rascalmpl.uri.FileAttributes;
import org.rascalmpl.uri.SourceLocationTransformer;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.uri.UnsupportedSchemeException;
import org.rascalmpl.uri.remote.RascalFileSystemServices;
import org.rascalmpl.uri.remote.jsonrpc.BooleanResponse;
import org.rascalmpl.uri.remote.jsonrpc.CapabilitiesResponse;
import org.rascalmpl.uri.remote.jsonrpc.Capability;
import org.rascalmpl.uri.remote.jsonrpc.CapabilityLevel;
import org.rascalmpl.uri.remote.jsonrpc.CopyRequest;
import org.rascalmpl.uri.remote.jsonrpc.DirectoryListingResponse;
import org.rascalmpl.uri.remote.jsonrpc.ISourceLocationRequest;
import org.rascalmpl.uri.remote.jsonrpc.LocationContentResponse;
import org.rascalmpl.uri.remote.jsonrpc.NumberResponse;
import org.rascalmpl.uri.remote.jsonrpc.RemoteIOError;
import org.rascalmpl.uri.remote.jsonrpc.RemoveRequest;
import org.rascalmpl.uri.remote.jsonrpc.RenameRequest;
import org.rascalmpl.uri.remote.jsonrpc.SetLastModifiedRequest;
import org.rascalmpl.uri.remote.jsonrpc.SourceLocationResponse;
import org.rascalmpl.uri.remote.jsonrpc.StringResponse;
import org.rascalmpl.uri.remote.jsonrpc.TimestampResponse;
import org.rascalmpl.uri.remote.jsonrpc.WatchRequest;
import org.rascalmpl.uri.remote.jsonrpc.WriteFileRequest;
import org.rascalmpl.uri.vfs.IRemoteResolverRegistryClient;
import org.rascalmpl.uri.vfs.IRemoteResolverRegistryServer;
import org.rascalmpl.vscode.lsp.util.Sets;
import org.rascalmpl.vscode.lsp.util.locations.Locations;

import io.usethesource.vallang.ISourceLocation;

/**
 * Wrapper for RascalFileSystemServices handling LSP-specifics.
 * In particular, locations from LSP are mapped to Rascal-friendly locations.
 */
public class RascalFileSystemInVSCode implements IRemoteResolverRegistryServer {
    private static final Logger logger = LogManager.getLogger(RascalFileSystemInVSCode.class);
    private static final URIResolverRegistry reg = URIResolverRegistry.getInstance();

    @MonotonicNonNull
    private RascalFileSystemServices fileSystemServices = null;
    @MonotonicNonNull
    private CompletableFuture<CapabilitiesResponse> serverCapabilities = null;

    private <T extends SourceLocationTransformer> T transformLocations(T req) {
        return req.transformLocations(RascalFileSystemInVSCode::toRascalLocation);
    }

    public void connectRemoteRegistryClient(IRemoteResolverRegistryClient client) {
        fileSystemServices = new RascalFileSystemServices(client);
        serverCapabilities = fileSystemServices.serverCapabilities();
    }

    private RascalFileSystemServices services() {
        if (fileSystemServices == null) {
            throw new IllegalStateException("Remote resolver registry client not set yet");
        }
        return fileSystemServices;
    }

    private CompletableFuture<CapabilitiesResponse> capabilities() {
        if (serverCapabilities == null) {
            throw new IllegalStateException("Remote resolver registry client not set yet");
        }
        return serverCapabilities;

    }

    private static final Capability LOGICAL_CAPABILITY = new Capability(CapabilityLevel.PARTIAL, Locations.TRANSLATED_SCHEMES);

    @Override
    public CompletableFuture<CapabilitiesResponse> serverCapabilities() {
        logger.trace("serverCapabilities request");
        return capabilities()
            .thenApply(c ->
                new CapabilitiesResponse(c.getInput(), c.getWatch(), c.getOutput(), mergeLogical(c.getLogical()), c.getGetCharset())
            );
    }

    private Capability mergeLogical(@Nullable Capability logical) {
        if (logical == null || logical.isUnsupported()) {
            return LOGICAL_CAPABILITY;
        }
        if (logical.isFullySupported()) {
            return logical;
        }
        // we have to merge partial sets
        return new Capability(CapabilityLevel.PARTIAL, Sets.union(LOGICAL_CAPABILITY.getOnlyForSchemes(), logical.getOnlyForSchemes()));
    }

    private static ISourceLocation toRascalLocation(ISourceLocation loc) {
        if (Locations.isWrappedOpaque(loc)) {
            throw RemoteIOError.translate(new UnsupportedSchemeException("Opaque locations are not supported by Rascal: " + loc.getScheme()));
        }
        return Locations.toClientLocation(loc);
    }


    private static boolean isSupported(@Nullable Capability cap, ISourceLocation loc) {
        if (cap == null || cap.isUnsupported()) {
            return false;
        }
        if (cap.isFullySupported()) {
            return true;
        }
        var scheme = loc.getScheme();
        int offset = scheme.indexOf('+');
        if (offset > 0) {
            scheme = scheme.substring(0, offset);
        }
        return cap.getOnlyForSchemes().contains(scheme);

    }
    @Override
    public CompletableFuture<SourceLocationResponse> resolveLocation(ISourceLocationRequest req) {
        logger.trace("resolveLocation: {}", req.getLocation());
        return capabilities()
            .thenApply(CapabilitiesResponse::getLogical)
            .thenCompose(cap -> {
                var transformed = transformLocations(req);
                if (isSupported(cap, transformed.getLocation())) {
                    return services().resolveLocation(transformed);
                }
                return CompletableFuture.completedFuture(new SourceLocationResponse(transformed.getLocation()));
            });
    }

    @Override
    public CompletableFuture<Void> watch(WatchRequest req) {
        var loc = req.getLocation();
        logger.trace("watch: {}", loc);
        return services().watch(transformLocations(req));
    }

    @Override
    public CompletableFuture<Void> unwatch(WatchRequest req) {
        var loc = req.getLocation();
        logger.trace("unwatch: {}", loc);
        return services().unwatch(transformLocations(req));
    }

    @Override
    public CompletableFuture<FileAttributes> stat(ISourceLocationRequest req) {
        var loc = req.getLocation();
        logger.trace("stat: {}", loc);
        return services().stat(transformLocations(req));
    }

    @Override
    public CompletableFuture<DirectoryListingResponse> list(ISourceLocationRequest req) {
        logger.trace("list: {}", req.getLocation());
        return services().list(transformLocations(req));
    }

    @Override
    public CompletableFuture<Void> mkDirectory(ISourceLocationRequest req) {
        var loc = req.getLocation();
        logger.trace("mkDirectory: {}", loc);
        return services().mkDirectory(transformLocations(req));
    }

    @Override
    public CompletableFuture<LocationContentResponse> readFile(ISourceLocationRequest req) {
        var loc = req.getLocation();
        logger.trace("readFile: {}", loc);
        return services().readFile(transformLocations(req));
    }

    @Override
    public CompletableFuture<Void> writeFile(WriteFileRequest req) {
        var loc = req.getLocation();
        logger.info("writeFile: {}", loc);
        if (reg.exists(loc) && reg.isDirectory(loc)) {
            throw new ResponseErrorException(new ResponseError(RemoteIOError.IsADirectory.code, "Is a directory: " + loc, req));
        }
        return services().writeFile(transformLocations(req));
    }

    @Override
    public CompletableFuture<Void> remove(RemoveRequest req) {
        var loc = req.getLocation();
        logger.trace("remove: {}", loc);
        return services().remove(transformLocations(req));
    }

    @Override
    public CompletableFuture<Void> rename(RenameRequest req) {
        logger.trace("rename: {} to {}", req.getFrom(), req.getTo());
        return services().rename(transformLocations(req));
    }

    @Override
    public CompletableFuture<Void> copy(CopyRequest req) {
        logger.trace("copy: {} to {}", req.getFrom(), req.getTo());
        return services().copy(transformLocations(req));
    }

    @Override
    public CompletableFuture<BooleanResponse> exists(ISourceLocationRequest req) {
        var loc = req.getLocation();
        logger.trace("exists: {}", loc);
        return services().exists(transformLocations(req));
    }

    @Override
    public CompletableFuture<TimestampResponse> lastModified(ISourceLocationRequest req) {
        var loc = req.getLocation();
        logger.trace("lastModified: {}", loc);
        return services().lastModified(transformLocations(req));
    }

    @Override
    public CompletableFuture<TimestampResponse> created(ISourceLocationRequest req) {
        var loc = req.getLocation();
        logger.trace("created: {}", loc);
        return services().created(transformLocations(req));
    }

    @Override
    public CompletableFuture<BooleanResponse> isDirectory(ISourceLocationRequest req) {
        var loc = req.getLocation();
        logger.trace("isDirectory: {}", loc);
        return services().isDirectory(transformLocations(req));
    }

    @Override
    public CompletableFuture<BooleanResponse> isFile(ISourceLocationRequest req) {
        var loc = req.getLocation();
        logger.trace("isFile: {}", loc);
        return services().isFile(transformLocations(req));
    }

    @Override
    public CompletableFuture<NumberResponse> size(ISourceLocationRequest req) {
        var loc = req.getLocation();
        logger.trace("size: {}", loc);
        return services().size(transformLocations(req));
    }

    @Override
    public CompletableFuture<BooleanResponse> isReadable(ISourceLocationRequest req) {
        var loc = req.getLocation();
        logger.trace("isReadable: {}", loc);
        return services().isReadable(transformLocations(req));
    }

    @Override
    public CompletableFuture<Void> setLastModified(SetLastModifiedRequest req) {
        var loc = req.getLocation();
        logger.trace("setLastModified: {}", loc);
        return services().setLastModified(transformLocations(req));
    }

    @Override
    public CompletableFuture<BooleanResponse> isWritable(ISourceLocationRequest req) {
        var loc = req.getLocation();
        logger.trace("isWritable: {}", loc);
        return services().isWritable(transformLocations(req));
    }

    @Override
    public CompletableFuture<StringResponse> getCharset(ISourceLocationRequest req) {
        var loc = req.getLocation();
        logger.trace("getCharset: {}", loc);
        return services().getCharset(transformLocations(req));
    }
}
