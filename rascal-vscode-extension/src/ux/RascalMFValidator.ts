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
import * as vscode from 'vscode';
import {posix} from 'path'; // posix path join is always correct, also on windows


export const MF_FILE = "RASCAL.MF";
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
        } catch (_e) { /* ignoring errors */ }
        for (const d of this.toDispose) {
            try {
                d.dispose();
            } catch (_e) { /* ignoring errors */ }
        }
    }

    private async verifyRascalMF(file: vscode.Uri) {
        try {
            if (!/\/target\/classes\//.test(file.path)) {
                const mfBody = await vscode.workspace.openTextDocument(file);
                const diagnostics: vscode.Diagnostic[] = [];
                checkMissingLastLine(mfBody, diagnostics);
                checkIncorrectProjectName(mfBody, diagnostics);
                checkPickySeparator(mfBody, diagnostics);
                checkDeprecatedFeatures(mfBody, diagnostics);
                this.diagnostics.set(file, diagnostics);
            }
        }
        catch (_error) {
            // ignoring errors
        }
    }
}

enum FixKind {
    addNewLine = 1,
    fixProjectName,
    missingSpaceAfterSeparator,
    removeInvalidCharsProjectName

}

function checkMissingLastLine(mfBody: vscode.TextDocument, diagnostics: vscode.Diagnostic[]) {
    const lastLine = mfBody.lineAt(mfBody.lineCount - 1);
    if (lastLine.text !== "") {
        const diag = new vscode.Diagnostic(lastLine.range,
            `Skipping the last line of ${MF_FILE} completely, unless the file ends with an empty line (no spaces, no comments).`,
            vscode.DiagnosticSeverity.Error);
        diag.code = FixKind.addNewLine;
        diagnostics.push(diag);
    }
}

const INVALID_PROJECT_NAME = /[^a-z0-9-_]/;

function checkIncorrectProjectName(mfBody: vscode.TextDocument, diagnostics: vscode.Diagnostic[]) {
    let hasProjectName = false;
    for (let l = 0; l < mfBody.lineCount; l++) {
        const line = mfBody.lineAt(l);
        const [key, value] = line.text.split(":");
        if (key && value && key.trim() === "Project-Name") {
            hasProjectName = true;
            const prName = value.split("#")[0]!.trim();
            const offset = line.text.indexOf(prName);
            const targetRange = new vscode.Range(
                l, offset,
                l, offset + prName.length
            );
            const expectedName = calculateProjectName(mfBody.uri);
            if (prName !== expectedName) {
                const diag = new vscode.Diagnostic(targetRange,
                    `Can not handle project names that are not equal to the directory name (${expectedName})`, vscode.DiagnosticSeverity.Error);
                diag.code = FixKind.fixProjectName;
                diagnostics.push(diag);
            }
            if (INVALID_PROJECT_NAME.test(prName)) {
                const diag = new vscode.Diagnostic(targetRange,
                    "Can not handle project name (" + prName + ") that is not all lowercase, digits, or dashes, i.e. in " + INVALID_PROJECT_NAME, vscode.DiagnosticSeverity.Error);
                diag.code = FixKind.removeInvalidCharsProjectName;
                diagnostics.push(diag);
            }
        }
    }
    if (!hasProjectName) {
        diagnostics.push(new vscode.Diagnostic(
            new vscode.Range(0,0,0,0), "Can not find a project name; please add a 'Project-Name' property"
        ));
    }
}

function checkPickySeparator(mfBody: vscode.TextDocument, diagnostics: vscode.Diagnostic[]) {
    for (let l = 0; l < mfBody.lineCount; l++) {
        const line = mfBody.lineAt(l);
        const [key, value] = line.text.split(":");
        if (key && value && value.trim() !== "" && !value.startsWith(" ")) {
            const diag = new vscode.Diagnostic(
                new vscode.Range(line.lineNumber, key.length, line.lineNumber, key.length + 2),
                "A space should always follow a :"
            );
            diag.code = FixKind.missingSpaceAfterSeparator;
            diagnostics.push(diag);
        }
    }
}

function checkDeprecatedFeatures(mfBody: vscode.TextDocument, diagnostics: vscode.Diagnostic[]) {
    for (let l = 0; l < mfBody.lineCount; l++) {
        const line = mfBody.lineAt(l);
        const [key, value] = line.text.split(":");
        if (key && value && key === "Require-Libraries" && value.trim() !== "") {
            diagnostics.push(new vscode.Diagnostic(line.range,
                "The 'Require-Libraries' option is not supported anymore. Please make sure your dependencies are listed in the pom.xml of the project and remove this line."
            ));
        }
    }

}

export function buildMFChildPath(uri: vscode.Uri) {
    return vscode.Uri.joinPath(uri, MF_DIR, MF_FILE);
}

class FixMFErrors implements vscode.CodeActionProvider {
    provideCodeActions(document: vscode.TextDocument, _range: vscode.Range | vscode.Selection, context: vscode.CodeActionContext, _token: vscode.CancellationToken): vscode.ProviderResult<(vscode.CodeAction | vscode.Command)[]> {
        const result: vscode.CodeAction[] = [];
        for (const diag of context.diagnostics) {
            switch (diag.code) {
                case FixKind.addNewLine: {
                    const addNewline = new vscode.CodeAction("Add newline", vscode.CodeActionKind.QuickFix);
                    addNewline.diagnostics = [diag];
                    addNewline.isPreferred = true;
                    addNewline.edit = new vscode.WorkspaceEdit();
                    const lastLine = document.lineAt(document.lineCount - 1);
                    addNewline.edit.insert(document.uri, lastLine.rangeIncludingLineBreak.end, (document.eol === vscode.EndOfLine.CRLF ? "\r\n" : "\n"));
                    result.push(addNewline);
                    break;
                }
                case FixKind.fixProjectName: {
                    const fixedProjectName = new vscode.CodeAction("Fix project-name", vscode.CodeActionKind.QuickFix);
                    fixedProjectName.diagnostics = [diag];
                    fixedProjectName.isPreferred = true;
                    fixedProjectName.edit = new vscode.WorkspaceEdit();
                    fixedProjectName.edit.replace(document.uri, diag.range, calculateProjectName(document.uri));
                    result.push(fixedProjectName);
                    break;
                }
                case FixKind.removeInvalidCharsProjectName: {
                    const fixedProjectName = new vscode.CodeAction("Remove invalid chars in project-name", vscode.CodeActionKind.QuickFix);
                    fixedProjectName.diagnostics = [diag];
                    fixedProjectName.isPreferred = true;
                    fixedProjectName.edit = new vscode.WorkspaceEdit();
                    fixedProjectName.edit.replace(document.uri, diag.range, document.getText(diag.range).replace(new RegExp(INVALID_PROJECT_NAME, "g"), "-"));
                    result.push(fixedProjectName);
                    break;
                }
                case FixKind.missingSpaceAfterSeparator: {
                    const typo = new vscode.CodeAction("Add space after :", vscode.CodeActionKind.QuickFix);
                    typo.diagnostics = [diag];
                    typo.isPreferred = true;
                    typo.edit = new vscode.WorkspaceEdit();
                    typo.edit.insert(document.uri, new vscode.Position(diag.range.start.line, diag.range.start.character + 1), " ");
                    result.push(typo);
                    break;
                }
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
