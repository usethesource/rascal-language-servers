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
import { isRascalProject, MF_DIR } from './RascalMFValidator';

const POM_XML_FILE = "pom.xml";

export class PomXmlValidator implements vscode.Disposable {
    private readonly diagnostics: vscode.DiagnosticCollection;
    private readonly disposables: vscode.Disposable[] = [];

    constructor (private readonly logger: vscode.LogOutputChannel) {
        logger.info("pom.xml validator starting");
        this.diagnostics = vscode.languages.createDiagnosticCollection("pom.xml diagnostics");

        // new projects should be checked
        vscode.workspace.onDidChangeWorkspaceFolders(async ws => {
            for (const added of ws.added) {
                if (await isRascalProject(added.uri)) {
                    void this.verifyPomXml(added.uri);
                }
            }
            for (const rem of ws.removed) {
                // clear messages of projects that are no longer in the workspace
                this.diagnostics.delete(this.buildPomXmlChildPath(rem.uri));
            }
        }, this, this.disposables);

        // check open folders
        for (const openProject of vscode.workspace.workspaceFolders || []) {
            const pomXmlUri = this.buildPomXmlChildPath(openProject.uri);
            void vscode.workspace.fs.stat(pomXmlUri).then(_s => void this.verifyPomXml(pomXmlUri));
        }

        // watch the file system for changes to pom.xml files
        const watcher = vscode.workspace.createFileSystemWatcher("**/" + POM_XML_FILE, true, false, false);
        watcher.onDidCreate(this.verifyPomXml, this, this.disposables);
        watcher.onDidChange(this.verifyPomXml, this, this.disposables);
        watcher.onDidDelete(e => this.diagnostics.delete(e), this, this.disposables);
        this.disposables.push(watcher);

        this.disposables.push(
            vscode.languages.registerCodeActionsProvider(
                { pattern: "**/" + POM_XML_FILE },
                new FixPomXmlIssues()
            )
        );
    }

    dispose() {
        this.safeDispose(this.diagnostics);
        for (const d of this.disposables) {
            this.safeDispose(d);
        }
    }

    private safeDispose(d: vscode.Disposable): void {
        try {
            d.dispose();
        } catch (_e) { /* ignore errors */ }
    }

    private async verifyPomXml(file: vscode.Uri) {
        try {
            const pomXmlBody = await vscode.workspace.openTextDocument(file);
            const diagnostics : vscode.Diagnostic[] = [];

            try {
                checkRascalDependency(pomXmlBody, diagnostics);
            } finally {
                this.diagnostics.set(file, diagnostics);
            }
        } catch (_error) {
            // Ignore errors
        }
    }

    private buildPomXmlChildPath(uri: vscode.Uri) {
        return vscode.Uri.joinPath(uri, MF_DIR, POM_XML_FILE);
    }
}

enum FixKind {
    noRascalDependency = 1,
    outdatedRascalDependency
}

class FixPomXmlIssues implements vscode.CodeActionProvider {
    provideCodeActions(_document: vscode.TextDocument, _range: vscode.Range | vscode.Selection, context: vscode.CodeActionContext, _token: vscode.CancellationToken): vscode.ProviderResult<(vscode.CodeAction | vscode.Command)[]> {
        const result: vscode.CodeAction[] = [];
        for (const diag of context.diagnostics) {
            switch (diag.code) {
                case FixKind.noRascalDependency: {
                    const addRascalDependency = new vscode.CodeAction("Add Rascal dependency", vscode.CodeActionKind.Empty);
                    addRascalDependency.diagnostics = [diag];
                    addRascalDependency.isPreferred = true;
                    // addRascalDependency.command = ...
                    // vscode.window.showErrorMessage("...");
                    result.push(addRascalDependency);
                    break;
                }
                case FixKind.outdatedRascalDependency: {
                    // TODO
                    break;
                }
            }
        }
        return result;
    }
}
const foo = /<!--([^-]*?|-->)/;

// Pitfall: this regular expression only accepts groupId, artifactId, and version in that particular order, and does not support comments
const dependencyMatcher = /<dependency>\s*<groupId>([^<]*?)<\/groupId>\s*<artifactId>([^<]*?)<\/artifactId>\s*<version>([^<]*?)<\/version>\s*<\/dependency>/g;

function checkRascalDependency(pomXmlBody: vscode.TextDocument, diagnostics: vscode.Diagnostic[]) {
    let match: RegExpExecArray | null;
    const pomXmlText = pomXmlBody.getText();
    while ((match = dependencyMatcher.exec(pomXmlText))) {
        if (match[1] === "org.rascalmpl" && match[2] === "rascal") {
            //TODO: check that version is new enough
        }
        return;
    }
    const diag = new vscode.Diagnostic(
        new vscode.Range(0, 0, 0, 0), "Could not detect a Rascal dependency, please add one to this pom.xml"
    );
    diag.code = FixKind.noRascalDependency;
    diagnostics.push(diag);
}
