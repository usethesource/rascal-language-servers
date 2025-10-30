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

import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.messages.BooleanResult;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.messages.DirectoryListingResult;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.messages.FileAttributesResult;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.messages.ISourceLocationRequest;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.messages.NumberResult;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.messages.ReadFileResult;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.messages.RenameRequest;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.messages.TimestampResult;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.messages.WatchRequest;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.messages.WriteFileRequest;

public interface VSCodeUriResolverServer {
    @JsonRequest("rascal/vfs/input/readFile")
    default CompletableFuture<ReadFileResult> readFile(ISourceLocationRequest req) {
        throw new UnsupportedOperationException();
    }

    @JsonRequest("rascal/vfs/input/exists")
    default CompletableFuture<BooleanResult> exists(ISourceLocationRequest req) {
        throw new UnsupportedOperationException();
    }

    @JsonRequest("rascal/vfs/input/lastModified")
    default CompletableFuture<TimestampResult> lastModified(ISourceLocationRequest req) {
        throw new UnsupportedOperationException();
    }

    @JsonRequest("rascal/vfs/input/created")
    default CompletableFuture<TimestampResult> created(ISourceLocationRequest req) {
        throw new UnsupportedOperationException();
    }

    @JsonRequest("rascal/vfs/input/isDirectory")
    default CompletableFuture<BooleanResult> isDirectory(ISourceLocationRequest req) {
        throw new UnsupportedOperationException();
    }

    @JsonRequest("rascal/vfs/input/isFile")
    default CompletableFuture<BooleanResult> isFile(ISourceLocationRequest req) {
        throw new UnsupportedOperationException();
    }

    @JsonRequest("rascal/vfs/input/list")
    default CompletableFuture<DirectoryListingResult> list(ISourceLocationRequest req) {
        throw new UnsupportedOperationException();
    }

    @JsonRequest("rascal/vfs/input/size")
    default CompletableFuture<NumberResult> size(ISourceLocationRequest req) {
        throw new UnsupportedOperationException();
    }

    @JsonRequest("rascal/vfs/input/stat")
    default CompletableFuture<FileAttributesResult> stat(ISourceLocationRequest req) {
        throw new UnsupportedOperationException();
    }

    @JsonRequest("rascal/vfs/input/isReadable")
    default CompletableFuture<BooleanResult> isReadable(ISourceLocationRequest req) {
        throw new UnsupportedOperationException();
    }

    @JsonRequest("rascal/vfs/input/isWritable")
    default CompletableFuture<BooleanResult> isWritable(ISourceLocationRequest req) {
        throw new UnsupportedOperationException();
    }


    @JsonRequest("rascal/vfs/output/writeFile")
    default CompletableFuture<Void> writeFile(WriteFileRequest req) {
        throw new UnsupportedOperationException();
    }

    @JsonRequest("rascal/vfs/output/mkDirectory")
    default CompletableFuture<Void> mkDirectory(ISourceLocationRequest req) {
        throw new UnsupportedOperationException();
    }

    @JsonRequest("rascal/vfs/output/remove")
    default CompletableFuture<Void> remove(ISourceLocationRequest req) {
        throw new UnsupportedOperationException();
    }

    @JsonRequest("rascal/vfs/output/rename")
    default CompletableFuture<Void> rename(RenameRequest req) {
        throw new UnsupportedOperationException();
    }

    @JsonRequest("rascal/vfs/watcher/watch")
    default CompletableFuture<Void> watch(WatchRequest req) {
        throw new UnsupportedOperationException();
    }

    @JsonRequest("rascal/vfs/watcher/unwatch")
    default CompletableFuture<Void> unwatch(WatchRequest req) {
        throw new UnsupportedOperationException();
    }

}
