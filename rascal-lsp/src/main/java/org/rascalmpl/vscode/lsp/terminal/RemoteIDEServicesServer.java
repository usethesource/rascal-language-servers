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
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.services.LanguageClient;
import org.rascalmpl.ideservices.IRemoteIDEServices;
import org.rascalmpl.uri.LogicalMapResolver;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.vscode.lsp.IBaseLanguageClient;
import org.rascalmpl.vscode.lsp.IBaseLanguageClient.BrowseParameter;
import org.rascalmpl.vscode.lsp.IBaseLanguageClient.EditorParameter;
import org.rascalmpl.vscode.lsp.IBaseTextDocumentService;
import org.rascalmpl.vscode.lsp.util.Diagnostics;
import org.rascalmpl.vscode.lsp.util.DocumentChanges;
import org.rascalmpl.vscode.lsp.util.locations.Locations;

import io.usethesource.vallang.IInteger;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;

/**
 * Server implementation of (remote) IDEServices, running within rascal-lsp
 * Most calls are preprocessed and subsequently forwarded to the LSP language client
 */
public class RemoteIDEServicesServer implements IRemoteIDEServices {
    private static final Logger logger = LogManager.getLogger(RemoteIDEServicesServer.class);
    private final IBaseLanguageClient languageClient;
    private final IBaseTextDocumentService docService;

    public RemoteIDEServicesServer(LanguageClient languageClient, IBaseTextDocumentService docService) {
        this.languageClient = (IBaseLanguageClient) languageClient;
        this.docService = docService;
    }

    @Override
    public CompletableFuture<Void> edit(ISourceLocation loc, int viewColumn) {
        logger.trace("edit({})", loc);
        var physical = Locations.toClientLocation(loc);
        var range = loc.hasOffsetLength() ? Locations.toRange(physical, docService.getColumnMaps()) : null;
        return CompletableFuture.runAsync(() -> languageClient.editDocument(new EditorParameter(physical.getURI().toASCIIString(), range, viewColumn)));
    }

    @Override
    public CompletableFuture<Void> browse(URI uri, IString title, IInteger viewColumn) {
        logger.trace("browse({})", uri);
        return CompletableFuture.runAsync(() -> languageClient.showContent(new BrowseParameter(uri, title, viewColumn)));
    }

    @Override
    public CompletableFuture<ISourceLocation> resolveProjectLocation(ISourceLocation loc) {
        logger.trace("resolveProjectLocation({})", loc);
        try {
            return CompletableFuture.completedFuture(URIResolverRegistry.getInstance().logicalToPhysical(loc));
        } catch (IOException e) {
            return CompletableFuture.completedFuture(loc);
        }
    }

    @Override
    public CompletableFuture<Void> applyDocumentsEdits(DocumentEditsParameter edits) {
        logger.trace("applyDocumentsEdits({})", edits);
        return CompletableFuture.runAsync(() ->
            languageClient.applyEdit(new ApplyWorkspaceEditParams(DocumentChanges.translateDocumentChanges(edits.getEdits(), docService.getColumnMaps()))));
    }

    @Override
    public CompletableFuture<Void> registerLocations(IString scheme, IString authority, ISourceLocation[][] mapping) {
        logger.trace("registerLocaions({}, {}, {})", scheme, authority, mapping);
        return CompletableFuture.runAsync(() ->
            URIResolverRegistry.getInstance().registerLogical(
                new LogicalMapResolver(
                    scheme.getValue(),
                    authority.getValue(),
                    IRemoteIDEServices.locArrayToMapLocLoc(mapping)
                )
            ));
    }

    @Override
    public CompletableFuture<Void> registerDiagnostics(RegisterDiagnosticsParameters param) {
        logger.trace("registerDiagnostics({})", param);
        return CompletableFuture.runAsync(() -> {
            Map<ISourceLocation, List<Diagnostic>> translated = Diagnostics.translateMessages(param.getMessages(), docService.getColumnMaps());

            for (Entry<ISourceLocation, List<Diagnostic>> entry : translated.entrySet()) {
                String uri = entry.getKey().getURI().toString();
                languageClient.publishDiagnostics(new PublishDiagnosticsParams(uri, entry.getValue()));
            }
        });
    }

    @Override
    public CompletableFuture<Void> unregisterDiagnostics(ISourceLocation[] locs) {
        logger.trace("unregisterDiagnostics({})", (Object[]) locs);
        return CompletableFuture.runAsync(() -> {
            for (ISourceLocation loc : locs) {
                loc = Locations.toPhysicalIfPossible(loc);
                languageClient.publishDiagnostics(new PublishDiagnosticsParams(loc.getURI().toString(), Collections.emptyList()));
            }
        });
    }

    @Override
    public CompletableFuture<Void> startDebuggingSession(int serverPort) {
        logger.trace("startDebuggingSession({})", serverPort);
        return CompletableFuture.runAsync(() -> languageClient.startDebuggingSession(serverPort));
    }

    @Override
    public CompletableFuture<Void> registerDebugServerPort(int processID, int serverPort) {
        logger.trace("registerDebugServerPort({}, {})", processID, serverPort);
        return CompletableFuture.runAsync(() -> languageClient.registerDebugServerPort(processID, serverPort));
    }
}
