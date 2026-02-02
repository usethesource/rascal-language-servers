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
import * as jsonc from 'jsonc-parser';
import { posix } from 'path';
import * as vscode from 'vscode';
import { buildMFChildPath, isRascalProject } from './RascalMFValidator';

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

/**
 * Create/fix the VS Code settings file and suggest appropriate settings for Rascal projects.
 */
export class VsCodeSettingsFixer implements vscode.Disposable {

    private readonly diagnostics: vscode.DiagnosticCollection;
    private readonly disposables: vscode.Disposable[] = [];

    constructor () {
        this.diagnostics = vscode.languages.createDiagnosticCollection("Rascal project settings");
        this.disposables.push(this.diagnostics);

        vscode.workspace.onDidChangeWorkspaceFolders(async changes => {
            // Clean up diagnostics when closing a project
            for (const r of changes.removed) {
                this.clearWorkspaceDiagnostics(r.uri);
            }
            // Fix settings for newly opened projects
            for (const a of changes.added) {
                await this.fixSettings(a.uri);
            }
        });

        const settingsGlob = posix.join("**", VSCODE_DIR, SETTINGS_FILE);
        const watcher = vscode.workspace.createFileSystemWatcher(settingsGlob);
        watcher.onDidCreate(f => this.fixSettings(this.projectRootFromSettings(f)), this, this.disposables);
        watcher.onDidChange(f => this.fixSettings(this.projectRootFromSettings(f)), this, this.disposables);
        watcher.onDidDelete(this.clearFileDiagnostics, this, this.disposables);
        this.disposables.push(watcher);

        // Fix settings for currently open projects
        for (const projectRoot of vscode.workspace.workspaceFolders || []) {
            this.fixSettings(projectRoot.uri); // Do not await; process workspaces in parallel
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

    private async fixSettings(projectRoot: vscode.Uri) {
        if (!await isRascalProject(projectRoot)) {
            return;
        }

        const res = await createSettingsDiagnostic(projectRoot);
        if (res) {
            this.diagnostics.set(res.uri, [res.diag]);
        }
    }

    dispose() {
        this.disposables.forEach(d => d.dispose());
    }

}

async function createSettingsDiagnostic(projectRoot: vscode.Uri): Promise<{uri: vscode.Uri, diag: vscode.Diagnostic} | undefined> {
    const warning = "The projects target folder appears in search results. Editing those files breaks the project build.";

    if (!await hasSettingsFile(projectRoot)) {
        // Settings file does not exist. Put the diagnostic on the RASCAL.MF file instead.
        const manifest = await vscode.workspace.openTextDocument(buildMFChildPath(projectRoot));
        const d = new vscode.Diagnostic(manifest.lineAt(0).range, warning, vscode.DiagnosticSeverity.Warning);
        d.code = {
            value: FixKind.createSettings,
            target: buildSettingsPath(projectRoot),
        };
        return {uri: manifest.uri, diag: d};
    }

    // Settings file exists
    const settingsDoc = await vscode.workspace.openTextDocument(buildSettingsPath(projectRoot));
    const settings = jsonc.parse(settingsDoc.getText());
    if (settings && ("target" in settings[SEARCH_EXCLUDE_SETTING_KEY] || "/target/" in settings[SEARCH_EXCLUDE_SETTING_KEY])) {
        return;
    }

    const d = new vscode.Diagnostic(new vscode.Range(new vscode.Position(0, 0), new vscode.Position(0, 0)), warning, vscode.DiagnosticSeverity.Warning);
    d.code = {
        value: FixKind.fixTargetExclude,
        target: buildSettingsPath(projectRoot),
    };
    return {uri: settingsDoc.uri, diag: d};
}

async function hasSettingsFile(projectRoot: vscode.Uri) {
    try {
        await vscode.workspace.fs.stat(buildSettingsPath(projectRoot));
        return true;
    } catch (_missing) {
        return false;
    }
}

function buildSettingsPath(projectRoot: vscode.Uri) {
    return vscode.Uri.joinPath(projectRoot, VSCODE_DIR, SETTINGS_FILE);
}

class FixSettingsActions implements vscode.CodeActionProvider {
    provideCodeActions(document: vscode.TextDocument, _range: vscode.Range | vscode.Selection, context: vscode.CodeActionContext, _token: vscode.CancellationToken): vscode.ProviderResult<(vscode.CodeAction | vscode.Command)[]> {
        let result: Promise<vscode.CodeAction[]> = new Promise(_ => []);
        for (const d of context.diagnostics) {
            if (d.code instanceof Object) {
                switch (d.code.value) {
                    case FixKind.createSettings: {
                        const settingsUri = d.code.target;
                        result = result.then(r => r.concat(this.createSettings(settingsUri, d)));
                        break;
                    }
                    case FixKind.fixTargetExclude: {
                        result = result.then(r => r.concat(this.fixTargetExclude(document, d)));
                        break;
                    }
                }
            }
        }
        return result;
    }

    private createSettings(settingsUri: vscode.Uri, diag: vscode.Diagnostic): vscode.CodeAction {
        const d = new vscode.CodeAction("Exclude target from search", vscode.CodeActionKind.QuickFix);
        d.edit = new vscode.WorkspaceEdit();
        d.edit.createFile(settingsUri, { contents: Buffer.from(DEFAULT_SETTINGS) });
        d.diagnostics = [diag];
        return d;
    }

    private fixTargetExclude(settingsDoc: vscode.TextDocument, diag: vscode.Diagnostic): vscode.CodeAction {
        const d = new vscode.CodeAction("Exclude target from search", vscode.CodeActionKind.QuickFix);
        const edits = jsonc.modify(settingsDoc.getText(), ["search.exclude", "/target/"], true, {});
        d.edit = new vscode.WorkspaceEdit();
        d.edit.set(settingsDoc.uri, this.convertEdits(settingsDoc, edits));
        d.diagnostics = [diag];
        return d;
    }

    private convertEdits(settingsDoc: vscode.TextDocument, edits: jsonc.Edit[]): vscode.TextEdit[] {
        return edits.map(e => {
            const start = settingsDoc.positionAt(e.offset);
            const end = settingsDoc.positionAt(e.offset + e.length);
            return new vscode.TextEdit(new vscode.Range(start, end), e.content);
        });
    }
}

enum FixKind {
    createSettings,
    fixTargetExclude,
}
