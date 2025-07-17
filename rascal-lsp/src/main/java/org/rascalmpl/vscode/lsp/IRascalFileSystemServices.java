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
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NotDirectoryException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;
import java.util.Base64.Encoder;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;
import org.checkerframework.checker.nullness.qual.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.rascalmpl.library.Prelude;
import org.rascalmpl.uri.ISourceLocationWatcher.ISourceLocationChangeType;
import org.rascalmpl.uri.ISourceLocationWatcher.ISourceLocationChanged;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.uri.UnsupportedSchemeException;
import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.vscode.lsp.util.locations.Locations;

import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValueFactory;

public interface IRascalFileSystemServices {
    static final URIResolverRegistry reg = URIResolverRegistry.getInstance();
    static final Logger IRascalFileSystemServices__logger = LogManager.getLogger(IDEServicesThread.class);

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
        });
    }

    @JsonRequest("rascal/filesystem/watch")
    default CompletableFuture<Void> watch(WatchParameters params) {
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
        });
    }

    static FileChangeEvent convertChangeEvent(ISourceLocationChanged changed) throws IOException {
        return new FileChangeEvent(convertFileChangeType(changed.getChangeType()),
                changed.getLocation().getURI().toASCIIString());
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
    default CompletableFuture<FileStat> stat(URIParameter uri) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ISourceLocation loc = uri.getLocation();
                if (!reg.exists(loc)) {
                    throw new FileNotFoundException();
                }
                var created = reg.created(loc);
                var lastModified = reg.lastModified(loc);
                if (reg.isDirectory(loc)) {
                    return new FileStat(FileType.Directory, created, lastModified, 0, null);
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
                return new FileStat(FileType.File, created, lastModified, size, readonly(loc) ? FilePermission.Readonly : null);
            } catch (IOException | URISyntaxException | RuntimeException e) {
                throw new VSCodeFSError(e);
            }
        });
    }

    @JsonRequest("rascal/filesystem/readDirectory")
    default CompletableFuture<FileWithType[]> readDirectory(URIParameter uri) {
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
        });
    }

    @JsonRequest("rascal/filesystem/createDirectory")
    default CompletableFuture<Void> createDirectory(URIParameter uri) {
        return CompletableFuture.runAsync(() -> {
            try {
                reg.mkDirectory(uri.getLocation());
            } catch (IOException | URISyntaxException | RuntimeException e) {
                throw new VSCodeFSError(e);
            }
        });
    }

    @JsonRequest("rascal/filesystem/readFile")
    default CompletableFuture<LocationContent> readFile(URIParameter uri) {
        final int BUFFER_SIZE = 3 * 1024; // has to be divisibly by 3

        return CompletableFuture.supplyAsync(() -> {
            try (InputStream source = reg.getInputStream(uri.getLocation())) {
                // there is no streaming base64 encoder, but we also do not want to have the
                // whole file in memory
                // just to base64 encode it. So we stream it in chunks that will not cause
                // padding characters in
                // base 64
                Encoder encoder = Base64.getEncoder();
                StringBuilder result = new StringBuilder();
                byte[] buffer = new byte[BUFFER_SIZE];
                int read;
                while ((read = source.read(buffer, 0, BUFFER_SIZE)) == BUFFER_SIZE) {
                    result.append(encoder.encodeToString(buffer));
                }
                if (read > 0) {
                    // last part needs to be a truncated part of the buffer
                    buffer = Arrays.copyOf(buffer, read);
                    result.append(encoder.encodeToString(buffer));
                }
                return new LocationContent(result.toString());
            } catch (IOException | URISyntaxException | RuntimeException e) {
                throw new VSCodeFSError(e);
            }
        });
    }

    @JsonRequest("rascal/filesystem/writeFile")
    default CompletableFuture<Void> writeFile(WriteFileParameters params) {
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
        });
    }

    @JsonRequest("rascal/filesystem/delete")
    default CompletableFuture<Void> delete(DeleteParameters params) {
        return CompletableFuture.runAsync(() -> {
            try {
                ISourceLocation loc = params.getLocation();
                reg.remove(loc, params.isRecursive());
            } catch (IOException | URISyntaxException e) {
                throw new CompletionException(e);
            }
        });
    }

    @JsonRequest("rascal/filesystem/rename")
    default CompletableFuture<Void> rename(RenameParameters params) {
        return CompletableFuture.runAsync(() -> {
            try {
                ISourceLocation oldLoc = params.getOldLocation();
                ISourceLocation newLoc = params.getNewLocation();
                reg.rename(oldLoc, newLoc, params.isOverwrite());
            } catch (IOException | URISyntaxException e) {
                throw new CompletionException(e);
            }
        });
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


    public static class DeleteParameters {
        private final String uri;
        private final boolean recursive;

        public DeleteParameters(String uri, boolean recursive) {
            this.uri = uri;
            this.recursive = recursive;
        }

        public ISourceLocation getLocation() throws URISyntaxException {
            return new URIParameter(uri).getLocation();
        }

        public boolean isRecursive() {
            return recursive;
        }
    }

    public static class RenameParameters {
        private final String oldUri;
        private final String newUri;
        private final boolean overwrite;

        public RenameParameters(String oldUri, String newUri, boolean overwrite) {
            this.oldUri = oldUri;
            this.newUri = newUri;
            this.overwrite = overwrite;
        }

        public ISourceLocation getOldLocation() throws URISyntaxException {
            return new URIParameter(oldUri).getLocation();
        }

        public ISourceLocation getNewLocation() throws URISyntaxException {
            return new URIParameter(newUri).getLocation();
        }

        public boolean isOverwrite() {
            return overwrite;
        }
    }

    public static class WatchParameters {
        private final String uri;
        private final boolean recursive;
        private final String[] excludes;

        public WatchParameters(String uri, boolean recursive, String[] excludes) {
            this.uri = uri;
            this.recursive = recursive;
            this.excludes = excludes;
        }

        public ISourceLocation getLocation() throws URISyntaxException {
            return new URIParameter(uri).getLocation();
        }

        public String[] getExcludes() {
            return excludes;
        }

        public boolean isRecursive() {
            return recursive;
        }
    }

    public static class SourceLocation {
        private final String uri;
        private final int[] offsetLength;
        private final int[] beginLineColumn;
        private final int[] endLineColumn;

        public static SourceLocation fromRascalLocation(ISourceLocation loc) {
            if (loc.hasOffsetLength()) {
                if (loc.hasLineColumn()) {
                    return new SourceLocation(loc.getURI().toString(), loc.getOffset(), loc.getLength(), loc.getBeginLine(), loc.getBeginColumn(), loc.getEndLine(), loc.getEndColumn());
                }
                else {
                    return new SourceLocation(loc.getURI().toString(), loc.getOffset(), loc.getLength());
                }
            }
            else {
                return new SourceLocation(loc.getURI().toString());
            }
        }

        public ISourceLocation toRascalLocation() throws URISyntaxException {
            final IValueFactory VF = IRascalValueFactory.getInstance();
            ISourceLocation tmp = URIUtil.createFromURI(uri);

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

        public boolean hasOffsetLength() {
            return offsetLength != null;
        }

        public boolean hasLineColumn() {
            return beginLineColumn != null && endLineColumn != null;
        }

        public int getOffset() {
            return offsetLength[0];
        }

        public int getLength() {
            return offsetLength[1];
        }

        public int getBeginLine() {
            return beginLineColumn[0];
        }

        public int getBeginColumn() {
            return beginLineColumn[1];
        }

        public int getEndLine() {
            return endLineColumn[0];
        }

        public int getEndColumn() {
            return endLineColumn[1];
        }
    }

    public static class FileChangeEvent {
        private final FileChangeType type;
        private final String uri;

        public FileChangeEvent(FileChangeType type, String uri) {
            this.type = type;
            this.uri = uri;
        }

        public FileChangeType getType() {
            return type;
        }

        public ISourceLocation getLocation() throws URISyntaxException {
            return new URIParameter(uri).getLocation();
        }
    }

    public static enum FileChangeType {
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

    public static class FileStat {
        FileType type;
        long ctime;
        long mtime;
        long size;

        @Nullable FilePermission permissions;

        public FileStat(FileType type, long ctime, long mtime, long size, @Nullable FilePermission permissions) {
            this.type = type;
            this.ctime = ctime;
            this.mtime = mtime;
            this.size = size;
            this.permissions = permissions;
        }

    }

    public static enum FileType {
        Unknown(0), File(1), Directory(2), SymbolicLink(64);

        private final int value;

        private FileType(int val) {
            assert val == 0 || val == 1 || val == 2 || val == 64;
            this.value = val;
        }

        public int getValue() {
            return value;
        }
    }

    // this enum models the enum inside vscode, which in the future might become an enum flag
    // in that case we have to solve that
    public static enum FilePermission {
        Readonly(1);
        private final int value;
        private FilePermission(int val) {
            assert val == 1;
            this.value = val;
        }

        public int getValue() {
            return value;
        }
    }

    public static class FileWithType {
        private final String name;
        private final FileType type;

        public FileWithType(String name, FileType type) {
            this.name = name;
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public FileType getType() {
            return type;
        }
    }

    public static class LocationContent {
        private String content;

        public LocationContent(String content) {
            this.content = content;
        }

        public String getContent() {
            return content;
        }
    }

    public static class URIParameter {
        private String uri;

        public URIParameter(String uri) {
            this.uri = uri;
        }

        public String getUri() {
            return uri;
        }

        public ISourceLocation getLocation() throws URISyntaxException {
            return URIUtil.createFromURI(uri);
        }
    }

    public static class WriteFileParameters {
        private final String uri;
        private final String content;
        private final boolean create;
        private final boolean overwrite;

        public WriteFileParameters(String uri, String content, boolean create, boolean overwrite) {
            this.uri = uri;
            this.content = content;
            this.create = create;
            this.overwrite = overwrite;
        }

        public String getUri() {
            return uri;
        }

        public ISourceLocation getLocation() throws URISyntaxException {
            return new URIParameter(uri).getLocation();
        }

        public String getContent() {
            return content;
        }

        public boolean isCreate() {
            return create;
        }

        public boolean isOverwrite() {
            return overwrite;
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
        private static ResponseError unavailable(Object data) {
            return new ResponseError(-6, "Unavailable", data);
        }

        private static ResponseError generic(String message, Object data) {
            return new ResponseError(-99, message, data);
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
