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

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.CreateFilesParams;
import org.eclipse.lsp4j.DeleteFilesParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.RenameFilesParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.services.LanguageClient;
import org.rascalmpl.util.locations.ColumnMaps;
import org.rascalmpl.util.locations.LineColumnOffsetMap;
import org.rascalmpl.vscode.lsp.parametric.LanguageRegistry.LanguageParameter;

import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValue;

class RoutingTextDocumentService implements IBaseTextDocumentService {

    private @MonotonicNonNull Function<ISourceLocation, ISingleLanguageServer> route;

    void setRouter(Function<ISourceLocation, ISingleLanguageServer> routeFunc) {
        this.route = routeFunc;
    }

    private IBaseTextDocumentService route(ISourceLocation uri) {
        if (this.route == null) {
            throw new IllegalStateException("Router has not been initialized");
        }
        return (IBaseTextDocumentService) this.route.apply(uri).getServer().getTextDocumentService();
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'didOpen'");
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'didChange'");
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'didClose'");
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'didSave'");
    }

    @Override
    public void initializeServerCapabilities(ClientCapabilities clientCapabilities, ServerCapabilities result) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'initializeServerCapabilities'");
    }

    @Override
    public void shutdown() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'shutdown'");
    }

    @Override
    public void connect(LanguageClient client) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'connect'");
    }

    @Override
    public void pair(BaseWorkspaceService workspaceService) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'pair'");
    }

    @Override
    public void initialized() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'initialized'");
    }

    @Override
    public void registerLanguage(LanguageParameter lang) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'registerLanguage'");
    }

    @Override
    public void unregisterLanguage(LanguageParameter lang) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'unregisterLanguage'");
    }

    @Override
    public void projectAdded(String name, ISourceLocation projectRoot) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'projectAdded'");
    }

    @Override
    public void projectRemoved(String name, ISourceLocation projectRoot) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'projectRemoved'");
    }

    @Override
    public CompletableFuture<IValue> executeCommand(String languageName, String command) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'executeCommand'");
    }

    @Override
    public LineColumnOffsetMap getColumnMap(ISourceLocation file) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getColumnMap'");
    }

    @Override
    public ColumnMaps getColumnMaps() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getColumnMaps'");
    }

    @Override
    public @Nullable TextDocumentState getDocumentState(ISourceLocation file) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getDocumentState'");
    }

    @Override
    public boolean isManagingFile(ISourceLocation file) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isManagingFile'");
    }

    @Override
    public void didCreateFiles(CreateFilesParams params) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'didCreateFiles'");
    }

    @Override
    public void didRenameFiles(RenameFilesParams params, List<WorkspaceFolder> workspaceFolders) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'didRenameFiles'");
    }

    @Override
    public void didDeleteFiles(DeleteFilesParams params) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'didDeleteFiles'");
    }

    @Override
    public void cancelProgress(String progressId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'cancelProgress'");
    }

}
