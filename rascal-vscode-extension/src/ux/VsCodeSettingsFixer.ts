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
import * as JSON5 from 'json5';
import { posix } from 'path';
import * as vscode from 'vscode';

const VSCODE_DIR = ".vscode";
const SETTINGS_FILE = "settings.json";

const SEARCH_EXCLUDE_SETTING_KEY = "search.exclude";
const EXCLUDE_ENTRY = `"/target/": true,`;
const SEARCH_EXCLUDE_SETTING = `
    "${SEARCH_EXCLUDE_SETTING_KEY}": {
        ${EXCLUDE_ENTRY}
    },
`;
const DEFAULT_SETTINGS = `{
    ${SEARCH_EXCLUDE_SETTING}
}`;

const TARGET_IN_SEARCH_WARNING = "The projects target folder appears in search results. Editing those files breaks the project build.";
const EXCLUDE_TARGET_TITLE = "Exclude target from search";

const Y = "Create settings.json and ignore target folder";
const N = "Do not update settings";

/**
 * Create/fix the VS Code settings file and suggest appropriate settings for Rascal projects.
 */
export class VsCodeSettingsFixer implements vscode.Disposable {

    private readonly diagnostics: vscode.DiagnosticCollection;
    private readonly disposables: vscode.Disposable[] = [];

    constructor () {
        this.diagnostics = vscode.languages.createDiagnosticCollection("VSCode workspace settings diagnostics");
        this.disposables.push(this.diagnostics);

        vscode.workspace.onDidChangeWorkspaceFolders(async changes => {
            // Clean up diagnostics when closing a project
            for (const r of changes.removed) {
                this.clearWorkspaceDiagnostics(r.uri);
            }
            // Fix settings for newly opened projects
            for (const a of changes.added) {
                await this.createAndFixWorkspaceSettings(a.uri);
            }
        });

        const settingsGlob = posix.join("**", VSCODE_DIR, SETTINGS_FILE);
        const watcher = vscode.workspace.createFileSystemWatcher(settingsGlob);
        watcher.onDidCreate(f => this.fixWorkspaceSettings(this.projectRootFromSettings(f)), this, this.disposables);
        watcher.onDidChange(f => this.fixWorkspaceSettings(this.projectRootFromSettings(f)), this, this.disposables);
        watcher.onDidDelete(this.clearFileDiagnostics, this, this.disposables);
        this.disposables.push(watcher);

        // Fix settings for currently open projects
        for (const projectRoot of vscode.workspace.workspaceFolders || []) {
            this.createAndFixWorkspaceSettings(projectRoot.uri); // Do not await; process workspaces in parallel
        }

        // Register code actions
        this.disposables.push(vscode.languages.registerCodeActionsProvider({ pattern: settingsGlob }, new FixSettingsActions()));
    }

    private projectRootFromSettings(uri: vscode.Uri): vscode.Uri {
        return uri.with({ path: posix.dirname(posix.dirname(uri.path)) });
    }

    private clearWorkspaceDiagnostics(uri: vscode.Uri) {
        this.clearFileDiagnostics(vscode.Uri.joinPath(uri, VSCODE_DIR, SETTINGS_FILE));
    }

    private clearFileDiagnostics(uri: vscode.Uri) {
        this.diagnostics.delete(uri);
    }

    private async createAndFixWorkspaceSettings(projectRoot: vscode.Uri) {
        await this.createWorkspaceSettings(projectRoot);
        await this.fixWorkspaceSettings(projectRoot);
    }

    private async createWorkspaceSettings(projectRoot: vscode.Uri) {
        const projectName = posix.basename(projectRoot.path);
        const settingsFile = vscode.Uri.joinPath(projectRoot, VSCODE_DIR, SETTINGS_FILE);

        try {
            await vscode.workspace.fs.stat(settingsFile);
            return;
        } catch (_missing) { /* continue */ }

        // Offer to create the missing file.
        const res = await vscode.window.showInformationMessage(`Project '${projectName}' does not have a VS Code settings file. ${TARGET_IN_SEARCH_WARNING}`, Y, N);
        if (res !== Y) {
            return;
        }
        await vscode.workspace.fs.writeFile(settingsFile, Buffer.from(DEFAULT_SETTINGS));
    }

