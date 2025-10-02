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
package org.rascalmpl.vscode.lsp.rascal.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.services.LanguageClient;
import org.rascalmpl.vscode.lsp.util.Diagnostics;
import org.rascalmpl.vscode.lsp.util.locations.ColumnMaps;

import io.usethesource.vallang.IList;
import io.usethesource.vallang.ISourceLocation;

/*package*/ class PathConfigDiagnostics {
    private final LanguageClient client;
    private final ColumnMaps cm;
    private final Map<ISourceLocation, Map<ISourceLocation, List<Diagnostic>>> publishedProjectDiagsPerFile = new HashMap<>();
    private final Map<ISourceLocation, Set<ISourceLocation>> filesWithDiagsPerProject = new HashMap<>();

    /*package*/ PathConfigDiagnostics(LanguageClient client, ColumnMaps cm) {
        this.client = client;
        this.cm = cm;
    }

    public void publishDiagnostics(ISourceLocation project, IList messages) {
        // Gather messages per file
        Map<ISourceLocation, List<Diagnostic>> diagnosticsPerFile = Diagnostics.translateMessages(messages, cm);

        // Publish diagnostics, but only for files for which diagnostics have changed
        for (var entry : diagnosticsPerFile.entrySet()) {
            ISourceLocation file = entry.getKey().top();
            List<Diagnostic> newDiagnostics = entry.getValue();

            Map<ISourceLocation, List<Diagnostic>> fileDiagsPerProject = publishedProjectDiagsPerFile.computeIfAbsent(file, (f) -> new LinkedHashMap<>());
            @Nullable List<Diagnostic> publishedDiags = fileDiagsPerProject.get(file);

            // Only publish diagnostics for a file if the diagnostics for that file have changed in the current project
            if (!newDiagnostics.equals(publishedDiags)) {
                // The diagnostics for this file related to the current project have changed.
                // We need to re-publish all diagnostics for this file (including the ones from other projects)
                fileDiagsPerProject.put(project, newDiagnostics);
                publishFileDiagnostics(file);
            }
        }

        // Now we have published the diagnostics of all files that occur in the list of new messages
        // Time to remove diagnostics from files that our project previously published diagnostics for but no longer have diagnostics
        Set<ISourceLocation> newFiles = diagnosticsPerFile.keySet().stream().map(file -> file.top()).collect(Collectors.toSet());
        Set<ISourceLocation> filesWithoutDiags = new HashSet<>(filesWithDiagsPerProject.getOrDefault(project, Collections.emptySet()));
        filesWithoutDiags.removeAll(newFiles);

        for (ISourceLocation file : filesWithoutDiags) {
            publishedProjectDiagsPerFile.get(file).remove(project); // File no longer has diagnostics associated with it in our project
            publishFileDiagnostics(file);
        }
        filesWithDiagsPerProject.put(project, newFiles);
    }

    private void publishFileDiagnostics(ISourceLocation file) {
        List<Diagnostic> fileDiagnostics = new ArrayList<>();
        for (List<Diagnostic> diags :  publishedProjectDiagsPerFile.getOrDefault(file, Collections.emptyMap()).values()) {
            fileDiagnostics.addAll(diags);
        }
        client.publishDiagnostics(new PublishDiagnosticsParams(file.getURI().toString(), fileDiagnostics));
    }
}
