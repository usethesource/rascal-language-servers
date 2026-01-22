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
import java.util.Set;
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
import org.rascalmpl.vscode.lsp.util.Sets;
import org.rascalmpl.vscode.lsp.util.concurrent.CompletableFutureUtils;

public abstract class FileOperationCapability extends AbstractDynamicCapability<FileOperationOptions> {

    private final Executor exec;

    FileOperationCapability(String propertyName, Executor exec) {
        super(propertyName);
        this.exec = exec;
    }

    final FileOperationOptions fromExtensions(Set<String> extensions) {
        return new FileOperationOptions(extensions.stream()
            .map(ext -> new FileOperationFilter(new FileOperationPattern(String.format("**/*.%s", ext))))
            .collect(Collectors.toList()));
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
        return CompletableFutureUtils.completedFuture(new FileOperationOptions(params.fileExtensions().stream().map(FileOperationCapability::extensionFilter).collect(Collectors.toList())), exec);
    }

    private static FileOperationFilter extensionFilter(String ext) {
        var pat = new FileOperationPattern(String.format("**/*.%s", ext));
        pat.setOptions(new FileOperationPatternOptions(true));
        pat.setMatches(FileOperationPatternKind.File);
        return new FileOperationFilter(pat);
    }

}
