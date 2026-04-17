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
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.rascalmpl.uri.FileAttributes;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.uri.UnsupportedSchemeException;
import org.rascalmpl.uri.remote.RascalFileSystemServices;
import org.rascalmpl.uri.remote.jsonrpc.CopyRequest;
import org.rascalmpl.uri.remote.jsonrpc.DirectoryListingResponse;
import org.rascalmpl.uri.remote.jsonrpc.ISourceLocationRequest;
import org.rascalmpl.uri.remote.jsonrpc.JsonRpcRequest;
import org.rascalmpl.uri.remote.jsonrpc.LocationContentResponse;
import org.rascalmpl.uri.remote.jsonrpc.RemoteIOError;
import org.rascalmpl.uri.remote.jsonrpc.RemoveRequest;
import org.rascalmpl.uri.remote.jsonrpc.RenameRequest;
import org.rascalmpl.uri.remote.jsonrpc.SourceLocationResponse;
import org.rascalmpl.uri.remote.jsonrpc.WatchRequest;
import org.rascalmpl.uri.remote.jsonrpc.WriteFileRequest;
import org.rascalmpl.vscode.lsp.util.locations.Locations;

/**
 * Wrapper for RascalFileSystemServices handling LSP-specifics.
 * In particular, locations from LSP are mapped to Rascal-friendly locations.
 */
public class RascalFileSystemInVSCode extends RascalFileSystemServices {
    private static final Logger logger = LogManager.getLogger(RascalFileSystemServices.class);
    private static final URIResolverRegistry reg = URIResolverRegistry.getInstance();

    private <T extends JsonRpcRequest> T transformLocations(T req) {
        return req.transformLocations(Locations::toClientLocation);
    }
    
    @Override
    public CompletableFuture<SourceLocationResponse> resolveLocation(ISourceLocationRequest req) {
        logger.trace("resolveLocation: {}", req.getLocation());
        return super.resolveLocation(transformLocations(req));
    }

    @Override
    public CompletableFuture<Void> watch(WatchRequest req) {
        var loc = req.getLocation();
        logger.trace("watch: {}", loc);
        if (Locations.isWrappedOpaque(loc)) {
            throw RemoteIOError.translate(new UnsupportedSchemeException("Opaque locations are not supported by Rascal: " + loc.getScheme()));
        }
        return super.watch(transformLocations(req));
    }

    @Override
    public CompletableFuture<FileAttributes> stat(ISourceLocationRequest req) {
        var loc = req.getLocation();
        logger.trace("stat: {}", loc);
        return super.stat(transformLocations(req));
    }

    @Override
    public CompletableFuture<DirectoryListingResponse> list(ISourceLocationRequest req) {
        logger.trace("list: {}", req.getLocation());
        return super.list(transformLocations(req));
    }

    @Override
    public CompletableFuture<Void> mkDirectory(ISourceLocationRequest req) {
        var loc = req.getLocation();
        logger.trace("mkDirectory: {}", loc);
        return super.mkDirectory(transformLocations(req));
    }

    @Override
    public CompletableFuture<LocationContentResponse> readFile(ISourceLocationRequest req) {
        var loc = req.getLocation();
        logger.trace("readFile: {}", loc);
        return super.readFile(transformLocations(req));
    }

    @Override
    public CompletableFuture<Void> writeFile(WriteFileRequest req) {
        var loc = req.getLocation();
        logger.info("writeFile: {}", loc);
        if (reg.exists(loc) && reg.isDirectory(loc)) {
            throw new ResponseErrorException(new ResponseError(RemoteIOError.IsADirectory.code, "Is a directory: " + loc, req));
        }
        return super.writeFile(transformLocations(req));
    }

    @Override
    public CompletableFuture<Void> remove(RemoveRequest req) {
        var loc = req.getLocation();
        logger.trace("remove: {}", loc);
        return super.remove(transformLocations(req));
    }

    @Override
    public CompletableFuture<Void> rename(RenameRequest req) {
        logger.trace("rename: {} to {}", req.getFrom(), req.getTo());
        return super.rename(transformLocations(req));
    }

    @Override
    public CompletableFuture<Void> copy(CopyRequest req) {
        logger.trace("copy: {} to {}", req.getFrom(), req.getTo());
        return super.copy(transformLocations(req));
    }
}
