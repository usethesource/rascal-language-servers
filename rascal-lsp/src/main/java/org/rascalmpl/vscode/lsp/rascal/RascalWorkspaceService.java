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

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.apache.commons.compress.utils.IOUtils;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.FileOperationFilter;
import org.eclipse.lsp4j.FileOperationOptions;
import org.eclipse.lsp4j.FileOperationPattern;
import org.eclipse.lsp4j.FileOperationPatternKind;
import org.eclipse.lsp4j.FileOperationsServerCapabilities;
import org.eclipse.lsp4j.FileOperationsWorkspaceCapabilities;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentContentParams;
import org.eclipse.lsp4j.TextDocumentContentRegistrationOptions;
import org.eclipse.lsp4j.TextDocumentContentResult;
import org.eclipse.lsp4j.WorkspaceClientCapabilities;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.WorkspaceServerCapabilities;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.vscode.lsp.BaseWorkspaceService;
import org.rascalmpl.vscode.lsp.util.Nullables;

import io.usethesource.vallang.ISourceLocation;

public class RascalWorkspaceService extends BaseWorkspaceService {

    private static final URIResolverRegistry REG = URIResolverRegistry.getInstance();

    RascalWorkspaceService(ExecutorService exec) {
        super(exec);
    }

    @Override
    public void initialize(ClientCapabilities clientCap, @Nullable List<WorkspaceFolder> currentWorkspaceFolders,
            ServerCapabilities capabilities) {
        super.initialize(clientCap, currentWorkspaceFolders, capabilities);

        var workspaceCap = Nullables.ensureNonNullAndGet(capabilities, ServerCapabilities::getWorkspace, ServerCapabilities::setWorkspace, WorkspaceServerCapabilities::new);
        var fileOperationCapabilities = Nullables.ensureNonNullAndGet(workspaceCap, WorkspaceServerCapabilities::getFileOperations, WorkspaceServerCapabilities::setFileOperations, FileOperationsServerCapabilities::new);

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


        registerTextDocumentContent(clientCap, workspaceCap);

    }

    private final Stream<String> allReadableSchemes() {
        var inputs = REG.getRegisteredInputSchemes();
        var logicals = REG.getRegisteredLogicalSchemes();
        return Stream.concat(inputs.stream(), logicals.stream()).filter(s -> !s.equals("lsp"));
    }

    private final static Set<String> GENERIC_SCHEMES = Set.of("file", "http", "https", "ftp");
    private final static Set<String> CONTAINER_LOCS = Set.of("jar", "zip", "compressed");

    private void registerTextDocumentContent(ClientCapabilities clientCap, WorkspaceServerCapabilities workspaceCap) {
        if (Nullables.has(clientCap.getWorkspace(), w -> w.getTextDocumentContent() != null)) {
            var schemes = Stream.concat(
                // rascal specific schemes
                allReadableSchemes().filter(s -> !GENERIC_SCHEMES.contains(s)),
                // nested schemes like jar+file etc
                allReadableSchemes().filter( s -> !CONTAINER_LOCS.contains(s)).flatMap(s -> CONTAINER_LOCS.stream().map(c -> c + "+" + s))
            ).collect(Collectors.toList());
            workspaceCap.setTextDocumentContent(new TextDocumentContentRegistrationOptions(schemes));
        }
    }

    @Override
    protected void projectRemoved(ISourceLocation loc) {
        ((RascalTextDocumentService) availableDocumentService()).projectRemoved(loc);
    }

    @Override
    public CompletableFuture<TextDocumentContentResult> textDocumentContent(TextDocumentContentParams params) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var file = URIUtil.assumeCorrectLocation(params.getUri());
                return new TextDocumentContentResult(org.apache.commons.io.IOUtils.toString(REG.getCharacterReader(file)));
            } catch (IOException e) {
                var message = e.getMessage();
                if (message == null || message.isEmpty()) {
                    message = e.getClass().getName();
                }
                throw new ResponseErrorException(new ResponseError(-100, "Could not read " + params.getUri() + "due to: " + message, e));
            }
        }, getExecutor());
    }


}
