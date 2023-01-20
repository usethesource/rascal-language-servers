/*
 * Copyright (c) 2018-2021, NWO-I CWI and Swat.engineering
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
import * as vscode from 'vscode';
import {posix} from 'path'; // posix path join is always correct, also on windows


const MF_FILE = "RASCAL.MF";
const MF_DIR = "META-INF";

/**
 * Check for common errors in RASCAL.MF files, and try and fix them if possible
 */
export class RascalMFValidator implements vscode.Disposable {
    private readonly diagnostics: vscode.DiagnosticCollection;
    private readonly toDispose: vscode.Disposable[] = [];

    constructor () {
        this.diagnostics = vscode.languages.createDiagnosticCollection("Rascal MF Diagnostics");

        // any new project should be checked
        vscode.workspace.onDidChangeWorkspaceFolders(async ws => {
            for (const added of ws.added) {
                try {
                    const mfURI = buildMFChildPath(added.uri);
                    await vscode.workspace.fs.stat(mfURI);
                    this.verifyRascalMF(mfURI);
                }
                catch (_missingFile) {
                    // most likely not a rascal project
                }
            }
            for (const rem of ws.removed) {
                // clear messages of things no longer in the workspace
                this.diagnostics.delete(buildMFChildPath(rem.uri));
            }
        }, this, this.toDispose);

        // also at the start, check the already opened folders
        for (const openProject of vscode.workspace.workspaceFolders || []) {
            const mfURI = buildMFChildPath(openProject.uri);
            vscode.workspace.fs.stat(mfURI).then(_s => this.verifyRascalMF(mfURI));
        }

        // all changed files should be checked on save (for example a git pull)
        // we use the filesystem watcher since a user might not have the RASCAL.MF open, but we still want to warn them
        // note that warnings/errors are only cleared at a save
        const watcher = vscode.workspace.createFileSystemWatcher("**/" + MF_FILE, true, false, false);
        watcher.onDidChange(this.verifyRascalMF, this, this.toDispose);
        watcher.onDidDelete(e => this.diagnostics.delete(e), this, this.toDispose); // clear errors for a file
        this.toDispose.push(watcher);

        this.toDispose.push(
            vscode.languages.registerCodeActionsProvider(
                {pattern: "**/" + MF_FILE},
                new FixMFErrors()
            )
        );
    }

    dispose() {
        try {
            this.diagnostics.dispose();
        } catch (_e) {}
        for (const d of this.toDispose) {
        try {
            d.dispose();
        } catch (_e) {}
        }
    }

    private async verifyRascalMF(file: vscode.Uri) {
        try {
            if (!/\/target\/classes\//.test(file.path)) {
                const mfBody = await vscode.workspace.openTextDocument(file);
                const diagnostics: vscode.Diagnostic[] = [];
                checkMissingLastLine(mfBody, diagnostics);
                checkIncorrectProjectName(mfBody, diagnostics);
                checkCommonTypo(mfBody, diagnostics);
                this.diagnostics.set(file, diagnostics);
            }
        }
        catch (error) {
        }
    }
}

enum FixKind {
    addNewLine = 1,
    fixProjectName,
    requireLibrariesTypo

}

function checkMissingLastLine(mfBody: vscode.TextDocument, diagnostics: vscode.Diagnostic[]) {
    const lastLine = mfBody.lineAt(mfBody.lineCount - 1);
    if (lastLine.text !== "") {
        const diag = new vscode.Diagnostic(lastLine.range,
            `${MF_FILE} should end with a empty newline (no spaces, no comments), else the previous line is ignored`,
            vscode.DiagnosticSeverity.Error);
        diag.code = FixKind.addNewLine;
        diagnostics.push(diag);
    }
}

