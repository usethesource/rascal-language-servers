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
import org.rascalmpl.uri.FileAttributes;

public class FileAttributesResult extends IOResult {
    private @Nullable Boolean exists;
    private @Nullable Integer type;
    private @Nullable Long ctime;
    private @Nullable Long mtime;
    private @Nullable Integer size;
    private @Nullable Integer permissions;
    private @Nullable Boolean isWritable;

    public FileAttributesResult(@NonNull int errorCode, @Nullable String errorMessage, @Nullable Boolean exists, @Nullable Integer type, @Nullable Long ctime, @Nullable Long mtime, @Nullable Integer size, @Nullable Integer permissions, @Nullable Boolean isWritable) {
        super(errorCode, errorMessage);
        this.exists = exists;
        this.type = type;
        this.ctime = ctime;
        this.mtime = mtime;
        this.size = size;
        this.permissions = permissions;
        this.isWritable = isWritable;
    }

    public FileAttributesResult() {}

    public FileAttributes getFileAttributes() {
        return new FileAttributes(exists, (type & 1) == 1, ctime, mtime, (permissions & 1) == 1, isWritable, size);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof FileAttributesResult) {
            var other = (FileAttributesResult)obj;
            return super.equals(obj)
                && Objects.equals(exists, other.exists)
                && Objects.equals(type, other.type)
                && Objects.equals(ctime, other.ctime)
                && Objects.equals(mtime, other.mtime)
                && Objects.equals(size, other.size)
                && Objects.equals(permissions, other.permissions)
                && Objects.equals(isWritable, other.isWritable);
        }
        return false;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((exists == null) ? 0 : exists.hashCode());
        result = prime * result + ((type == null) ? 0 : type.hashCode());
        result = prime * result + ((ctime == null) ? 0 : ctime.hashCode());
        result = prime * result + ((mtime == null) ? 0 : mtime.hashCode());
        result = prime * result + ((size == null) ? 0 : size.hashCode());
        result = prime * result + ((permissions == null) ? 0 : permissions.hashCode());
        result = prime * result + ((isWritable == null) ? 0 : isWritable.hashCode());
        return result;
    }

    @Override
    public String toString() {
        return "FileStatResult [exists="+ exists + " type=" + type + " ctime=" + ctime + " mtime=" + mtime + " size=" + size + " permissions=" + permissions + " isWritable=" + isWritable + " io=" + super.toString() + "]";
    }

}
