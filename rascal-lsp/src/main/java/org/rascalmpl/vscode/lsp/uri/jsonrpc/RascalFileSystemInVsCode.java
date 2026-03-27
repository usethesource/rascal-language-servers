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

import java.io.FileNotFoundException;
import java.net.URISyntaxException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NotDirectoryException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.rascalmpl.uri.FileAttributes;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.uri.UnsupportedSchemeException;
import org.rascalmpl.uri.remote.RascalFileSystemServices;
import org.rascalmpl.uri.remote.jsonrpc.ISourceLocationRequest;
import org.rascalmpl.uri.remote.jsonrpc.RemoveRequest;
import org.rascalmpl.uri.remote.jsonrpc.RenameRequest;
import org.rascalmpl.uri.remote.jsonrpc.WatchRequest;
import org.rascalmpl.uri.remote.jsonrpc.WriteFileRequest;
import org.rascalmpl.vscode.lsp.util.locations.Locations;

import io.usethesource.vallang.ISourceLocation;

public class RascalFileSystemInVsCode extends RascalFileSystemServices {
    private static final Logger logger = LogManager.getLogger(RascalFileSystemServices.class);
    private static final URIResolverRegistry reg = URIResolverRegistry.getInstance();
    
    @Override
    public CompletableFuture<ISourceLocation> resolveLocation(ISourceLocationRequest req) {
        logger.trace("resolveLocation: {}", req.getLocation());
        return super.resolveLocation(new ISourceLocationRequest(Locations.toClientLocation(req.getLocation()))).exceptionally(this::handleException);
    }

    @Override
    public CompletableFuture<Void> watch(WatchRequest params) {
        logger.trace("watch: {}", params.getLocation());
        if (Locations.isWrappedOpaque(params.getLocation())) {
            throw new ResponseErrorException(translate(new UnsupportedSchemeException("Opaque locations are not supported by Rascal")));
        }
        return super.watch(params);
    }

    @Override
    public CompletableFuture<FileAttributes> stat(ISourceLocationRequest req) {
        var loc = req.getLocation();
        logger.trace("stat: {}", loc);
        return super.stat(new ISourceLocationRequest(Locations.toClientLocation(loc))).exceptionally(this::handleException);
    }

    @Override
    public CompletableFuture<FileWithType[]> list(ISourceLocationRequest req) {
        logger.trace("list: {}", req.getLocation());
        return super.list(new ISourceLocationRequest(Locations.toClientLocation(req.getLocation()))).exceptionally(this::handleException);
    }

    @Override
    public CompletableFuture<Void> mkDirectory(ISourceLocationRequest req) {
        var loc = req.getLocation();
        logger.trace("mkDirectory: {}", loc);
        return super.mkDirectory(new ISourceLocationRequest(Locations.toClientLocation(loc))).exceptionally(this::handleException);
    }

    @Override
    public CompletableFuture<String> readFile(ISourceLocationRequest req) {
        var loc = req.getLocation();
        logger.trace("readFile: {}", loc);
        return super.readFile(new ISourceLocationRequest(Locations.toClientLocation(loc))).exceptionally(this::handleException);
    }

    @Override
    public CompletableFuture<Void> writeFile(WriteFileRequest req) {
        var loc = req.getLocation();
        logger.info("writeFile: {}", loc);
        if (reg.exists(loc) && reg.isDirectory(loc)) {
            return CompletableFuture.failedFuture(new ResponseErrorException(fileIsADirectory(loc)));
        }
        return super.writeFile(new WriteFileRequest(Locations.toClientLocation(loc), req.getContent(), req.isAppend())).exceptionally(this::handleException);
    }

    @Override
    public CompletableFuture<Void> remove(RemoveRequest req) {
        var loc = req.getLocation();
        logger.trace("remove: {}", loc);
        return super.remove(new RemoveRequest(Locations.toClientLocation(loc), req.isRecursive())).exceptionally(this::handleException);
    }

    @Override
    public CompletableFuture<Void> rename(RenameRequest req) {
        logger.trace("rename: {} to {}", req.getFrom(), req.getTo());
        return super.rename(new RenameRequest(Locations.toClientLocation(req.getFrom()), Locations.toClientLocation(req.getTo()), req.isOverwrite())).exceptionally(this::handleException);
    }

    private static ResponseError fileExists(Object data) {
        return new ResponseError(-1, "File exists", data);
    }

    private static ResponseError fileIsADirectory(Object data) {
        return new ResponseError(-2, "File is a directory", data);
    }

    private static ResponseError fileNotADirectory(Object data) {
        return new ResponseError(-3, "File is not a directory", data);
    }
    
    private static ResponseError fileNotFound(Object data) {
        return new ResponseError(-4, "File is not found", data);
    }
    
    private static ResponseError noPermissions(Object data) {
        return new ResponseError(-5, "No permissions", data);
    }

    @SuppressWarnings("unused")
    private static ResponseError unavailable(Object data) {
        return new ResponseError(-6, "Unavailable", data);
    }

    private static ResponseError generic(@Nullable String message, Object data) {
        return new ResponseError(-99, message == null ? "no error message was provided" : message, data);
    }

    public static ResponseError translate(Throwable original) {
        if (original == null) {
            return generic("Unknown error occurred", null);
        }
        if (original instanceof CompletionException) {
            var cause = original.getCause();
            
            if (cause instanceof FileNotFoundException || cause instanceof UnsupportedSchemeException || cause instanceof URISyntaxException) {
                return fileNotFound(cause);
            } else if (cause instanceof FileAlreadyExistsException) {
                return fileExists(cause);
            } else if (cause instanceof NotDirectoryException) {
                return fileNotADirectory(cause);
            } else if (cause instanceof SecurityException) {
                return noPermissions(cause);
            } else if (cause instanceof ResponseErrorException) {
                return ((ResponseErrorException) cause).getResponseError();
            } else {
                return generic(cause.getMessage(), original);
            }
        }
        return generic(original.getMessage(), original);
    }

    private <T, U extends T> T handleException(Throwable t) throws ResponseErrorException {
        throw new ResponseErrorException(translate(t));
    }
}