    private async fixWorkspaceSettings(projectRoot: vscode.Uri) {
        const settingsDiagnostics: vscode.Diagnostic[] = [];
        const settingsFile = vscode.Uri.joinPath(projectRoot, VSCODE_DIR, SETTINGS_FILE);

        try {
            try {
                await vscode.workspace.fs.stat(settingsFile);
            } catch (_missing) {
                // If it does not exist, we have nothing to do here.
                return;
            }

            const settingsDoc = await vscode.workspace.openTextDocument(settingsFile);
            const contents = settingsDoc.getText();

            const d = new vscode.Diagnostic(new vscode.Range(new vscode.Position(0, 0), settingsDoc.positionAt(Number.MAX_VALUE)), TARGET_IN_SEARCH_WARNING, vscode.DiagnosticSeverity.Warning);
            if (contents.trim().length === 0) {
                // Empty file; insert full settings
                d.code = FixKind.insertSettings;
            } else {
                // Settings might have comments, trailing commas, etc., so we use JSON5.
                const settings = JSON5.parse(contents);
                if (!(SEARCH_EXCLUDE_SETTING_KEY in settings)) {
                    d.code = FixKind.insertSearchExclude;
                } else if (!("target" in settings[SEARCH_EXCLUDE_SETTING_KEY] || "/target/" in settings[SEARCH_EXCLUDE_SETTING_KEY])) {
                    const excludeStart = contents.indexOf(SEARCH_EXCLUDE_SETTING_KEY);
                    const excludeEnd = excludeStart + SEARCH_EXCLUDE_SETTING_KEY.length;
                    d.range = new vscode.Range(settingsDoc.positionAt(excludeStart), settingsDoc.positionAt(excludeEnd));
                    d.code = FixKind.insertTargetExclude;
                }
            }
            settingsDiagnostics.push(d);
        } catch (e) {
            // The settings are not valid JSON, or something else we cannot recover from.
        } finally {
            this.diagnostics.set(settingsFile, settingsDiagnostics);
        }
    }

    dispose() {
        this.disposables.forEach(d => d.dispose());
    }

}

function insertQuickFix(title: string, insertText: string, insertPosition: number, diag: vscode.Diagnostic, document: vscode.TextDocument) {
    const action = new vscode.CodeAction(title, vscode.CodeActionKind.QuickFix);
    action.diagnostics = [diag];
    action.edit = new vscode.WorkspaceEdit();
    action.edit.insert(document.uri, document.positionAt(insertPosition), insertText);
    return action;
}

class FixSettingsActions implements vscode.CodeActionProvider {
    provideCodeActions(document: vscode.TextDocument, _range: vscode.Range | vscode.Selection, context: vscode.CodeActionContext, _token: vscode.CancellationToken): vscode.ProviderResult<(vscode.CodeAction | vscode.Command)[]> {
        const result: vscode.CodeAction[] = [];
        for (const d of context.diagnostics) {
            switch (d.code) {
                case FixKind.insertSettings: {
                    result.push(insertQuickFix(EXCLUDE_TARGET_TITLE, DEFAULT_SETTINGS, 0, d, document));
                    break;
                }
                case FixKind.insertSearchExclude: {
                    result.push(insertQuickFix(EXCLUDE_TARGET_TITLE, SEARCH_EXCLUDE_SETTING, document.getText().indexOf("{") + 1, d, document));
                    break;
                }
                case FixKind.insertTargetExclude: {
                    const searchExcludes = document.getText().indexOf("search.exclude");
                    const insertLocation = document.getText().indexOf("{", searchExcludes) + 1;
                    result.push(insertQuickFix(EXCLUDE_TARGET_TITLE, EXCLUDE_ENTRY, insertLocation, d, document));
                    break;
                }
            }
        }
        return result;
    }

}

enum FixKind {
    insertSettings,
    insertSearchExclude,
    insertTargetExclude,
}
