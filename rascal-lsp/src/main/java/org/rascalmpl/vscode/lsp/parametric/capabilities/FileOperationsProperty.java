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
import java.util.stream.Collectors;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.FileOperationFilter;
import org.eclipse.lsp4j.FileOperationOptions;
import org.eclipse.lsp4j.FileOperationPattern;
import org.eclipse.lsp4j.FileOperationsServerCapabilities;
import org.eclipse.lsp4j.ServerCapabilities;

public class FileOperationsProperty extends AbstractDynamicProperty<FileOperationOptions> {

    public FileOperationsProperty() {
        super("workspace.fileOperations.willCreate", false);
    }

    @Override
    protected @Nullable FileOperationOptions mergeOptions(@Nullable FileOperationOptions right, @Nullable FileOperationOptions left) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        if (left.equals(right)) {
            return left;
        }

        var mergedOpts = new ArrayList<FileOperationFilter>(left.getFilters().size() + right.getFilters().size());
        mergedOpts.addAll(left.getFilters());
        mergedOpts.addAll(right.getFilters());
        return new FileOperationOptions(mergedOpts);
    }

    @Override
    protected boolean isDynamicallySupportedBy(ClientCapabilities clientCapabilities) {
        return clientCapabilities.getWorkspace().getFileOperations().getDynamicRegistration();
    }

    @Override
    protected void registerStatically(ServerCapabilities serverCapabilities) {
        var filters = new FileOperationOptions(List.of(new FileOperationFilter(new FileOperationPattern("**/*"))));
        var fileOps = new FileOperationsServerCapabilities();
        fileOps.setDidRename(filters);
        fileOps.setDidDelete(filters);
        serverCapabilities.getWorkspace().setFileOperations(fileOps);
    }

    @Override
    protected CompletableFuture<Boolean> hasProperty(ICapabilityParams params) {
        return CompletableFuture.completedFuture(!params.extensions().isEmpty());
    }

    @Override
    protected CompletableFuture<@Nullable FileOperationOptions> options(ICapabilityParams params) {
        return CompletableFuture.completedFuture(new FileOperationOptions(params.extensions().stream()
            .map(ext -> new FileOperationFilter(new FileOperationPattern(String.format("**/*.%s", ext))))
            .collect(Collectors.toList())
        ));
    }


}
