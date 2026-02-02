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
import org.rascalmpl.uri.ISourceLocationWatcher;
import org.rascalmpl.vscode.lsp.util.locations.Locations;
import io.usethesource.vallang.ISourceLocation;

public class ISourceLocationChanged {
    @NonNull
    private String watchId;
    @NonNull
    private String location;
    @NonNull
    private ISourceLocationChangeType changeType;

    public ISourceLocationChanged(@NonNull String watchId, @NonNull String location, @NonNull ISourceLocationChangeType changeType) {
        this.watchId = watchId;
        this.location = location;
        this.changeType = changeType;
    }

    public ISourceLocationChangeType getChangeType() {
        return changeType;
    }
    public String getLocation() {
        return location;
    }
    public ISourceLocation getSourceLocation() {
        return Locations.toLoc(location);
    }

    public String getWatchId() {
        return watchId;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj instanceof ISourceLocationChanged) {
            var other = (ISourceLocationChanged)obj;
            return Objects.equals(watchId, other.watchId)
                && Objects.equals(location, other.location)
                && Objects.equals(changeType, other.changeType)
                ;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(watchId, location, changeType);
    }

    public ISourceLocationWatcher.ISourceLocationChanged translate() {
        return ISourceLocationWatcher.makeChange(
            getSourceLocation(),
            ISourceLocationChangeType.translate(changeType)
        );
    }

    @Override
    public String toString() {
        return "ISourceLocationChanged [changeType=" + changeType + ", location=" + location + ", watchId=" + watchId + "]";
    }





}
