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
package org.rascalmpl.vscode.lsp.terminal;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.services.LanguageClient;
import org.rascalmpl.ideservices.IRemoteIDEServices;
import org.rascalmpl.ideservices.jsonrpc.ApplyDocumentsEditsRequest;
import org.rascalmpl.ideservices.jsonrpc.BrowseRequest;
import org.rascalmpl.ideservices.jsonrpc.EditRequest;
import org.rascalmpl.ideservices.jsonrpc.RegisterDebugServerPortRequest;
import org.rascalmpl.ideservices.jsonrpc.RegisterDiagnosticsRequest;
import org.rascalmpl.ideservices.jsonrpc.RegisterLocationsRequest;
import org.rascalmpl.ideservices.jsonrpc.StartDebuggingSessionRequest;
import org.rascalmpl.ideservices.jsonrpc.UnregisterDiagnosticsRequest;
import org.rascalmpl.uri.LogicalMapResolver;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.uri.remote.jsonrpc.ISourceLocationRequest;
import org.rascalmpl.uri.remote.jsonrpc.SourceLocationResponse;
import org.rascalmpl.vscode.lsp.IBaseLanguageClient;
import org.rascalmpl.vscode.lsp.IBaseTextDocumentService;
import org.rascalmpl.vscode.lsp.rascal.conversion.Diagnostics;
import org.rascalmpl.vscode.lsp.rascal.conversion.DocumentChanges;
import org.rascalmpl.vscode.lsp.util.concurrent.CompletableFutureUtils;
import org.rascalmpl.vscode.lsp.util.locations.Locations;

import io.usethesource.vallang.ISourceLocation;

/**
 * Server implementation of (remote) IDEServices, running within rascal-lsp
 * Most calls are preprocessed and subsequently forwarded to the LSP language client
 */
public class RemoteIDEServicesServer implements IRemoteIDEServices {
    private static final Logger logger = LogManager.getLogger(RemoteIDEServicesServer.class);
    private final IBaseLanguageClient languageClient;
    private final IBaseTextDocumentService docService;
    private final ExecutorService exec;

    public RemoteIDEServicesServer(LanguageClient languageClient, IBaseTextDocumentService docService, ExecutorService exec) {
        this.languageClient = (IBaseLanguageClient) languageClient;
        this.docService = docService;
        this.exec = exec;
    }

    @Override
    public CompletableFuture<Void> edit(EditRequest req) {
        var loc = req.getLocation();
        logger.trace("edit({})", loc);
        return CompletableFuture.runAsync(() -> {
            var physical = Locations.toClientLocation(loc);
            var range = loc.hasOffsetLength() ? Locations.toRange(physical, docService.getColumnMaps()) : null;
            languageClient.editDocument(Locations.toUri(physical), range, req.getViewColumn());
        }, exec);
    }

    @Override
    public CompletableFuture<Void> browse(BrowseRequest req) {
        var uri = req.getUri();
        logger.trace("browse({})", uri);
        return CompletableFuture.runAsync(() -> languageClient.showContent(uri, req.getTitle(), req.getViewColumn()), exec);
    }

    @Override
    public CompletableFuture<SourceLocationResponse> resolveProjectLocation(ISourceLocationRequest req) {
        var loc = req.getLocation();
        logger.trace("resolveProjectLocation({})", loc);
        try {
            return CompletableFutureUtils.completedFuture(new SourceLocationResponse(URIResolverRegistry.getInstance().logicalToPhysical(loc)), exec);
        } catch (IOException e) {
            return CompletableFutureUtils.completedFuture(new SourceLocationResponse(loc), exec);
        }
    }

    @Override
    public CompletableFuture<Void> applyDocumentsEdits(ApplyDocumentsEditsRequest req) {
        logger.trace("applyDocumentsEdits({})", req);
        return CompletableFuture.runAsync(() ->
            languageClient.applyEdit(new ApplyWorkspaceEditParams(DocumentChanges.translateDocumentChanges(req.getEdits(), docService.getColumnMaps()))), exec);
    }

    @Override
    public CompletableFuture<Void> registerLocations(RegisterLocationsRequest req) {
        var scheme = req.getScheme().getValue();
        var authority = req.getAuthority().getValue();
        var mapping = req.getMapping();
        logger.trace("registerLocations({}, {}, {})", scheme, authority, mapping);
        return CompletableFuture.runAsync(() ->
            URIResolverRegistry.getInstance().registerLogical(new LogicalMapResolver(scheme, authority, mapping)), exec);
    }

    @Override
    public CompletableFuture<Void> registerDiagnostics(RegisterDiagnosticsRequest req) {
        logger.trace("registerDiagnostics({})", req);
        return CompletableFuture.runAsync(() -> {
            Map<ISourceLocation, List<Diagnostic>> translated = Diagnostics.translateMessages(req.getMessages(), docService.extensions(), docService.getColumnMaps());

            for (Entry<ISourceLocation, List<Diagnostic>> entry : translated.entrySet()) {
                languageClient.publishDiagnostics(new PublishDiagnosticsParams(Locations.toUri(entry.getKey()).toString(), entry.getValue()));
            }
        }, exec);
    }

    @Override
    public CompletableFuture<Void> unregisterDiagnostics(UnregisterDiagnosticsRequest req) {
        var locs = req.getLocations();
        logger.trace("unregisterDiagnostics({})", (Object[]) locs);
        return CompletableFuture.runAsync(() -> {
            for (ISourceLocation loc : locs) {
                loc = Locations.toPhysicalIfPossible(loc);
                languageClient.publishDiagnostics(new PublishDiagnosticsParams(Locations.toUri(loc).toString(), Collections.emptyList()));
            }
        }, exec);
    }

    @Override
    public CompletableFuture<Void> startDebuggingSession(StartDebuggingSessionRequest req) {
        var serverPort = req.getServerPort();
        logger.trace("startDebuggingSession({})", serverPort);
        return CompletableFuture.runAsync(() -> languageClient.startDebuggingSession(serverPort), exec);
    }

    @Override
    public CompletableFuture<Void> registerDebugServerPort(RegisterDebugServerPortRequest req) {
        var processID = req.getProcessID();
        var serverPort = req.getServerPort();
        logger.trace("registerDebugServerPort({}, {})", processID, serverPort);
        return CompletableFuture.runAsync(() -> languageClient.registerDebugServerPort(processID, serverPort), exec);
    }
}
