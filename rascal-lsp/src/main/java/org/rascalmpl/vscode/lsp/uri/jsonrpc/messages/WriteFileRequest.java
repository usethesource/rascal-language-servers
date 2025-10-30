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

import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;
import io.usethesource.vallang.ISourceLocation;

public class WriteFileRequest extends ISourceLocationRequest {

    @NonNull
    private String content;
    @NonNull
    private boolean append;

    public WriteFileRequest() {}

    public WriteFileRequest(@NonNull String uri, @NonNull String content, @NonNull boolean append) {
        super(uri);
        this.content = content;
        this.append = append;
    }

    public WriteFileRequest(ISourceLocation loc, @NonNull String content, @NonNull boolean append) {
        super(loc);
        this.content = content;
        this.append = append;
    }

    public String getContent() {
        return content;
    }

    public boolean getAppend() {
        return append;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj instanceof WriteFileRequest) {
            var other = (WriteFileRequest)obj;
            return super.equals(obj)
                && Objects.equals(content, other.content)
                && append == other.append;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return super.hashCode() + 11 * Objects.hash(content, append);
    }

}
