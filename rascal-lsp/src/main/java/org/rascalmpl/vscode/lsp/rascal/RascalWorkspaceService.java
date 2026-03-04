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
package org.rascalmpl.vscode.lsp.rascal;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.FileOperationFilter;
import org.eclipse.lsp4j.FileOperationOptions;
import org.eclipse.lsp4j.FileOperationPattern;
import org.eclipse.lsp4j.FileOperationPatternKind;
import org.eclipse.lsp4j.FileOperationsServerCapabilities;
import org.eclipse.lsp4j.FileOperationsWorkspaceCapabilities;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.WorkspaceClientCapabilities;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.WorkspaceServerCapabilities;
import org.rascalmpl.vscode.lsp.BaseWorkspaceService;
import org.rascalmpl.vscode.lsp.IBaseTextDocumentService;
import org.rascalmpl.vscode.lsp.util.Nullables;

public class RascalWorkspaceService extends BaseWorkspaceService {

    RascalWorkspaceService(ExecutorService exec, IBaseTextDocumentService documentService) {
        super(exec, documentService);
    }

    @Override
    public void initialize(ClientCapabilities clientCap, @Nullable List<WorkspaceFolder> currentWorkspaceFolders,
            ServerCapabilities capabilities) {
        super.initialize(clientCap, currentWorkspaceFolders, capabilities);

        var workspaceCap = Nullables.initAndGet(capabilities, ServerCapabilities::getWorkspace, ServerCapabilities::setWorkspace, WorkspaceServerCapabilities::new);
        var fileOperationCapabilities = Nullables.initAndGet(workspaceCap, WorkspaceServerCapabilities::getFileOperations, WorkspaceServerCapabilities::setFileOperations, FileOperationsServerCapabilities::new);

        var rascalFile = new FileOperationPattern("**/*.rsc");
        rascalFile.setMatches(FileOperationPatternKind.File);
        var projectFolder = new FileOperationPattern("**/*");
        projectFolder.setMatches(FileOperationPatternKind.Folder);
        var whichFiles = new FileOperationOptions(Stream.of(rascalFile, projectFolder).map(FileOperationFilter::new).collect(Collectors.toList()));

        if (Nullables.has(clientCap.getWorkspace(), WorkspaceClientCapabilities::getFileOperations, FileOperationsWorkspaceCapabilities::getDidCreate)) {
            fileOperationCapabilities.setDidCreate(whichFiles);
        }
        if (Nullables.has(clientCap.getWorkspace(), WorkspaceClientCapabilities::getFileOperations, FileOperationsWorkspaceCapabilities::getDidRename)) {
            fileOperationCapabilities.setDidRename(whichFiles);
        }
        if (Nullables.has(clientCap.getWorkspace(), WorkspaceClientCapabilities::getFileOperations, FileOperationsWorkspaceCapabilities::getDidDelete)) {
            fileOperationCapabilities.setDidDelete(whichFiles);
        }
    }

}
