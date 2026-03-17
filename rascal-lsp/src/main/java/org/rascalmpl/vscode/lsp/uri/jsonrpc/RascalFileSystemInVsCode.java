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
import org.rascalmpl.uri.FileAttributes;
import org.rascalmpl.uri.UnsupportedSchemeException;
import org.rascalmpl.uri.remote.IRascalFileSystemServices;
import org.rascalmpl.vscode.lsp.util.locations.Locations;

import io.usethesource.vallang.ISourceLocation;

public class RascalFileSystemInVsCode implements IRascalFileSystemServices {
    static final Logger logger = LogManager.getLogger(IRascalFileSystemServices.class);
    
    @Override
    public CompletableFuture<ISourceLocation> resolveLocation(ISourceLocation loc) {
        logger.trace("resolveLocation: {}", loc);
        return IRascalFileSystemServices.super.resolveLocation(Locations.toClientLocation(loc));
    }

    @Override
    public CompletableFuture<Void> watch(WatchRequest params) {
        logger.trace("watch: {}", params.getLocation());
        if (Locations.isWrappedOpaque(params.getLocation())) {
            throw new VSCodeFSError(new UnsupportedSchemeException("Opaque locations are not supported by Rascal"));
        }
        return IRascalFileSystemServices.super.watch(params);
    }

    @Override
    public CompletableFuture<FileAttributes> stat(ISourceLocation loc) {
        logger.trace("stat: {}", loc);
        return IRascalFileSystemServices.super.stat(Locations.toClientLocation(loc));
    }

    @Override
    public CompletableFuture<FileWithType[]> list(ISourceLocation loc) {
        logger.trace("list: {}", loc);
        return IRascalFileSystemServices.super.list(Locations.toClientLocation(loc));
    }

    @Override
    public CompletableFuture<Void> mkDirectory(ISourceLocation loc) {
        logger.trace("mkDirectory: {}", loc);
        return IRascalFileSystemServices.super.mkDirectory(Locations.toClientLocation(loc));
    }

    @Override
    public CompletableFuture<String> readFile(ISourceLocation loc) {
        logger.trace("readFile: {}", loc);
        return IRascalFileSystemServices.super.readFile(Locations.toClientLocation(loc));
    }

    @Override
    public CompletableFuture<Void> writeFile(ISourceLocation loc, String content, boolean append) {
        logger.info("writeFile: {}", loc);
        return IRascalFileSystemServices.super.writeFile(Locations.toClientLocation(loc), content, append);
    }

    @Override
    public CompletableFuture<Void> remove(ISourceLocation loc, boolean recursive) {
        logger.trace("remove: {}", loc);
        return IRascalFileSystemServices.super.remove(Locations.toClientLocation(loc), recursive);
    }

    @Override
    public CompletableFuture<Void> rename(ISourceLocation from, ISourceLocation to, boolean overwrite) {
        logger.trace("rename: {} to {}", from, to);
        return IRascalFileSystemServices.super.rename(Locations.toClientLocation(from), Locations.toClientLocation(to), overwrite);
    }

    @Override
    public void onDidChangeFile(ISourceLocation loc, int type, String watchId) {
        logger.trace("onDidChangeFile: {}", loc);
    }
}
