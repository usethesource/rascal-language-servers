/*
 * Copyright (c) 2018-2021, NWO-I CWI and Swat.engineering
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

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nullable;
import com.google.gson.JsonPrimitive;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.WorkspaceFoldersOptions;
import org.eclipse.lsp4j.WorkspaceServerCapabilities;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.WorkspaceService;

public class BaseWorkspaceService implements WorkspaceService, LanguageClientAware {
    private final IBaseTextDocumentService documentService;
    private final CopyOnWriteArrayList<WorkspaceFolder> workspaceFolders = new CopyOnWriteArrayList<>();


    BaseWorkspaceService(IBaseTextDocumentService documentService) {
        this.documentService = documentService;
        documentService.pair(this);
    }


    public void initializeServerCapabilities(ServerCapabilities capabilities) {
        var workspaceCap = new WorkspaceFoldersOptions();
        workspaceCap.setSupported(true);
        workspaceCap.setChangeNotifications(true);
        capabilities.setWorkspace(new WorkspaceServerCapabilities(workspaceCap));
    }

    public void setInitialWorkspaceFolders(@Nullable List<WorkspaceFolder> workspaceFolders) {
        this.workspaceFolders.clear();
        if (workspaceFolders != null) {
            this.workspaceFolders.addAll(workspaceFolders);
        }
    }

    public List<WorkspaceFolder> currentWorkSpaceFolders() {
        return Collections.unmodifiableList(workspaceFolders);
    }

    @Override
    public void connect(LanguageClient client) {
        // reserved for the future
    }

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams params) {
        // not used yet
    }

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
        // todo: use in the future
    }

    @Override
    public void didChangeWorkspaceFolders(DidChangeWorkspaceFoldersParams params) {
        var removed = params.getEvent().getRemoved();
        if (removed != null) {
            workspaceFolders.removeAll(removed);
        }
        var added = params.getEvent().getAdded();
        if (added != null) {
            workspaceFolders.addAll(added);
        }
    }

    @Override
    public CompletableFuture<Object> executeCommand(ExecuteCommandParams params) {
        switch (params.getCommand()) {
            case "rascal-meta-command":
                String extension = ((JsonPrimitive) params.getArguments().get(0)).getAsString();
                String command = ((JsonPrimitive) params.getArguments().get(1)).getAsString();
                return documentService.executeCommand(extension, command).thenApply(v -> command);
            default:
                return CompletableFuture.supplyAsync(() -> params.getCommand() + " was ignored.");
        }
    }


}
