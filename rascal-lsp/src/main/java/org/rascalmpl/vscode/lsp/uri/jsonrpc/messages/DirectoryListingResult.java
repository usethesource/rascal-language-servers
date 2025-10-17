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

import java.util.Arrays;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;

public class DirectoryListingResult {

    private  String[] entries;
    private boolean[] areDirectory;

    public DirectoryListingResult(String [] entries, boolean [] areDirectory) {
        this.entries = entries;
        this.areDirectory = areDirectory;
    }

    public DirectoryListingResult() {}

    public String [] getEntries() {
        return entries;
    }

    public boolean [] getAreDirectory() {
        return areDirectory;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj instanceof DirectoryListingResult) {
            return super.equals(obj)
                && Objects.deepEquals(entries, ((DirectoryListingResult)obj).entries)
                && Objects.deepEquals(areDirectory, ((DirectoryListingResult)obj).areDirectory)
                ;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return super.hashCode() + 11 * (Arrays.deepHashCode(entries) + 1) + 19 * (Arrays.hashCode(areDirectory) + 1);
    }

    @Override
    public String toString() {
        return "DirectoryListingResult [entries=" + Arrays.toString(entries) + "areDirectory=" +Arrays.toString(areDirectory) + " io=" + super.toString() + "]";
    }

}
