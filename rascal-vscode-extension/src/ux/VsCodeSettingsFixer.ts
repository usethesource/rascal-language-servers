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
import * as fs from 'fs/promises';
import { posix } from 'path';
import * as vscode from 'vscode';

const VSCODE_DIR = ".vscode";
const SETTINGS_FILE = "settings.json";
const SEARCH_EXCLUDE = `
    "search.exclude": {
        "/target/": true
    },
`;
export class VsCodeSettingsFixer implements vscode.Disposable {

    private readonly diagnostics: vscode.DiagnosticCollection;
    private readonly disposables: vscode.Disposable[] = [];

    constructor () {
        this.diagnostics = vscode.languages.createDiagnosticCollection("VSCode workspace settings diagnostics");

        vscode.workspace.onDidChangeWorkspaceFolders(async changes => {
            // Clean up diagnostics when closing a project
            for (const r of changes.removed) {
                this.diagnostics.delete(buildSettingsPath(r.uri));
            }
            // Fix settings for newly opened projects
            for (const a of changes.added) {
                await this.fixWorkspaceSettings(a.uri);
            }
        });

        const watcher = vscode.workspace.createFileSystemWatcher(posix.join("**", VSCODE_DIR, SETTINGS_FILE));
        watcher.onDidChange(this.fixWorkspaceSettings, this, this.disposables);
        watcher.onDidDelete(e => this.diagnostics.delete(e), this, this.disposables);

        // Fix settings for currently open projects
        for (const projectRoot of vscode.workspace.workspaceFolders || []) {
            this.fixWorkspaceSettings(projectRoot.uri); // Note dangling promise
        }

        // Register code actions
        this.disposables.push(vscode.languages.registerCodeActionsProvider({ pattern: posix.join("**", VSCODE_DIR, SETTINGS_FILE) }, new FixSettingsActions()));
    }

    private async fixWorkspaceSettings(projectRoot: vscode.Uri) {
        const vsCodeDir = vscode.Uri.joinPath(projectRoot, VSCODE_DIR);
        const settingsFile = vscode.Uri.joinPath(vsCodeDir, SETTINGS_FILE);

        const settingsDoc = await vscode.workspace.openTextDocument(settingsFile);
        const contents = settingsDoc.getText();
        if (contents.trim().length === 0) {
            await fs.writeFile(settingsFile.toString(), "{}");
        }
        try {
            const settings = JSON.parse(contents);
            if (!("search.exclude" in settings)) {
                const d = new vscode.Diagnostic(new vscode.Range(new vscode.Position(0, 0), new vscode.Position(0, 0)), "Files in `target` appear in search results. Be careful; editing those files breaks the project build.", vscode.DiagnosticSeverity.Warning);
                d.code = FixKind.insertSearchExclude;
                this.diagnostics.set(settingsFile, [d]);
            }
        } catch { /* ignore errors */ }
    }

    dispose() {
        this.disposables.forEach(d => d.dispose());
    }

}

function buildSettingsPath(projectRoot: vscode.Uri) {
    return vscode.Uri.joinPath(projectRoot, VSCODE_DIR, SETTINGS_FILE);
}

class FixSettingsActions implements vscode.CodeActionProvider {
    provideCodeActions(document: vscode.TextDocument, _range: vscode.Range | vscode.Selection, context: vscode.CodeActionContext, _token: vscode.CancellationToken): vscode.ProviderResult<(vscode.CodeAction | vscode.Command)[]> {
        const result: vscode.CodeAction[] = [];
        for (const d of context.diagnostics) {
            switch (d.code) {
                case FixKind.insertSearchExclude: {
                    const beginOfSettings = document.positionAt(document.getText().indexOf("{") + 1);
                    const addSearchExclude = new vscode.CodeAction("Exclude `target` from search", vscode.CodeActionKind.QuickFix);
                    addSearchExclude.diagnostics = [d];
                    addSearchExclude.edit = new vscode.WorkspaceEdit();
                    addSearchExclude.edit.insert(document.uri, beginOfSettings, SEARCH_EXCLUDE);
                    result.push(addSearchExclude);
                    break;
                }
            }
        }
        return result;
    }

}

enum FixKind {
    insertSearchExclude
}
