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
package org.rascalmpl.vscode.lsp.uri;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.checkerframework.checker.initialization.qual.UnderInitialization;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.rascalmpl.uri.ILogicalSourceLocationResolver;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.vscode.lsp.IBaseTextDocumentService;
import org.rascalmpl.vscode.lsp.TextDocumentState;
import org.rascalmpl.vscode.lsp.util.Lazy;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.gson.JsonPrimitive;

import io.usethesource.vallang.ISourceLocation;

public class FallbackResolver implements ILogicalSourceLocationResolver {

    private static @MonotonicNonNull FallbackResolver instance = null;

    // The FallbackResolver is dynamically instantiated by URIResolverRegistry. By implementing it as a singleton and
    // making it avaible through this method, we allow the IBaseTextDocumentService implementations to interact with it.
    public static FallbackResolver getInstance() {
        if (instance == null) {
            instance = new FallbackResolver();
            //throw new IllegalStateException("FallbackResolver accessed before initialization");
        }
        return instance;
    }

    private FallbackResolver() {
        instance = this;
    }

    /**
     * Rascal's current implementions sometimes ask for a directory listing
     * and then iterate over all the entries checking if they are a directory.
     * This is super slow for this jsonrcp, so we store the last directory listing
     * and check inside
     */
    private final Cache<ISourceLocation, Lazy<Map<String, Boolean>>> cachedDirectoryListing
        = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(5))
            .maximumSize(1000)
            .build();

    @Override
    public String scheme() {
        throw new UnsupportedOperationException("Scheme not supported on fallback resolver");
    }

    public boolean isFileManaged(ISourceLocation file) {
        for (var service : textDocumentServices) {
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
                return URIUtil.changeScheme(input.top(), "lsp+" + input.getScheme());
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

    private final List<IBaseTextDocumentService> textDocumentServices = new CopyOnWriteArrayList<>();

    @SuppressWarnings({"initialization", "argument"})
    public void registerTextDocumentService(@UnderInitialization IBaseTextDocumentService service) {
        textDocumentServices.add(service);
    }

    public TextDocumentState getDocumentState(ISourceLocation file) throws IOException {
        for (var service : textDocumentServices) {
            var state = service.getDocumentState(file);
            if (state != null) {
                return state;
            }
        }
        throw new IOException("File is not managed by lsp");
    }

    private static IOException translateException(ResponseErrorException cause) {
        var error = cause.getResponseError();
        switch (error.getCode()) {
            case -1: return new IOException("Generic error: " + error.getMessage());
            case -2: {
                if (error.getData() instanceof JsonPrimitive) {
                    var data = (JsonPrimitive)error.getData();
                    if (data.isString()) {
                        switch (data.getAsString()) {
                            case "FileExists": // fall-through
                            case "EntryExists":
                                return new FileAlreadyExistsException(error.getMessage());
                            case "FileNotFound": // fall-through
                            case "EntryNotFound":
                                return new NoSuchFileException(error.getMessage());
                            case "FileNotADirectory": // fall-through
                            case "EntryNotADirectory":
                                return new NotDirectoryException(error.getMessage());
                            case "FileIsADirectory": // fall-through
                            case "EntryIsADirectory":
                                return new IOException("File is a directory: " + error.getMessage());
                            case "NoPermissions":
                                return new AccessDeniedException(error.getMessage());
                        }
                    }
                }
                return new IOException("File system error: " + error.getMessage() + " data: " + error.getData());
            }
            case -3: return new IOException("Rascal native scheme's should not be forwarded to VS Code");
            default: return new IOException("Missing case for: " + error);
        }
    }

}