function checkIncorrectProjectName(mfBody: vscode.TextDocument, diagnostics: vscode.Diagnostic[]) {
    let hasProjectName = false;
    for (let l = 0; l < mfBody.lineCount; l++) {
        const line = mfBody.lineAt(l);
        const kvPair = line.text.split(":");
        if (kvPair.length === 2 && kvPair[0].trim() === "Project-Name") {
            hasProjectName = true;
            const prName = kvPair[1].split("#")[0].trim();
            const expectedName = calculateProjectName(mfBody.uri);
            if (prName !== expectedName) {
                const offset = line.text.indexOf(prName);
                const targetRange = new vscode.Range(
                    l, offset,
                    l, offset + prName.length
                );
                const diag = new vscode.Diagnostic(targetRange,
                    `Incorrect project-name, it should equal the directory name (${expectedName})`, vscode.DiagnosticSeverity.Error);
                diag.code = FixKind.fixProjectName;
                diagnostics.push(diag);
            }
        }
    }
    if (!hasProjectName) {
        diagnostics.push(new vscode.Diagnostic(
            new vscode.Range(0,0,0,0), "Missing project name, please add a 'Project-Name' property"
        ));
    }
}

const commonTypos = new Set<string>(['required-libraries', 'require-library', 'required-library']);

function checkCommonTypo(mfBody: vscode.TextDocument, diagnostics: vscode.Diagnostic[]) {
    for (let l = 0; l < mfBody.lineCount; l++) {
        const line = mfBody.lineAt(l);
        const kvPair = line.text.split(":");
        if (kvPair.length >= 2) {
            const key = kvPair[0].trim();
            if (commonTypos.has(key.toLocaleLowerCase())) {
                const originalLabel = kvPair[0];
                const diag = new vscode.Diagnostic(
                    new vscode.Range(l, 0, l, originalLabel.length),
                    `"${originalLabel} should be Require-Libraries`
                );
                diag.code = FixKind.requireLibrariesTypo;
                diagnostics.push(diag);
            }
        }
    }

}


function hasNewline(tl: vscode.TextLine) {
    return tl.range.end.character !== tl.rangeIncludingLineBreak.end.character;
}

function buildMFChildPath(uri: vscode.Uri) {
    return uri.with({
        path: posix.join(uri.path, MF_DIR, MF_FILE)
    });
}

class FixMFErrors implements vscode.CodeActionProvider {
    provideCodeActions(document: vscode.TextDocument, range: vscode.Range | vscode.Selection, context: vscode.CodeActionContext, token: vscode.CancellationToken): vscode.ProviderResult<(vscode.CodeAction | vscode.Command)[]> {
        const result: vscode.CodeAction[] = [];
        for (const diag of context.diagnostics) {
            switch (diag.code) {
                case FixKind.addNewLine:
                    const addNewline = new vscode.CodeAction("Add newline", vscode.CodeActionKind.QuickFix);
                    addNewline.diagnostics = [diag];
                    addNewline.isPreferred = true;
                    addNewline.edit = new vscode.WorkspaceEdit();
                    const lastLine = document.lineAt(document.lineCount - 1);
                    addNewline.edit.insert(document.uri, lastLine.rangeIncludingLineBreak.end, (document.eol === vscode.EndOfLine.CRLF ? "\r\n" : "\n"));
                    result.push(addNewline);
                    break;
                case FixKind.fixProjectName:
                    const fixedProjectName = new vscode.CodeAction("Fix project-name", vscode.CodeActionKind.QuickFix);
                    fixedProjectName.diagnostics = [diag];
                    fixedProjectName.isPreferred = true;
                    fixedProjectName.edit = new vscode.WorkspaceEdit();
                    fixedProjectName.edit.replace(document.uri, diag.range, calculateProjectName(document.uri));
                    result.push(fixedProjectName);
                    break;
                case FixKind.requireLibrariesTypo:
                    const typo = new vscode.CodeAction("Fix typo", vscode.CodeActionKind.QuickFix);
                    typo.diagnostics = [diag];
                    typo.isPreferred = true;
                    typo.edit = new vscode.WorkspaceEdit();
                    typo.edit.replace(document.uri, diag.range, "Require-Libraries");
                    result.push(typo);
                    break;

            }
        }
        return result;
    }
}

function calculateProjectName(uri: vscode.Uri) {
    const path = posix.dirname(uri.path);
    if (posix.basename(uri.path) !== MF_FILE || posix.basename(path) !== MF_DIR) {
        throw new Error("Cannot calculate project name for <uri.path>");
    }
    return posix.basename(posix.dirname(path));
}
