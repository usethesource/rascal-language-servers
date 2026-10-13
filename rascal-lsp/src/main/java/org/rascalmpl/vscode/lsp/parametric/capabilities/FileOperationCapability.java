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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.DynamicRegistrationCapabilities;
import org.eclipse.lsp4j.FileOperationFilter;
import org.eclipse.lsp4j.FileOperationOptions;
import org.eclipse.lsp4j.FileOperationPattern;
import org.eclipse.lsp4j.FileOperationPatternKind;
import org.eclipse.lsp4j.FileOperationPatternOptions;
import org.eclipse.lsp4j.FileOperationsServerCapabilities;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.WorkspaceClientCapabilities;
import org.eclipse.lsp4j.WorkspaceServerCapabilities;
import org.rascalmpl.vscode.lsp.util.Nullables;
import org.rascalmpl.vscode.lsp.util.Sets;

/**
 * Collection of capabilities related to file operations.
 */
public abstract class FileOperationCapability extends WorkspaceCapability<FileOperationOptions> {

    FileOperationCapability(String propertyName) {
        super(propertyName);
    }

    @Override
    public @Nullable DynamicRegistrationCapabilities getCapabilities(WorkspaceClientCapabilities caps) {
        return caps.getFileOperations();
    }

    @Override
    public final CompletableFuture<Boolean> isProvidedBy(ICapabilityParams params) {
        return CompletableFuture.completedFuture(!params.fileExtensions().isEmpty());
    }

    @Override
    public final FileOperationOptions mergeOptions(FileOperationOptions o1, FileOperationOptions o2) {
        return new FileOperationOptions(new ArrayList<>(Sets.union(o1.getFilters(), o2.getFilters())));
    }

    @Override
    public final CompletableFuture<@Nullable FileOperationOptions> options(ICapabilityParams params) {
        var patterns = params.fileExtensions().stream()
            .map(FileOperationCapability::extensionFilter)
            .collect(Collectors.toList());

        for (var glob : folderOperationGlobs()) {
            var pattern = new FileOperationPattern(glob);
            pattern.setMatches(FileOperationPatternKind.Folder);
            patterns.add(new FileOperationFilter(pattern));
        }

        for (var glob : fileOperationGlobs()) {
            var pattern = new FileOperationPattern(glob);
            pattern.setMatches(FileOperationPatternKind.File);
            patterns.add(new FileOperationFilter(pattern));
        }

        return CompletableFuture.completedFuture(new FileOperationOptions(patterns));
    }

    protected List<String> folderOperationGlobs() {
        // By default, do receive notifications about each folder
        return List.of("**/*");
    }

    protected List<String> fileOperationGlobs() {
        // By default, don't receive notifications about any file
        return Collections.emptyList();
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

    @Override
    public final void registerStatically(ServerCapabilities caps) {
        var workspace = Nullables.ensureNonNullAndGet(caps, ServerCapabilities::getWorkspace, ServerCapabilities::setWorkspace, WorkspaceServerCapabilities::new);
        var fileOps =  Nullables.ensureNonNullAndGet(workspace, WorkspaceServerCapabilities::getFileOperations, WorkspaceServerCapabilities::setFileOperations, FileOperationsServerCapabilities::new);
        registerStatically(fileOps);
    }

    protected abstract void registerStatically(FileOperationsServerCapabilities fileOperationCapabilities);

    /**
     * File deleted notification capability.
     *
     * @see https://microsoft.github.io/language-server-protocol/specifications/lsp/3.18/specification/#workspace_didDeleteFiles
     */
    public static class DidDeleteFiles extends FileOperationCapability {

        public DidDeleteFiles() {
            super("didDeleteFiles");
        }

        @Override
        public void registerStatically(FileOperationsServerCapabilities result) {
            result.setDidDelete(staticOptions());
        }

        @Override
        protected List<String> fileOperationGlobs() {
            // Receiving notifications about extension-less files would be enough, but it seems "extension-less file"
            // cannot be expressed using LSP's glob patterns.
            return List.of("**/*");
        }
    }

    /**
     * File renamed notification capability.
     *
     * @see https://microsoft.github.io/language-server-protocol/specifications/lsp/3.18/specification/#workspace_didRenameFiles
     */
    public static class DidRenameFiles extends FileOperationCapability {

        public DidRenameFiles() {
            super("didRenameFiles");
        }

        @Override
        public void registerStatically(FileOperationsServerCapabilities result) {
            result.setDidRename(staticOptions());
        }

    }

}
