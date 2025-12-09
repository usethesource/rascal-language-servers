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
package org.rascalmpl.vscode.lsp;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NotDirectoryException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;

import org.apache.commons.codec.binary.Base64InputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;
import org.rascalmpl.library.Prelude;
import org.rascalmpl.uri.ISourceLocationWatcher.ISourceLocationChangeType;
import org.rascalmpl.uri.ISourceLocationWatcher.ISourceLocationChanged;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.uri.UnsupportedSchemeException;
import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.messages.FileAttributesResult;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.messages.FileAttributesResult.FilePermission;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.messages.FileAttributesResult.FileType;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.messages.FileWithType;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.messages.ISourceLocationRequest;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.messages.RenameRequest;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.messages.WatchRequest;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.messages.WriteFileRequest;
import org.rascalmpl.vscode.lsp.util.NamedThreadPool;
import org.rascalmpl.vscode.lsp.util.locations.Locations;

import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValueFactory;

public interface IRascalFileSystemServices {
    static final URIResolverRegistry reg = URIResolverRegistry.getInstance();
    static final Logger IRascalFileSystemServices__logger = LogManager.getLogger(IRascalFileSystemServices.class);
    static final ExecutorService executor = NamedThreadPool.cachedDaemon("rascal-vfs");

    @JsonRequest("rascal/filesystem/resolveLocation")
    default CompletableFuture<SourceLocation> resolveLocation(SourceLocation loc) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ISourceLocation tmp = loc.toRascalLocation();

                ISourceLocation resolved = Locations.toClientLocation(tmp);

                if (resolved == null) {
                    return loc;
                }

