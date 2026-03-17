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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.checkerframework.checker.initialization.qual.UnderInitialization;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.rascalmpl.uri.ILogicalSourceLocationResolver;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.vscode.lsp.IBaseTextDocumentService;
import org.rascalmpl.vscode.lsp.TextDocumentState;

import io.usethesource.vallang.ISourceLocation;

public class FallbackResolver implements ILogicalSourceLocationResolver {

    private static @MonotonicNonNull FallbackResolver instance = null;

    public static FallbackResolver getInstance() {
        if (instance == null) {
            instance = new FallbackResolver();
        }
        return instance;
    }

    private FallbackResolver() { }

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
}
