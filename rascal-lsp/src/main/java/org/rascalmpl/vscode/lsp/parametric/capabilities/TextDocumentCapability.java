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

import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.DynamicRegistrationCapabilities;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.rascalmpl.vscode.lsp.util.Nullables;

/**
 * Capability in the `textDocument/` namespace.
 */
public abstract class TextDocumentCapability<T> extends AbstractDynamicCapability<T> {

    /**
     * @param methodSuffix This is prefixed with `textDocument/` before registration.
     */
    protected TextDocumentCapability(String methodSuffix) {
        super(String.format("textDocument/%s", methodSuffix));
    }

    /**
     * @param methodSuffix This is prefixed with `textDocument/` before registration.
     * @param preferStaticRegistration Whether to register this capability statically instead of dynamically. Intented for testing & debugging.
     */
    protected TextDocumentCapability(String methodSuffix, boolean preferStaticRegistration) {
        super(String.format("textDocument/%s", methodSuffix), preferStaticRegistration);
    }

    /**
     * Retrieves the client capabilities for this capability. Used to determine whether the client supports dynamic registration.
     * @param caps The client capabilties.
     * @return An object that determines dynamic registration support.
     */
    protected abstract @Nullable DynamicRegistrationCapabilities getCapabilities(TextDocumentClientCapabilities caps);

    @Override
    protected final boolean isDynamicallySupportedBy(ClientCapabilities clientCapabilities) {
        return Nullables.has(clientCapabilities.getTextDocument(), this::getCapabilities, DynamicRegistrationCapabilities::getDynamicRegistration);
    }

}
