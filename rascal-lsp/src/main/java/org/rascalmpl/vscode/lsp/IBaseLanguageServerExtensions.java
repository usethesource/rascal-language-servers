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
package org.rascalmpl.vscode.lsp;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.jsonrpc.messages.Tuple.Two;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.services.LanguageServer;
import org.rascalmpl.vscode.lsp.parametric.LanguageRegistry.LanguageParameter;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.messages.PathConfigParameter;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.messages.VFSRegister;

public interface IBaseLanguageServerExtensions extends LanguageServer, IRascalFileSystemServices {
    @JsonNotification("rascal/supplyRemoteIDEServicesConfiguration")
    default CompletableFuture<IDEServicesConfiguration> supplyRemoteIDEServicesConfiguration() {
        throw new UnsupportedOperationException();
    }

    @JsonRequest("rascal/sendRegisterLanguage")
    default CompletableFuture<Void> sendRegisterLanguage(LanguageParameter lang) {
        throw new UnsupportedOperationException();
    }

    @JsonRequest("rascal/sendUnregisterLanguage")
    default CompletableFuture<Void> sendUnregisterLanguage(LanguageParameter lang) {
        throw new UnsupportedOperationException();
    }

    @JsonRequest("rascal/supplyPathConfig")
    default CompletableFuture<Two<String, URI[]>[]> supplyPathConfig(PathConfigParameter projectFolder) {
        throw new UnsupportedOperationException();
    }

    @JsonNotification("rascal/vfs/register")
    void registerVFS(VFSRegister registration);

    @JsonNotification("rascal/logLevel")
    void setMinimumLogLevel(String level);
}
