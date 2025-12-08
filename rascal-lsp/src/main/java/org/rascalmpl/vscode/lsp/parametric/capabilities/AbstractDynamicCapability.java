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
package org.rascalmpl.vscode.lsp.parametric.capabilities;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.ServerCapabilities;
import org.rascalmpl.vscode.lsp.parametric.ILanguageContributions;

public abstract class AbstractDynamicCapability<O> {

    private final String id;

    protected AbstractDynamicCapability() {
        id = UUID.randomUUID().toString();
    }

    protected final String id() {
        return id;
    }

    protected abstract String methodName();

    protected abstract CompletableFuture<@NonNull O> options(ILanguageContributions contribs);

    protected abstract CompletableFuture<Boolean> hasContribution(ILanguageContributions contribs);

    protected abstract @NonNull O mergeOptions(Object existingOpts, Object newOpts);

    protected boolean preferStaticRegistration() {
        return false;
    }

    protected abstract boolean hasDynamicCapability(ClientCapabilities clientCapabilities);

    protected abstract void setStaticCapability(final ServerCapabilities result);

    protected final boolean setStaticCapability(ClientCapabilities client, final ServerCapabilities result) {
        if (preferStaticRegistration() || !hasDynamicCapability(client)) {
            setStaticCapability(result);
            return true;
        }
        return false;
    }

}
