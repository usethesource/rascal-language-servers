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

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.DeleteFilesParams;
import org.eclipse.lsp4j.RenameFilesParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.LanguageParameter;
import org.rascalmpl.vscode.lsp.util.locations.LineColumnOffsetMap;

import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValue;

public interface IBaseTextDocumentService extends TextDocumentService {
    static final Duration NO_DEBOUNCE = Duration.ZERO;
    static final Duration NORMAL_DEBOUNCE = Duration.ofMillis(800);

    void initializeServerCapabilities(ServerCapabilities result);
    void shutdown();
    void connect(LanguageClient client);
    void pair(BaseWorkspaceService workspaceService);
    void registerLanguage(LanguageParameter lang);
    void unregisterLanguage(LanguageParameter lang);
    CompletableFuture<IValue> executeCommand(String languageName, String command);
    LineColumnOffsetMap getColumnMap(ISourceLocation file);
    TextDocumentState getDocumentState(ISourceLocation file);

    boolean isManagingFile(ISourceLocation file);

    void didRenameFiles(RenameFilesParams params, List<WorkspaceFolder> workspaceFolders);
    void didDeleteFiles(DeleteFilesParams params);
    void cancelProgress(String progressId);
}
