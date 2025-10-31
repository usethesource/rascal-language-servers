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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.services.LanguageClient;
import org.rascalmpl.vscode.lsp.util.Diagnostics;
import org.rascalmpl.util.locations.ColumnMaps;

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

    private final Map<ISourceLocation, Map<ISourceLocation, List<Diagnostic>>> perFileProjectDiagnostics = new LinkedHashMap<>();

    /*package*/ PathConfigDiagnostics(LanguageClient client, ColumnMaps cm) {
        this.client = client;
        this.cm = cm;
    }

    public void publishDiagnostics(ISourceLocation project, IList messages) {
        // Gather messages per file
        publishDiagnostics(project, Diagnostics.translateMessages(messages, cm));
    }

    public void clearDiagnostics(ISourceLocation project) {
        publishDiagnostics(project, Collections.emptyMap());
    }

    private void publishDiagnostics(ISourceLocation project, Map<ISourceLocation, List<Diagnostic>> diagnostics) {
        for (PublishDiagnosticsParams params : updateDiagnostics(project, diagnostics)) {
            client.publishDiagnostics(params);
        }
    }

    // Update diagnostics for a set of files in a single project. Gather files that need republishing because their
    // diagnostics have changed. This can include files outside the set because diagnostics may have to be removed.
    private synchronized List<PublishDiagnosticsParams> updateDiagnostics(ISourceLocation project, Map<ISourceLocation, List<Diagnostic>> diagnostics) {
        Set<ISourceLocation> filesToRepublish = new HashSet<>();
        for (var entry : diagnostics.entrySet()) {
            ISourceLocation file = entry.getKey().top();
            List<Diagnostic> newDiagnostics = entry.getValue();

            // Determine which files get new diagnostics
            updateFileDiagnostics(project, file, newDiagnostics, filesToRepublish);
        }

        // Cleanup diagnostics for files that no longer have diagnostics for our project
        cleanFilesWithoutDiagnostics(project, diagnostics, filesToRepublish);

        // Now gather the diagnostics for all files that need it.
        return gatherPublishList(filesToRepublish);
    }

    // Update the diagnostics of a single file. Add any files that need republishing to the `filesToRepublish` set
    private void updateFileDiagnostics(ISourceLocation project, ISourceLocation file, List<Diagnostic> diagnostics, Set<ISourceLocation> filesToRepublish) {
        Map<ISourceLocation, List<Diagnostic>> publishedDiagsPerProject = perFileProjectDiagnostics.computeIfAbsent(file, f -> new LinkedHashMap<>());
        List<Diagnostic> publishedForOurProject = publishedDiagsPerProject.get(project);
        if (publishedForOurProject == null || !publishedForOurProject.equals(diagnostics)) {
            publishedDiagsPerProject.put(project, diagnostics);
            filesToRepublish.add(file);
        }
    }

    private void cleanFilesWithoutDiagnostics(ISourceLocation project, Map<ISourceLocation, List<Diagnostic>> diagnostics, Set<ISourceLocation> filesToRepublish) {
        // Build the set of project files from the diagnostic source locations so we can quickly check if a file has new diagnostics
        Set<ISourceLocation> diagnosticFiles = new HashSet<>();
        for (ISourceLocation loc : diagnostics.keySet()) {
            diagnosticFiles.add(loc.top());
        }

        // Remove our project diagnostics from files that have them but are not in the new list of diagnostics
        perFileProjectDiagnostics.entrySet().removeIf(entry -> {
            ISourceLocation file = entry.getKey();
            Map<ISourceLocation, List<Diagnostic>> projectDiagnostics = entry.getValue();
            if (!diagnosticFiles.contains(file) && projectDiagnostics.remove(project) != null) {
                filesToRepublish.add(file);
                if (projectDiagnostics.isEmpty()) {
                    // Remove entry for this file when no more projects with diagnostics for this file are left
                    return true;
                }
            }

            return false;
        });
    }

    private List<PublishDiagnosticsParams> gatherPublishList(Set<ISourceLocation> filesToRepublish) {
        List<PublishDiagnosticsParams> publishList = new ArrayList<>(filesToRepublish.size());
        for (ISourceLocation file : filesToRepublish) {
            List<Diagnostic> fileDiagnostics = new ArrayList<>();
            // Iterate over all files and gather the diagnostics for all projects that have diagnostics for this file
            for (List<Diagnostic> diags : perFileProjectDiagnostics.getOrDefault(file, Collections.emptyMap()).values()) {
                fileDiagnostics.addAll(diags);
            }
            publishList.add(new PublishDiagnosticsParams(file.getURI().toString(), fileDiagnostics));
        }
        return publishList;
    }

}
