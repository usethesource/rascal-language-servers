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
import * as os from 'os';
import * as path from 'path';
import * as vscode from 'vscode';

import { integer } from 'vscode-languageclient/node';
import { checkForJVMUpdate, getJavaExecutable } from './auto-jvm/JavaLookup';
import { RascalLanguageServer } from './lsp/RascalLanguageServer';
import { LanguageParameter, ParameterizedLanguageServer } from './lsp/ParameterizedLanguageServer';
import { RascalTerminalLinkProvider } from './RascalTerminalLinkProvider';
import { VSCodeUriResolverServer } from './fs/VSCodeURIResolver';
import { FileType } from 'vscode';

export class RascalExtension implements vscode.Disposable {
    private readonly vfsServer: VSCodeUriResolverServer;
    private readonly dsls:ParameterizedLanguageServer;
    private readonly rascal: RascalLanguageServer;


    constructor(private readonly context: vscode.ExtensionContext, private readonly jarRootPath: string, private readonly icon: vscode.Uri, private readonly isDeploy = true) {
        this.vfsServer = new VSCodeUriResolverServer(!isDeploy);

        this.dsls = new ParameterizedLanguageServer(context, this.vfsServer, jarRootPath, isDeploy);
        this.rascal = new RascalLanguageServer(context, this.vfsServer, jarRootPath, this.dsls, isDeploy);

        this.registerTerminalCommand();
        this.registerMainRun();
        this.registerImportModule();
        checkForJVMUpdate();

        vscode.window.registerTerminalLinkProvider(new RascalTerminalLinkProvider(this.rascal.rascalClient));
    }

    dispose() {
        this.vfsServer.dispose();
        this.dsls.dispose();
        this.rascal.dispose();
    }

    externalLanguageRegistry() {
        return {
            registerLanguage: (lang:LanguageParameter) => this.dsls.registerLanguage(lang),
            unregisterLanguage: (lang:LanguageParameter) => this.dsls.unregisterLanguage(lang),
            getRascalExtensionDeploymode: () => this.isDeploy,
        };
    }

    private registerTerminalCommand() {
        this.context.subscriptions.push(
            vscode.commands.registerCommand("rascalmpl.createTerminal", () => {
                this.startTerminal(vscode.window.activeTextEditor?.document.uri);
            })
        );
    }

    private registerMainRun() {
        this.context.subscriptions.push(
            vscode.commands.registerTextEditorCommand("rascalmpl.runMain", (text, _edit, moduleName) => {
                if (!text.document.uri || !moduleName) {
                    return;
                }
                this.startTerminal(text.document.uri, "--loadModule", moduleName, "--runModule");
            })
        );
    }


    private registerImportModule() {
        this.context.subscriptions.push(
            vscode.commands.registerTextEditorCommand("rascalmpl.importModule", (text, _edit, moduleName) => {
                if (!text.document.uri || !moduleName) {
                    return;
                }
                this.startTerminal(text.document.uri, "--loadModule", moduleName);
            })
        );
    }

    private async startTerminal(uri: vscode.Uri | undefined, ...extraArgs: string[]) {
        try {
            await vscode.window.withProgress({
                location: vscode.ProgressLocation.Notification,
                cancellable: false,
                title: "Rascal terminal"
            }, async (progress) => {
                progress.report({message: "Starting rascal-lsp"});
                const rascal = await this.rascal.rascalClient;
                console.log(`Starting Rascal REPL: on ${uri} and with args: ${extraArgs}`);
                if (uri && !uri.path.endsWith(".rsc")) {
                    // do not try to figure out a rascal project path when the focus is not a rascal file
                    uri = undefined;
                }
                progress.report({increment: 5, message: "Checking basic project setup"});
                if (uri) {
                    const [error, detail] = await this.verifyProjectSetup(uri);
                    if (error !== '') {
                        await this.reportTerminalStartError(error, detail);
                        return;
                    }
                }
                progress.report({increment: 20, message: "Requesting IDE configuration"});
                const serverConfig = await rascal.sendRequest<IDEServicesConfiguration>("rascal/supplyIDEServicesConfiguration");
                progress.report({increment: 25, message: "Calculating project class path"});
                const compilationPath = await rascal.sendRequest<string[]>("rascal/supplyProjectCompilationClasspath", { uri: uri?.toString() });
                progress.report({increment: 25, message: "Creating terminal"});
                const projectRoot = uri ? vscode.workspace.getWorkspaceFolder(uri) : undefined;
                const terminal = vscode.window.createTerminal({
                    iconPath: this.icon,
                    shellPath: await getJavaExecutable(),
                    shellArgs: this.buildShellArgs(compilationPath, serverConfig, ...extraArgs),
                    isTransient: false, // right now we don't support transient terminals yet
                    name: `Rascal Terminal (${projectRoot?.name ?? "no project"})`,
                });

                terminal.show(false);
                progress.report({increment: 25, message: "Finished creating terminal"});
            });
        } catch (err) {
            await this.reportTerminalStartError("Failed to start the Rascal REPL, check Rascal Output Window", "" + err);
        }
    }

