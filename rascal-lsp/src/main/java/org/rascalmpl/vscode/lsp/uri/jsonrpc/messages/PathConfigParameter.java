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
package org.rascalmpl.vscode.lsp.uri.jsonrpc.messages;

import java.net.URISyntaxException;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;
import org.rascalmpl.vscode.lsp.util.locations.Locations;

import io.usethesource.vallang.ISourceLocation;

public class PathConfigParameter {
    @NonNull
    private String uri;

    @NonNull
    private PathConfigMode mode;


    public PathConfigParameter(@NonNull String uri, @NonNull PathConfigMode mode) {
        this.uri = uri;
        this.mode = mode;
    }

    public String getUri() {
        return uri;
    }

    public ISourceLocation getLocation() throws URISyntaxException {
        return Locations.toCheckedLoc(uri);
    }

    public PathConfigMode getMode() {
        return mode;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj instanceof PathConfigParameter) {
            var other = (PathConfigParameter)obj;
            return uri.equals(other.uri)
                && mode == other.mode
                ;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri, mode);
    }

    @Override
    public String toString() {
        return "PathConfigParameter [uri=" + uri + ", mode=" + mode + "]";
    }

}
