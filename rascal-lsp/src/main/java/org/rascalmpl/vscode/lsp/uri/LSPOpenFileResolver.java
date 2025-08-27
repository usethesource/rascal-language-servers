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
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.rascalmpl.uri.FileAttributes;
import org.rascalmpl.uri.ISourceLocationInput;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.vscode.lsp.TextDocumentState;
import org.rascalmpl.vscode.lsp.util.Versioned;
import io.usethesource.vallang.ISourceLocation;

public class LSPOpenFileResolver implements ISourceLocationInput {

    private TextDocumentState getEditorState(ISourceLocation uri) throws IOException {
        return FallbackResolver.getInstance().getDocumentState(stripLspPrefix(uri));
    }

    @Override
    public InputStream getInputStream(ISourceLocation uri) throws IOException {
        return StringByteUtils.streamingBytes(getEditorState(uri).getCurrentContent().get(), StandardCharsets.UTF_16);
    }

    @Override
    public Charset getCharset(ISourceLocation uri) throws IOException {
        return StandardCharsets.UTF_16;
    }

    @Override
    public boolean exists(ISourceLocation uri) {
        return FallbackResolver.getInstance().isFileManaged(stripLspPrefix(uri));
    }

    @Override
    public long lastModified(ISourceLocation uri) throws IOException {
        return getEditorState(uri).getLastModified();
    }

    @Override
    public boolean isDirectory(ISourceLocation uri) {
        return false;
    }

    @Override
    public boolean isFile(ISourceLocation uri) {
        return exists(uri);
    }

    public static ISourceLocation stripLspPrefix(ISourceLocation uri) {
        if (uri.getScheme().startsWith("lsp+")) {
            try {
                return URIUtil.changeScheme(uri, uri.getScheme().substring("lsp+".length()));
            } catch (URISyntaxException e) {
                // fall through
            }
        }
        return uri;
    }

    @Override
    public String[] list(ISourceLocation uri) throws IOException {
        throw new IOException("`list` is not supported on files");
    }

    @Override
    public String scheme() {
        return "lsp";
    }

    @Override
    public boolean supportsHost() {
        return false;
    }

    @Override
    public long size(ISourceLocation uri) throws IOException {
        return size(getEditorState(uri).getCurrentContent());
    }

    private int size(Versioned<String> s) {
        return StringByteUtils.byteCount(s.get(), StandardCharsets.UTF_16);
    }

    @Override
    public boolean isReadable(ISourceLocation uri) throws IOException {
        return FallbackResolver.getInstance().isFileManaged(stripLspPrefix(uri));
    }

    @Override
    public FileAttributes stat(ISourceLocation uri) throws IOException {
        if (!exists(uri)) {
            return new FileAttributes(false, false, -1, -1, false, false, 0);
        }
        var current = getEditorState(uri).getCurrentContent();
        var modified = current.getTimestamp();
        var isWritable = FallbackResolver.getInstance().isWritable(stripLspPrefix(uri));
        return new FileAttributes(
            true, true,
            modified, modified, //We fix the creation timestamp to be equal to the last modified time
            true, isWritable,
            size(current)
        );
    }

}
