/*
 * Copyright (c) 2018-2023, NWO-I CWI and Swat.engineering
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
import { RASCAL_LANGUAGE_ID } from '../Identifiers';
import { Diagnostic, DiagnosticSeverity, Position, Range, Uri } from 'vscode';
import { MF_FILE, buildMFChildPath } from './RascalMFValidator';

const FIRST_WORD = new Range(new Position(0,0), new Position(0,0));
const EXPLAIN_PROBLEM = "this reduces Rascal's capabilities for typechecking or executing this module.";

export class RascalProjectValidator implements vscode.Disposable {
    private readonly toDispose: vscode.Disposable[] = [];
    private readonly diagnostics: vscode.DiagnosticCollection;
    private readonly cachedSourcePaths: Map<string, Promise<Uri[]>> = new Map();

    constructor() {
        this.diagnostics = vscode.languages.createDiagnosticCollection("Rascal Project Diagnostics");
        this.toDispose.push(this.diagnostics);
        vscode.workspace.onDidOpenTextDocument(this.validate, this, this.toDispose);
        vscode.workspace.onDidCloseTextDocument(this.closeFile, this, this.toDispose);

        // clear the cached source paths on changes to the rascal.mf file
        const watcher = vscode.workspace.createFileSystemWatcher("**/" + MF_FILE, true, false, false);
        this.toDispose.push(watcher);
        watcher.onDidChange(this.rascalMFChanged, this, this.toDispose);
        watcher.onDidDelete(this.rascalMFChanged, this, this.toDispose);

        this.validateAllOpenEditors();
    }

    async validateAllOpenEditors() {
        for (const tab of vscode.window.tabGroups.all.flatMap(t => t.tabs)) {
            if (tab.input instanceof vscode.TabInputText) {
                try {
                const document = await vscode.workspace.openTextDocument((<vscode.TabInputText>tab.input).uri);
                this.validate(document);
                } catch (e) {
                    console.log("Swallowing: ", e);
                }
            }

        }
    }

    async rascalMFChanged(mfFile : Uri) {
        if (this.cachedSourcePaths.delete(mfFile.toString())) {
            // we had calculated the source paths before
            // lets see re-validate all messages we've reported
            this.validateAllOpenEditors();
        }
    }


    async validate(e: vscode.TextDocument) {
        if (e.languageId !== RASCAL_LANGUAGE_ID) {
            return;
        }
        const folder = vscode.workspace.getWorkspaceFolder(e.uri);
        const messages: Diagnostic[] = [];
        if (!folder) {
            messages.push(new Diagnostic(
                FIRST_WORD,
                "This Rascal file is not part of a project opened in this VS Code window, " + EXPLAIN_PROBLEM,
                vscode.DiagnosticSeverity.Warning
            ));
        }
        else {
            await reportMissingFile(buildPOMChildPath(folder.uri), folder, messages);

            const mf = buildMFChildPath(folder.uri);
            if (!(await reportMissingFile(mf, folder, messages))) {
                try {
                    const sources = await this.getSourcePaths(mf);
                    if (sources.find(s => isChild(s, e.uri)) === undefined) {
                        messages.push(new Diagnostic(
                            FIRST_WORD,
                            `This file is not in the source path of the "${folder.name}" project, please review the RASCAL.MF file. Since ${EXPLAIN_PROBLEM}`,
                            DiagnosticSeverity.Warning
                        ));
                    }
                } catch (ex) {
                    console.log("Swallowing: ", ex);
                }
            }

        }
        this.diagnostics.set(e.uri, messages);
    }

    getSourcePaths(mf: vscode.Uri) : Promise<Uri[]> {
        const key = mf.toString();
        let result = this.cachedSourcePaths.get(key);
        if (result !== undefined) {
            return result;
        }
        result = parseSourcePaths(mf);
        this.cachedSourcePaths.set(key, result);
        return result;
    }

    closeFile(e: vscode.TextDocument) {
        if (e.languageId !== RASCAL_LANGUAGE_ID) {
            return;
        }
        // clear all warnings for close files, to not bother the user more than needed.
        this.diagnostics.delete(e.uri);
    }

    dispose() {
        vscode.Disposable.from(...this.toDispose).dispose();
    }


}

async function fileExists(u : Uri) : Promise<boolean> {
    try {
        const stat = await vscode.workspace.fs.stat(u);
        return (stat.type & vscode.FileType.File) === vscode.FileType.File;
    }
    catch (_) {
        return false;
    }

}

function buildPOMChildPath(u: Uri): Uri {
    return Uri.joinPath(u, "pom.xml");
}

async function reportMissingFile(file: vscode.Uri, project: vscode.WorkspaceFolder, messages: vscode.Diagnostic[]) {
    if (await fileExists(file)) {
        return false;
    }
    messages.push(new Diagnostic(
        FIRST_WORD,
        `${project.name} is missing the ${posix.relative(project.uri.path, file.path)} file, ${EXPLAIN_PROBLEM}`,
        vscode.DiagnosticSeverity.Warning
    ));
    return true;
}

function isChild(parent: Uri, child: Uri): unknown {
    return parent.scheme === child.scheme
        && parent.authority === child.authority
        && child.path.startsWith(parent.path.endsWith('/') ? parent.path : (parent.path + '/'));
}

// TODO: at a later point, merge this with code in the RascalMF validator
async function parseSourcePaths(mf: Uri): Promise<vscode.Uri[]> {
    try {
        const body = new TextDecoder("UTF8").decode(await vscode.workspace.fs.readFile(mf));
        const lines = body.split('\n');
        for (let line of lines) {
            line = removeComments(line);
            const [key, value] = line.split(":");
            if (key && value && key.trim() === "Source") {
                const parent = mf.with({path : posix.dirname(posix.dirname(mf.path))});
                return value.split(',')
                    .map(s => s.trim())
                    .map(s => Uri.joinPath(parent, s));

            }
        }
        return [];
    }
    catch (e) {
        console.log("Could not parse rascal.mf", e);
        return [];
    }
}

function removeComments(entry: string): string {
    return entry.replace(/#.*$/, "");
}