                return SourceLocation.fromRascalLocation(resolved);
            } catch (Exception e) {
                IRascalFileSystemServices__logger.warn("Could not resolve location {}", loc, e);
                return loc;
            }
        }, executor);
    }

    @JsonRequest("rascal/filesystem/watch")
    default CompletableFuture<Void> watch(WatchRequest params) {
        return CompletableFuture.runAsync(() -> {
            try {
                ISourceLocation loc = params.getLocation();

                URIResolverRegistry.getInstance().watch(loc, params.isRecursive(), changed -> {
                    try {
                        onDidChangeFile(convertChangeEvent(changed));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (IOException | URISyntaxException | RuntimeException e) {
                throw new VSCodeFSError(e);
            }
        }, executor);
    }

    static FileChangeEvent convertChangeEvent(ISourceLocationChanged changed) throws IOException {
        return new FileChangeEvent(convertFileChangeType(changed.getChangeType()),
                Locations.toUri(changed.getLocation()).toASCIIString());
    }

    static FileChangeType convertFileChangeType(ISourceLocationChangeType changeType) throws IOException {
        switch (changeType) {
            case CREATED:
                return FileChangeType.Created;
            case DELETED:
                return FileChangeType.Deleted;
            case MODIFIED:
                return FileChangeType.Changed;
            default:
                throw new IOException("unknown change type: " + changeType);
        }
    }

    private static boolean readonly(ISourceLocation loc) throws IOException {
        if (reg.getRegisteredOutputSchemes().contains(loc.getScheme())) {
            return false;
        }
        if (reg.getRegisteredLogicalSchemes().contains(loc.getScheme())) {
            var resolved = Locations.toClientLocation(loc);
            if (resolved != null && resolved != loc) {
                return readonly(resolved);
            }
            return true;
        }
        return true;
    }

    @JsonRequest("rascal/filesystem/stat")
    default CompletableFuture<FileAttributesResult> stat(ISourceLocationRequest uri) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ISourceLocation loc = uri.getLocation();
                if (!reg.exists(loc)) {
                    throw new FileNotFoundException();
                }
                var created = reg.created(loc);
                var lastModified = reg.lastModified(loc);
                if (reg.isDirectory(loc)) {
                    return new FileAttributesResult(true, FileType.Directory, created, lastModified, 0, null);
                }
                long size = 0;
                if (reg.supportsReadableFileChannel(loc)) {
                    try (var c = reg.getReadableFileChannel(loc)) {
                        size = c.size();
                    }
                }
                else {
                    size = Prelude.__getFileSize(IRascalValueFactory.getInstance(), loc).longValue();
                }
                return new FileAttributesResult(true, FileType.File, created, lastModified, size, readonly(loc) ? FilePermission.Readonly : null);
            } catch (IOException | URISyntaxException | RuntimeException e) {
                throw new VSCodeFSError(e);
            }
        }, executor);
    }

    @JsonRequest("rascal/filesystem/readDirectory")
    default CompletableFuture<FileWithType[]> readDirectory(ISourceLocationRequest uri) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ISourceLocation loc = uri.getLocation();
                if (!reg.isDirectory(loc)) {
                    throw VSCodeFSError.notADirectory(loc);
                }
                return Arrays.stream(reg.list(loc)).map(l -> new FileWithType(URIUtil.getLocationName(l),
                        reg.isDirectory(l) ? FileType.Directory : FileType.File)).toArray(FileWithType[]::new);
            } catch (IOException | URISyntaxException | RuntimeException e) {
                throw new VSCodeFSError(e);
            }
        }, executor);
    }

    @JsonRequest("rascal/filesystem/createDirectory")
    default CompletableFuture<Void> createDirectory(ISourceLocationRequest uri) {
        return CompletableFuture.runAsync(() -> {
            try {
                reg.mkDirectory(uri.getLocation());
            } catch (IOException | URISyntaxException | RuntimeException e) {
                throw new VSCodeFSError(e);
            }
        }, executor);
    }

    @JsonRequest("rascal/filesystem/readFile")
    default CompletableFuture<String> readFile(ISourceLocationRequest uri) {
        return CompletableFuture.supplyAsync(() -> {
            try (InputStream source = new Base64InputStream(reg.getInputStream(uri.getLocation()), true)) {
                return new String(source.readAllBytes(), StandardCharsets.US_ASCII);
            } catch (IOException | URISyntaxException | RuntimeException e) {
                throw new VSCodeFSError(e);
            }
        }, executor);
    }

    @JsonRequest("rascal/filesystem/writeFile")
    default CompletableFuture<Void> writeFile(WriteFileRequest params) {
        return CompletableFuture.runAsync(() -> {
            try {
                ISourceLocation loc = params.getLocation();

                boolean fileExists = reg.exists(loc);
                if (!fileExists && !params.isCreate()) {
                    throw new FileNotFoundException(loc.toString());
                }
                if (fileExists && reg.isDirectory(loc)) {
                    throw VSCodeFSError.isADirectory(loc);
                }

                ISourceLocation parentFolder = URIUtil.getParentLocation(loc);
                if (!reg.exists(parentFolder) && params.isCreate()) {
                    throw new FileNotFoundException(parentFolder.toString());
                }

                if (fileExists && params.isCreate() && !params.isOverwrite()) {
                    throw new FileAlreadyExistsException(loc.toString());
                }
                try (OutputStream target = reg.getOutputStream(loc, false)) {
                    target.write(Base64.getDecoder().decode(params.getContent()));
                }
            } catch (IOException | URISyntaxException | RuntimeException e) {
                throw new VSCodeFSError(e);
            }
        }, executor);
    }

    @JsonRequest("rascal/filesystem/delete")
    default CompletableFuture<Void> delete(ISourceLocationRequest params, boolean recursive) {
        return CompletableFuture.runAsync(() -> {
            try {
                ISourceLocation loc = params.getLocation();
                reg.remove(loc, recursive);
            } catch (IOException | URISyntaxException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    @JsonRequest("rascal/filesystem/rename")
    default CompletableFuture<Void> rename(RenameRequest params) {
        return CompletableFuture.runAsync(() -> {
            try {
                ISourceLocation oldLoc = params.getFromLocation();
                ISourceLocation newLoc = params.getToLocation();
                reg.rename(oldLoc, newLoc, params.isOverwrite());
            } catch (IOException | URISyntaxException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    @JsonRequest("rascal/filesystem/schemes")
    default CompletableFuture<String[]> fileSystemSchemes() {
        Set<String> inputs = reg.getRegisteredInputSchemes();
        Set<String> logicals = reg.getRegisteredLogicalSchemes();

        return CompletableFuture
                .completedFuture(Stream.concat(inputs.stream(), logicals.stream()).toArray(String[]::new));
    }

    @JsonNotification("rascal/filesystem/onDidChangeFile")
    default void onDidChangeFile(FileChangeEvent event) { }


    public static class SourceLocation {
        @NonNull private final String uri;
        private final int @Nullable[] offsetLength;
        private final int @Nullable[] beginLineColumn;
        private final int @Nullable[] endLineColumn;

        public static SourceLocation fromRascalLocation(ISourceLocation loc) {
            if (loc.hasOffsetLength()) {
                if (loc.hasLineColumn()) {
                    return new SourceLocation(Locations.toUri(loc).toString(), loc.getOffset(), loc.getLength(), loc.getBeginLine(), loc.getBeginColumn(), loc.getEndLine(), loc.getEndColumn());
                }
                else {
                    return new SourceLocation(Locations.toUri(loc).toString(), loc.getOffset(), loc.getLength());
                }
            }
            else {
                return new SourceLocation(Locations.toUri(loc).toString());
            }
        }

        public ISourceLocation toRascalLocation() throws URISyntaxException {
            final IValueFactory VF = IRascalValueFactory.getInstance();
            ISourceLocation tmp = Locations.toCheckedLoc(uri);

            if (hasOffsetLength()) {
                if (hasLineColumn()) {
                    tmp = VF.sourceLocation(tmp,getOffset(), getLength(), getBeginLine(), getEndLine(), getBeginColumn(), getEndColumn());
                }
                else {
                    tmp = VF.sourceLocation(tmp, getOffset(), getLength());
                }
            }

            return tmp;
        }

        private SourceLocation(String uri, int offset, int length, int beginLine, int beginColumn, int endLine, int endColumn) {
            this.uri = uri;
            this.offsetLength = new int[] {offset, length};
            this.beginLineColumn = new int [] {beginLine, beginColumn};
            this.endLineColumn = new int [] {endLine, endColumn};
        }

        private SourceLocation(String uri, int offset, int length) {
            this.uri = uri;
            this.offsetLength = new int[] {offset, length};
            this.beginLineColumn = null;
            this.endLineColumn = null;
        }

        private SourceLocation(String uri) {
            this.uri = uri;
            this.offsetLength = null;
            this.beginLineColumn = null;
            this.endLineColumn = null;
        }

        public String getUri() {
            return uri;
        }

        @EnsuresNonNullIf(expression = "this.offsetLength", result = true)
        public boolean hasOffsetLength() {
            return offsetLength != null;
        }

        @EnsuresNonNullIf(expression = "this.endLineColumn", result = true)
        @EnsuresNonNullIf(expression = "this.beginLineColumn", result = true)
        public boolean hasLineColumn() {
            return beginLineColumn != null && endLineColumn != null;
        }

        public int getOffset() {
            if (!hasOffsetLength()) {
                throw new IllegalStateException("This location has no offset");
            }
            return offsetLength[0];
        }

        public int getLength() {
            if (!hasOffsetLength()) {
                throw new IllegalStateException("This location has no length");
            }
            return offsetLength[1];
        }

        public int getBeginLine() {
            if (!hasLineColumn()) {
                throw new IllegalStateException("This location has no line and columns");
            }
            return beginLineColumn[0];
        }

        public int getBeginColumn() {
            if (!hasLineColumn()) {
                throw new IllegalStateException("This location has no line and columns");
            }
            return beginLineColumn[1];
        }

        public int getEndLine() {
            if (!hasLineColumn()) {
                throw new IllegalStateException("This location has no line and columns");
            }
            return endLineColumn[0];
        }

        public int getEndColumn() {
            if (!hasLineColumn()) {
                throw new IllegalStateException("This location has no line and columns");
            }
            return endLineColumn[1];
        }
    }

    public static class FileChangeEvent {
        @NonNull private final FileChangeType type;
        @NonNull private final String uri;

        public FileChangeEvent(FileChangeType type, @NonNull String uri) {
            this.type = type;
            this.uri = uri;
        }

        public FileChangeType getType() {
            return type;
        }

        public ISourceLocation getLocation() throws URISyntaxException {
            return Locations.toCheckedLoc(uri);
        }
    }

    public enum FileChangeType {
        Changed(1), Created(2), Deleted(3);

        private final int value;

        private FileChangeType(int val) {
            assert val == 1 || val == 2 || val == 3;
            this.value = val;
        }

        public int getValue() {
            return value;
        }
    }

    public static class LocationContent {
        @NonNull private final String content;

        public LocationContent(@NonNull String content) {
            this.content = content;
        }

        public String getContent() {
            return content;
        }
    }

    /** Maps common exceptions to FileSystemError in VS Code */
    public static class VSCodeFSError extends ResponseErrorException {
        public VSCodeFSError(Exception original) {
            super(translate(original));
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

        public static ResponseErrorException notADirectory(Object data) {
            return new ResponseErrorException(fileNotADirectory(data));
        }

        public static ResponseErrorException isADirectory(Object data) {
            return new ResponseErrorException(fileIsADirectory(data));
        }

        private static ResponseError translate(Exception original) {
            if (original instanceof FileNotFoundException
                || original instanceof UnsupportedSchemeException
                || original instanceof URISyntaxException
            ) {
                return fileNotFound(original);
            }
            else if (original instanceof FileAlreadyExistsException) {
                return fileExists(original);
            }
            else if (original instanceof NotDirectoryException) {
                return fileNotADirectory(original);
            }
            else if (original instanceof SecurityException) {
                return noPermissions(original);
            }
            else if (original instanceof ResponseErrorException) {
                return ((ResponseErrorException)original).getResponseError();
            }
            return generic(original.getMessage(), original);
        }
    }
}
