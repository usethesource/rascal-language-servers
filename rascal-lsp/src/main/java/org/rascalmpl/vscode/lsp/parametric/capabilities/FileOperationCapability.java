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
package org.rascalmpl.vscode.lsp.parametric.capabilities;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.FileOperationFilter;
import org.eclipse.lsp4j.FileOperationOptions;
import org.eclipse.lsp4j.FileOperationPattern;
import org.eclipse.lsp4j.FileOperationPatternKind;
import org.eclipse.lsp4j.FileOperationPatternOptions;
import org.eclipse.lsp4j.FileOperationsServerCapabilities;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.WorkspaceServerCapabilities;
import org.rascalmpl.vscode.lsp.util.Sets;
import org.rascalmpl.vscode.lsp.util.concurrent.CompletableFutureUtils;

/**
 * Collection of capabilities related to file operations.
 */
public abstract class FileOperationCapability extends AbstractDynamicCapability<FileOperationOptions> {

    private final Executor exec;

    FileOperationCapability(String propertyName, Executor exec) {
        super(propertyName);
        this.exec = exec;
    }

    @Override
    protected final boolean isDynamicallySupportedBy(ClientCapabilities clientCapabilities) {
        return clientCapabilities.getWorkspace().getFileOperations().getDynamicRegistration();
    }

    @Override
    protected final CompletableFuture<Boolean> isProvidedBy(ICapabilityParams params) {
        return CompletableFutureUtils.completedFuture(!params.fileExtensions().isEmpty(), exec);
    }

    @Override
    protected final FileOperationOptions mergeOptions(FileOperationOptions o1, FileOperationOptions o2) {
        return new FileOperationOptions(new ArrayList<>(Sets.union(o1.getFilters(), o2.getFilters())));
    }

    @Override
    protected final CompletableFuture<@Nullable FileOperationOptions> options(ICapabilityParams params) {
        return CompletableFutureUtils.completedFuture(new FileOperationOptions(params.fileExtensions().stream()
            .map(FileOperationCapability::extensionFilter)
            .collect(Collectors.toList())), exec);
    }

    /**
     * Options to use when registering statically, i.e. nothing is known about the registered languages.
     */
    private static FileOperationOptions staticOptions() {
        // Since we do not know what the extenstions of the to-be-registered languages are, we match on anything.
        return new FileOperationOptions(List.of(new FileOperationFilter(new FileOperationPattern("**/*"))));
    }

    private static FileOperationFilter extensionFilter(String ext) {
        var pat = new FileOperationPattern(String.format("**/*.%s", ext));
        pat.setOptions(new FileOperationPatternOptions(true));
        pat.setMatches(FileOperationPatternKind.File);
        return new FileOperationFilter(pat);
    }

    private static FileOperationsServerCapabilities fileOperationCapabilities(ServerCapabilities caps) {
        var workspace = caps.getWorkspace();
        if (workspace == null) {
            workspace = new WorkspaceServerCapabilities();
            caps.setWorkspace(workspace);
        }
        var fileOps = workspace.getFileOperations();
        if (fileOps == null) {
            fileOps = new FileOperationsServerCapabilities();
            workspace.setFileOperations(fileOps);
        }
        return fileOps;
    }

    /**
     * File created notification capability.
     *
     * @see https://microsoft.github.io/language-server-protocol/specifications/lsp/3.18/specification/#workspace_didCreateFiles
     */
    public static class DidCreateFiles extends FileOperationCapability {

        public DidCreateFiles(Executor exec) {
            super("workspace/didCreateFiles", exec);
        }

        @Override
        protected void registerStatically(ServerCapabilities result) {
            fileOperationCapabilities(result).setDidCreate(staticOptions());
        }

    }

    /**
     * File deleted notification capability.
     *
     * @see https://microsoft.github.io/language-server-protocol/specifications/lsp/3.18/specification/#workspace_didDeleteFiles
     */
    public static class DidDeleteFiles extends FileOperationCapability {

        public DidDeleteFiles(Executor exec) {
            super("workspace/didDeleteFiles", exec);
        }

        @Override
        protected void registerStatically(ServerCapabilities result) {
            fileOperationCapabilities(result).setDidDelete(staticOptions());
        }

    }

    /**
     * File renamed notification capability.
     *
     * @see https://microsoft.github.io/language-server-protocol/specifications/lsp/3.18/specification/#workspace_didRenameFiles
     */
    public static class DidRenameFiles extends FileOperationCapability {

        public DidRenameFiles(Executor exec) {
            super("workspace/didRenameFiles", exec);
        }

        @Override
        protected void registerStatically(ServerCapabilities result) {
            fileOperationCapabilities(result).setDidRename(staticOptions());
        }

    }

}
