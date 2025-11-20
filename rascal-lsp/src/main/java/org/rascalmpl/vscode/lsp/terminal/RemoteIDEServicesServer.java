/*
 * Copyright (c) 2015-2025, NWO-I CWI and Swat.engineering
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
package org.rascalmpl.vscode.lsp.terminal;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.rascalmpl.ideservices.IRemoteIDEServices;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.values.ValueFactoryFactory;

import io.usethesource.vallang.IInteger;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;

public class RemoteIDEServicesServer implements IRemoteIDEServices {
    private final static Logger logger = LogManager.getLogger(RemoteIDEServicesServer.class);
    private final TerminalIDEClient terminalClient;

    public RemoteIDEServicesServer(TerminalIDEClient terminalClient) {
        this.terminalClient = terminalClient;
    }

    @Override
    public CompletableFuture<Void> edit(ISourceLocation loc) {
        terminalClient.edit(loc);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> browse(URI uri, IString title, IInteger viewColumn) {
        terminalClient.browse(uri, title, viewColumn);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<ISourceLocation> resolveProjectLocation(ISourceLocation param) {
        try {
            return CompletableFuture.completedFuture(URIResolverRegistry.getInstance().logicalToPhysical(param));
        } catch (IOException e) {
            return CompletableFuture.completedFuture(param);
        }
    }

    @Override
    public CompletableFuture<Void> applyDocumentsEdits(DocumentEditsParameter param) {
        terminalClient.applyFileSystemEdits(param.getEdits());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> registerLocations(RegisterLocationsParameters param) {
        terminalClient.registerLocations(param.getScheme(), param.getAuthority(), param.getMapping());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> registerDiagnostics(RegisterDiagnosticsParameters param) {
        terminalClient.registerDiagnostics(param.getMessages());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> unregisterDiagnostics(ISourceLocation[] locs) {
        terminalClient.unregisterDiagnostics(ValueFactoryFactory.getValueFactory().list(locs));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> startDebuggingSession(int serverPort) {
        terminalClient.startDebuggingSession(serverPort);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> registerDebugServerPort(int processID, int serverPort) {
        terminalClient.registerDebugServerPort(processID, serverPort);
        return CompletableFuture.completedFuture(null);
    }
}
