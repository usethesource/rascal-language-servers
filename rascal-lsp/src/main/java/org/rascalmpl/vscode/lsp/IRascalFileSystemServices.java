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
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.rascalmpl.library.Prelude;
import org.rascalmpl.uri.ISourceLocationWatcher.ISourceLocationChangeType;
import org.rascalmpl.uri.ISourceLocationWatcher.ISourceLocationChanged;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.uri.UnsupportedSchemeException;
import org.rascalmpl.uri.vfs.FileAttributesResult;
import org.rascalmpl.uri.vfs.FileAttributesResult.FilePermission;
import org.rascalmpl.uri.vfs.FileAttributesResult.FileType;
import org.rascalmpl.uri.vfs.IRemoteResolverRegistry;
import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.vscode.lsp.util.NamedThreadPool;
import org.rascalmpl.vscode.lsp.util.locations.Locations;

import io.usethesource.vallang.ISourceLocation;

public class IRascalFileSystemServices implements IRemoteResolverRegistry {
    static final URIResolverRegistry reg = URIResolverRegistry.getInstance();
    static final Logger IRascalFileSystemServices__logger = LogManager.getLogger(IRascalFileSystemServices.class);
    public static final ExecutorService executor = NamedThreadPool.cachedDaemon("rascal-vfs");

    //@JsonRequest("rascal/filesystem/resolveLocation")
    @Override
    /*default */public CompletableFuture<ISourceLocation> resolveLocation(ISourceLocation loc) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // ISourceLocation tmp = loc.toRascalLocation();

                // ISourceLocation resolved = Locations.toClientLocation(tmp);
                ISourceLocation resolved = Locations.toClientLocation(URIResolverRegistry.getInstance().logicalToPhysical(loc));

                if (resolved == null) {
                    return loc;
                }

                // return SourceLocation.fromRascalLocation(resolved);
                return resolved;
            } catch (Exception e) {
                IRascalFileSystemServices__logger.warn("Could not resolve location {}", loc, e);
                return loc;
            }
        }, executor);
    }

    //@JsonRequest("rascal/filesystem/watch")
    @Override
    /*default */public CompletableFuture<Void> watch(WatchRequest params) {
        return CompletableFuture.runAsync(() -> {
            try {
                // ISourceLocation loc = Locations.toLoc(params.getURI());
                ISourceLocation loc = params.getLocation();

                URIResolverRegistry.getInstance().watch(loc, params.isRecursive(), changed -> {
                    try {
                        onDidChangeFile(convertChangeEvent(changed));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (IOException | RuntimeException e) {
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

    //@JsonRequest("rascal/filesystem/stat")
    @Override
    /*default */public CompletableFuture<FileAttributesResult> stat(ISourceLocation loc) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // ISourceLocation loc = Locations.toLoc(uri);
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

    //@JsonRequest("rascal/filesystem/readDirectory")
    @Override
    /*default */public CompletableFuture<FileWithType[]> list(ISourceLocation loc) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // ISourceLocation loc = Locations.toLoc(uri);
                if (!reg.isDirectory(loc)) {
                    throw VSCodeFSError.notADirectory(loc);
                }
                return Arrays.stream(reg.list(loc)).map(l -> new FileWithType(URIUtil.getLocationName(l),
                        reg.isDirectory(l) ? FileType.Directory : FileType.File)).toArray(FileWithType[]::new);
            } catch (IOException | RuntimeException e) {
                throw new VSCodeFSError(e);
            }
        }, executor);
    }

    //@JsonRequest("rascal/filesystem/createDirectory")
    @Override
    /*default */public CompletableFuture<Void> mkDirectory(ISourceLocation loc) {
        return CompletableFuture.runAsync(() -> {
            try {
                // reg.mkDirectory(Locations.toLoc(uri));
                reg.mkDirectory(loc);
            } catch (IOException | RuntimeException e) {
                throw new VSCodeFSError(e);
            }
        }, executor);
    }

    //@JsonRequest("rascal/filesystem/readFile")
    @Override
    /*default */public CompletableFuture<String> readFile(ISourceLocation loc) {
        return CompletableFuture.supplyAsync(() -> {
            // try (InputStream source = new Base64InputStream(reg.getInputStream(Locations.toLoc(uri)), true)) {
            try (InputStream source = new Base64InputStream(reg.getInputStream(loc), true)) {
                return new String(source.readAllBytes(), StandardCharsets.US_ASCII);
            } catch (IOException | RuntimeException e) {
                throw new VSCodeFSError(e);
            }
        }, executor);
    }

    //@JsonRequest("rascal/filesystem/writeFile")
    @Override
    /*default */public CompletableFuture<Void> writeFile(ISourceLocation loc, String content, boolean append, boolean create, boolean overwrite) {
        return CompletableFuture.runAsync(() -> {
            try {
                // ISourceLocation loc = Locations.toLoc(uri);

                boolean fileExists = reg.exists(loc);
                if (!fileExists && !create) {
                    throw new FileNotFoundException(loc.toString());
                }
                if (fileExists && reg.isDirectory(loc)) {
                    throw VSCodeFSError.isADirectory(loc);
                }

                ISourceLocation parentFolder = URIUtil.getParentLocation(loc);
                if (!reg.exists(parentFolder) && create) {
                    throw new FileNotFoundException(parentFolder.toString());
                }

                if (fileExists && create && !overwrite) {
                    throw new FileAlreadyExistsException(loc.toString());
                }
                try (OutputStream target = reg.getOutputStream(loc, false)) {
                    target.write(Base64.getDecoder().decode(content));
                }
            } catch (IOException | RuntimeException e) {
                throw new VSCodeFSError(e);
            }
        }, executor);
    }

    //@JsonRequest("rascal/filesystem/delete")
    @Override
    /*default */public CompletableFuture<Void> remove(ISourceLocation loc, boolean recursive) {
        return CompletableFuture.runAsync(() -> {
            try {
                // ISourceLocation loc = Locations.toLoc(uri);
                reg.remove(loc, recursive);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    //@JsonRequest("rascal/filesystem/rename")
    @Override
    /*default */public CompletableFuture<Void> rename(ISourceLocation from, ISourceLocation to, boolean overwrite) {
        return CompletableFuture.runAsync(() -> {
            try {
                // ISourceLocation oldLoc = Locations.toLoc(from);
                // ISourceLocation newLoc = Locations.toLoc(to);
                // reg.rename(oldLoc, newLoc, overwrite);
                reg.rename(from, to, overwrite);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    // @JsonNotification("rascal/filesystem/onDidChangeFile")
    @Override
    /*default */public void onDidChangeFile(FileChangeEvent event) { }

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
