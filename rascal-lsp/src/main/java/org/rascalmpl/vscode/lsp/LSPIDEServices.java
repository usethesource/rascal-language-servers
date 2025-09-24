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

import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.logging.log4j.Logger;
import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowDocumentParams;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.rascalmpl.debug.IRascalMonitor;
import org.rascalmpl.ideservices.IDEServices;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.BrowseParameter;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.LanguageParameter;
import org.rascalmpl.vscode.lsp.util.Diagnostics;
import org.rascalmpl.vscode.lsp.util.DocumentChanges;
import org.rascalmpl.vscode.lsp.util.locations.Locations;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.IMap;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.IValue;

/**
 * This server forwards IDE services requests by a Rascal terminal
 * directly to the LSP language client.
 */
public class LSPIDEServices implements IDEServices {
    private final Logger logger;

    private final IBaseLanguageClient languageClient;
    private final IBaseTextDocumentService docService;
    private final BaseWorkspaceService workspaceService;

    private final IRascalMonitor monitor;

    public LSPIDEServices(IBaseLanguageClient client, IBaseTextDocumentService docService, BaseWorkspaceService workspaceService, Logger logger, IRascalMonitor monitor) {
        this.languageClient = client;
        this.workspaceService = workspaceService;
        this.docService = docService;
        this.logger = logger;
        this.monitor = monitor;
    }

    @Override
    public PrintWriter stderr() {
        assert false: "this should not be used here";
        return new PrintWriter(System.out);
    }

    @Override
    public void browse(URI uri, String title, int viewColumn) {
        languageClient.showContent(new BrowseParameter(uri.toString(), title, viewColumn));
    }

    @Override
    public void edit(ISourceLocation path) {
        ISourceLocation physical = Locations.toClientLocation(path);
        ShowDocumentParams params = new ShowDocumentParams(physical.getURI().toASCIIString());
        params.setTakeFocus(false);

        if (physical.hasOffsetLength()) {
            params.setSelection(Locations.toRange(physical, docService.getColumnMap(physical)));
        }

        languageClient.showDocument(params);
    }

    @Override
    public ISourceLocation resolveProjectLocation(ISourceLocation input) {
        for (var folder : workspaceService.workspaceFolders()) {
            if (folder.getName().equals(input.getAuthority())) {
                return buildProjectChildLoc(folder, input);
            }
        }
        return input;
    }

    private ISourceLocation buildProjectChildLoc(WorkspaceFolder folder, ISourceLocation input) {
        try {
            ISourceLocation root = URIUtil.createFromURI(folder.getUri());
            return URIUtil.getChildLocation(root, input.getPath());
        } catch (URISyntaxException e) {
            logger.catching(e);
            return input;
        }
    }

    @Override
    public void registerLanguage(IConstructor language) {
        languageClient.receiveRegisterLanguage(LanguageParameter.fromRascalValue(language));
    }

    @Override
    public void unregisterLanguage(IConstructor language) {
        languageClient.receiveUnregisterLanguage(LanguageParameter.fromRascalValue(language));
    }

    @Override
    public void applyDocumentsEdits(IList edits) {
        applyFileSystemEdits(edits);
    }

    @Override
    public void applyFileSystemEdits(IList edits) {
        languageClient.applyEdit(new ApplyWorkspaceEditParams(DocumentChanges.translateDocumentChanges(docService, edits)));
    }

    @Override
    public void registerLocations(IString scheme, IString auth, IMap map) {
        IDEServices.super.registerLocations(scheme, auth, map);
    }

    @Override
    public void registerDiagnostics(IList messages) {
        Map<ISourceLocation, List<Diagnostic>> translated = Diagnostics.translateMessages(messages, docService.getColumnMaps());

        for (Entry<ISourceLocation, List<Diagnostic>> entry : translated.entrySet()) {
            String uri = entry.getKey().getURI().toString();
            languageClient.publishDiagnostics(new PublishDiagnosticsParams(uri, entry.getValue()));
        }
    }

    @Override
    public void unregisterDiagnostics(IList resources) {
        for (IValue elem : resources) {
            ISourceLocation loc = (ISourceLocation) elem;
            languageClient.publishDiagnostics(new PublishDiagnosticsParams(loc.getURI().toString(), Collections.emptyList()));
        }
    }

    @Override
    public void jobStart(String name, int workShare, int totalWork) {
        monitor.jobStart(name, workShare, totalWork);
    }

    @Override
    public void jobStep(String name, String message, int workShare) {
        monitor.jobStep(name, message, workShare);
    }

    @Override
    public int jobEnd(String name, boolean succeeded) {
        return monitor.jobEnd(name, succeeded);
    }

    @Override
    public boolean jobIsCanceled(String name) {
        return monitor.jobIsCanceled(name);
    }

    @Override
    public void jobTodo(String name, int work) {
        monitor.jobTodo(name, work);
    }

    @Override
    public void endAllJobs() {
        monitor.endAllJobs();
    }

    @Override
    public void warning(String message, ISourceLocation src) {
        monitor.warning(message, src);
    }

    public IRascalMonitor getMonitor() {
        return monitor;
    }

}
