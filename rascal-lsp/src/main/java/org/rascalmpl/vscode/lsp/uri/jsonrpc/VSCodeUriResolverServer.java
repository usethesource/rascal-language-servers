package org.rascalmpl.vscode.lsp.uri.jsonrpc;

import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.messages.BooleanResult;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.messages.DirectoryListingResult;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.messages.IOResult;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.messages.ISourceLocationRequest;
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

    @JsonRequest("rascal/vfs/output/writeFile")
    default CompletableFuture<IOResult> writeFile(WriteFileRequest req) {
        throw new UnsupportedOperationException();
    }

    @JsonRequest("rascal/vfs/output/mkDirectory")
    default CompletableFuture<IOResult> mkDirectory(ISourceLocationRequest req) {
        throw new UnsupportedOperationException();
    }

    @JsonRequest("rascal/vfs/output/remove")
    default CompletableFuture<IOResult> remove(ISourceLocationRequest req) {
        throw new UnsupportedOperationException();
    }

    @JsonRequest("rascal/vfs/output/rename")
    default CompletableFuture<IOResult> rename(RenameRequest req) {
        throw new UnsupportedOperationException();
    }

    @JsonRequest("rascal/vfs/watcher/watch")
    default CompletableFuture<IOResult> watch(WatchRequest req) {
        throw new UnsupportedOperationException();
    }

    @JsonRequest("rascal/vfs/watcher/unwatch")
    default CompletableFuture<IOResult> unwatch(WatchRequest req) {
        throw new UnsupportedOperationException();
    }

}
