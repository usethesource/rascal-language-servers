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

/*
 * This class updates VSCode diagnostics based on PathConfig messages.
 * Messages are tracked by project and by file. So changes in one project will not affect
 * messages genrated by other projects, even if files have messages belonging to different projects.
 */
/*package*/ class PathConfigDiagnostics {
    private final LanguageClient client;
    private final ColumnMaps cm;

    // For each file, keep track of the list of diagnostics per project.
    private final Map<ISourceLocation, Map<ISourceLocation, List<Diagnostic>>> projectDiagsPerFile = new HashMap<>();

    // For each project, keep track of the set of files that currently have diagnostics for that project
    private final Map<ISourceLocation, Set<ISourceLocation>> filesWithDiagsPerProject = new HashMap<>();

    /*package*/ PathConfigDiagnostics(LanguageClient client, ColumnMaps cm) {
        this.client = client;
        this.cm = cm;
    }

    public void publishDiagnostics(ISourceLocation project, IList messages) {
        // Gather messages per file
        publishDiagnostics(project, Diagnostics.translateMessages(messages, cm));
    }

    private void publishDiagnostics(ISourceLocation project, Map<ISourceLocation, List<Diagnostic>> diagnostics) {
        Set<ISourceLocation> filesToRepublish = new HashSet<>();

        // Do the actual manipulation of the internal datastructures in a synchronized block
        // so their coherence is guaranteed.
        synchronized(this) {
            // Publish diagnostics, but only for files for which diagnostics have changed
            for (var entry : diagnostics.entrySet()) {
                ISourceLocation file = entry.getKey().top();
                List<Diagnostic> newDiagnostics = entry.getValue();

                Map<ISourceLocation, List<Diagnostic>> diagsPerProject = projectDiagsPerFile.computeIfAbsent(file, (f) -> new LinkedHashMap<>());
                @Nullable List<Diagnostic> publishedDiags = diagsPerProject.get(project);

                // Only publish diagnostics for a file if the diagnostics for that file have changed in the current project
                if (!newDiagnostics.equals(publishedDiags)) {
                    // The diagnostics for this file related to the current project have changed.
                    // We need to re-publish all diagnostics for this file (including the ones from other projects)
                    diagsPerProject.put(project, newDiagnostics);
                    filesToRepublish.add(file);
                }
            }

            // Now we have published the diagnostics of all files that occur in the list of new messages
            // Time to remove diagnostics from files that our project previously published diagnostics for but no longer have diagnostics
            Set<ISourceLocation> newFiles = diagnostics.keySet().stream().map(ISourceLocation::top).collect(Collectors.toSet());
            Set<ISourceLocation> filesWithoutDiags = new HashSet<>(filesWithDiagsPerProject.getOrDefault(project, Collections.emptySet()));
            filesWithoutDiags.removeAll(newFiles);

            for (ISourceLocation file : filesWithoutDiags) {
                // File no longer has diagnostics associated with it in our project
                projectDiagsPerFile.get(file).remove(project);
                filesToRepublish.add(file);
            }
            filesWithDiagsPerProject.put(project, newFiles);
        }

        publishFileDiagnostics(filesToRepublish);
    }

    public void clearDiagnostics(ISourceLocation project) {
        publishDiagnostics(project, Collections.emptyMap());
    }

    private void publishFileDiagnostics(Set<ISourceLocation> files) {
        for (ISourceLocation file : files) {
            List<Diagnostic> fileDiagnostics = new ArrayList<>();
            synchronized (this) {
                // Iterate over all files and gather the diagnostics for all projects that have diagnostics for this file
                for (List<Diagnostic> diags :  projectDiagsPerFile.getOrDefault(file, Collections.emptyMap()).values()) {
                    fileDiagnostics.addAll(diags);
                }
            }
            client.publishDiagnostics(new PublishDiagnosticsParams(file.getURI().toString(), fileDiagnostics));
        }
    }
}