    private reportTerminalStartError(msg: string, detail: string = "", modal = true) {
        return vscode.window.showErrorMessage(msg, {detail : detail, modal: modal});
    }

    async fileExists(f: vscode.Uri) {
        try {
            return ((await vscode.workspace.fs.stat(f)).type & FileType.File) !== 0;
        } catch (_ignored) {
            return false;
        }
    }

    async verifyProjectSetup(uri: vscode.Uri): Promise<[string, string]>  {
        const projectFolder = vscode.workspace.getWorkspaceFolder(uri);
        if (!projectFolder) {
            return [`The file: ${uri.path} is not located inside of a Workspace folder, the REPL cannot be correctly configured`, ''];
        }
        const requiredFiles : [string, vscode.Uri][] = [
            ["RASCAL.MF", vscode.Uri.joinPath(projectFolder.uri, "META-INF", "RASCAL.MF")],
            ["pom.xml", vscode.Uri.joinPath(projectFolder.uri, "pom.xml")],
        ];
        for (const [name, path] of requiredFiles) {
            if (!(await this.fileExists(path))) {
                return [`The ${name} file is missing for the "${projectFolder.name}" project, please create a valid Rascal Project as described in the documentation`, `Missing file: ${path}`];
            }
        }
        return ['',''];
    }

    private buildShellArgs(classPath: string[], ide: IDEServicesConfiguration, ...extraArgs: string[]) {
        const shellArgs = [
                calculateRascalREPLMemory()
        ];
        const replStackSize = calculateRascalREPLStackSize();
        if (replStackSize.length !== 0) {
            shellArgs.push(replStackSize);
        }
        shellArgs.push(
            '-cp'
            , this.buildTerminalJVMPath() + (classPath.length > 0 ? (path.delimiter + classPath.join(path.delimiter)) : ''),
        );
        if (!this.isDeploy) {
            // for development mode we always start the terminal with debuging ready to go
            shellArgs.push(
                '-Xdebug'
                , '-Xrunjdwp:transport=dt_socket,address=9001,server=y,suspend=n'
            );
        }
        shellArgs.push();
        shellArgs.push(
            '-Dfile.encoding=UTF8'
            , '-Drascal.fallbackResolver=org.rascalmpl.vscode.lsp.uri.FallbackResolver'
            , 'org.rascalmpl.vscode.lsp.terminal.LSPTerminalREPL'
            , '--ideServicesPort'
            , '' + ide.port
            , '--vfsPort'
            , '' + this.vfsServer.port
        );
        return shellArgs.concat(extraArgs || []);
    }
    private buildTerminalJVMPath() :string {
        const jars = ['rascal-lsp.jar', 'rascal.jar'];
        return jars.map(j => path.join(this.jarRootPath, j)).join(path.delimiter);
    }

}

interface IDEServicesConfiguration {
    port:integer;
}



function gb(amount: integer) {
    return amount * (1024 * 1024 * 1024);
}

function calculateRascalREPLMemory() {
    const config = vscode.workspace.getConfiguration();
    if (config.has('rascal.interpreter.maxHeapSize')) {
        const maxHeapSize = config.get('rascal.interpreter.maxHeapSize');
        if (maxHeapSize !== null) {
            return `-Xmx${maxHeapSize}M`;
        }
    }

    if (os.totalmem() >= gb(32)) {
        return "-Xmx9000M";
    }
    if (os.totalmem() >= gb(16)) {
        return "-Xmx4800M";

    }
    if (os.totalmem() >= gb(8)) {
        return "-Xmx2400M";
    }
    return "-Xmx800M";
}

function calculateRascalREPLStackSize() {
    const config = vscode.workspace.getConfiguration();
    if (config.has('rascal.interpreter.stackSize')) {
        const stackSize = config.get('rascal.interpreter.stackSize');
        if (stackSize !== null) {
            return `-Xss${stackSize}M`;
        }
    }

    return "";
}
