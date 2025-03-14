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
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;
import io.usethesource.vallang.ISourceLocation;

public class RenameRequest {
    @NonNull
    private String from;
    @NonNull
    private String to;
    @NonNull
    private boolean overwrite;

    public RenameRequest() {
    }

    public RenameRequest(String from, String to, boolean overwrite) {
        this.from = from;
        this.to = to;
        this.overwrite = overwrite;
    }

    public RenameRequest(ISourceLocation from, ISourceLocation to, boolean overwrite) {
        this.from = from.getURI().toString();
        this.to = to.getURI().toString();
        this.overwrite = overwrite;
    }

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }

    public boolean getOverwrite() {
        return overwrite;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof RenameRequest) {
            var other = (RenameRequest)obj;
            return Objects.equals(from, other.from)
                && Objects.equals(to, other.to)
                && overwrite == other.overwrite
                ;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(from, to, overwrite);
    }

}
