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
// The module 'vscode' contains the VS Code extensibility API
// Import the module and reference it with the alias vscode in your code below
import * as vscode from 'vscode';
import * as path from 'path';
import * as net from 'net';
import * as cp from 'child_process';
import * as os from 'os';

import {LanguageClient, LanguageClientOptions, ServerOptions, StreamInfo, integer } from 'vscode-languageclient/node';
import { RascalFileSystemProvider } from './RascalFileSystemProviders';
import { RascalTerminalLinkProvider } from './RascalTerminalLinkProvider';

const deployMode = (process.env.RASCAL_LSP_DEV || "false") !== "true";
const ALL_LANGUAGES_ID = 'parametric-rascalmpl';
const registeredFileExtensions:Set<string> = new Set();

let childProcess: cp.ChildProcessWithoutNullStreams;

let parametricClient: LanguageClient | undefined = undefined;
let rascalClient: LanguageClient | undefined = undefined;

let rascalActivationHandle: vscode.Disposable | undefined = undefined;

class IDEServicesConfiguration {
    public port:integer;

    constructor (port:integer) {
        this.port = port;
    }
}

export function getRascalExtensionDeploymode() : boolean {
    return deployMode;
}

export function activate(context: vscode.ExtensionContext) {
    //if there is an open Rascal file, activate the Rascal server
    for (const editor of vscode.window.visibleTextEditors) {
        if (editor.document.uri.path.endsWith(".rsc")) {
            rascalClient = activateRascalLanguageClient(context);
            break;
        }
    }
    if (!rascalClient) {
        //Register a handle to activate the Rascal server upon opening a Rascal file
        rascalActivationHandle = vscode.workspace.onDidOpenTextDocument(document => {
            if (!rascalClient && document.uri.path.endsWith(".rsc")) {
                rascalClient = activateRascalLanguageClient(context);
            };
        });
        context.subscriptions.push(rascalActivationHandle);
    }

    registerTerminalCommand(context);
    registerMainRun(context);
    registerImportModule(context);

    context.subscriptions.push(vscode.workspace.onDidOpenTextDocument(e => {
        const ext = path.extname(e.fileName);

        if (ext !== "" && e.languageId !== ALL_LANGUAGES_ID && registeredFileExtensions.has(ext.substring(1))) {
            vscode.languages.setTextDocumentLanguage(e, ALL_LANGUAGES_ID);
        }
    }));

    vscode.window.registerTerminalLinkProvider(new RascalTerminalLinkProvider(() => {
        if (!rascalClient) {
            rascalClient = activateRascalLanguageClient(context);
        }
        return rascalClient;
    }));

    return {registerLanguage};
}

export function registerLanguage(context: vscode.ExtensionContext, lang:LanguageParameter) {
    if (!parametricClient) {
        parametricClient = activateParametricLanguageClient(context);
    }
    // first we load the new language into the parametric server
    parametricClient.onReady().then(() => {
        parametricClient!.sendRequest("rascal/sendRegisterLanguage", lang);
    });
    if (lang.extension && lang.extension !== "") {
        registeredFileExtensions.add(lang.extension);
    }
}

export function activateRascalLanguageClient(context: vscode.ExtensionContext):LanguageClient {
    try {
        return activateLanguageClient(context, 'rascalmpl', 'org.rascalmpl.vscode.lsp.rascal.RascalLanguageServer', 'Rascal MPL Language Server', 8888, false);
    } finally {
        console.log('LSP (Rascal) server started');
        if (rascalActivationHandle) {
            rascalActivationHandle.dispose();
        }
    }
}

export function activateParametricLanguageClient(context: vscode.ExtensionContext) {
    try {
        return activateLanguageClient(context, 'parametric-rascalmpl', 'org.rascalmpl.vscode.lsp.parametric.ParametricLanguageServer', 'Language Parametric Rascal Language Server', 9999, true);
    } finally {
        console.log('LSP (Parametric) server started');
    }
}

export function activateLanguageClient(context: vscode.ExtensionContext, language:string, main:string, title:string, devPort:integer, isParametricServer:boolean) :LanguageClient {
    const serverOptions: ServerOptions = deployMode
        ? buildRascalServerOptions(context, main)
        : () => connectToRascalLanguageServerSocket(devPort) // we assume a server is running in debug mode
            .then((socket) => <StreamInfo> { writer: socket, reader: socket});

    const clientOptions: LanguageClientOptions = {
        documentSelector: [{ scheme: '*', language: language }],
    };

    const client = new LanguageClient(language, title, serverOptions, clientOptions, !deployMode);

    client.onReady().then(() => {
        client.onNotification("rascal/showContent", (bp:BrowseParameter) => {
           showContentPanel(bp.uri);
        });

        if (!isParametricServer) {
            client.onNotification("rascal/receiveRegisterLanguage", (lang:LanguageParameter) => {
                registerLanguage(context, lang);
            });
        }

        let schemesReply:Promise<string[]> = client.sendRequest("rascal/filesystem/schemes");

        schemesReply.then( schemes => {
            new RascalFileSystemProvider(client).registerSchemes(schemes);
        });
    });

    context.subscriptions.push(client.start());

    return client;
}

export function deactivate() {
    if (childProcess) {
        childProcess.kill('SIGKILL');
    }
}

function registerTerminalCommand(context: vscode.ExtensionContext) {
    const command = vscode.commands.registerTextEditorCommand("rascalmpl.createTerminal", (text, edit) => {
        if (!text.document.uri) {
            return;
        }
        startTerminal(text.document.uri, context);
    });
    context.subscriptions.push(command);
}

function registerMainRun(context: vscode.ExtensionContext) {
    const command = vscode.commands.registerTextEditorCommand("rascalmpl.runMain", (text, edit, moduleName) => {
        if (!text.document.uri || !moduleName) {
            return;
        }
        startTerminal(text.document.uri, context, "--loadModule", moduleName, "--runModule");
    });
    context.subscriptions.push(command);
}


function registerImportModule(context: vscode.ExtensionContext) {
    const command = vscode.commands.registerTextEditorCommand("rascalmpl.importModule", (text, edit, moduleName) => {
        if (!text.document.uri || !moduleName) {
            return;
        }
        startTerminal(text.document.uri, context, "--loadModule", moduleName);
    });
    context.subscriptions.push(command);
}

function startTerminal(uri: vscode.Uri, context: vscode.ExtensionContext, ...extraArgs: string[]) {
    if (!rascalClient) {
        rascalClient = activateRascalLanguageClient(context);
    }
    console.log("Starting:" + uri + extraArgs);
    Promise.all([
        rascalClient.sendRequest<IDEServicesConfiguration>("rascal/supplyIDEServicesConfiguration"),
        rascalClient.sendRequest<string[]>("rascal/supplyProjectCompilationClasspath", { uri: uri.toString() })
    ]).then(cfg => {
        let terminal = vscode.window.createTerminal({
            cwd: path.dirname(uri.fsPath),
            shellPath: getJavaExecutable(),
            shellArgs: buildShellArgs(cfg[1], cfg[0], context, ...extraArgs),
            name: 'Rascal Terminal',
        });

        terminal.show(false);
    });
}

function buildShellArgs(classPath: string[], ide: IDEServicesConfiguration, context: vscode.ExtensionContext, ...extraArgs: string[]) {
    const shellArgs = [
            '-cp'
            , buildTerminalJVMPath(context) + (classPath.length > 0 ? (path.delimiter + classPath.join(path.delimiter)) : ''),
    ];
    if (!deployMode) {
        // for development mode we always start the terminal with debuging ready to go
        shellArgs.push(
            '-Xdebug'
            , '-Xrunjdwp:transport=dt_socket,address=9001,server=y,suspend=n'
        );
    }
    shellArgs.push(
        'org.rascalmpl.vscode.lsp.terminal.LSPTerminalREPL'
        , '--ideServicesPort'
        , '' + ide.port
    );
    return shellArgs.concat(extraArgs || []);
}


function gb(amount: integer) {
    return amount * (1024 * 1024);
}

function calculateRascalMemoryReservation() {
    // rascal lsp needs at least 800M but runs better with 2G or even 2.5G (especially the type checker)
    if (os.totalmem() >= gb(32)) {
        return "-Xmx2500M";
    }
    if (os.totalmem() >= gb(16)) {
        return "-Xmx1500M";

    }
    if (os.totalmem() >= gb(8)) {
        return "-Xmx1200M";
    }
    return "-Xmx800M";
}

function calculateDSLMemoryReservation() {
    // this is a hard one, if you register many DSLs, it can grow quite a bit
    // 400MB per language is a reasonable estimate (for average sized languages)
    if (os.totalmem() >= gb(32)) {
        return "-Xmx2400M";
    }
    if (os.totalmem() >= gb(16)) {
        return "-Xmx1600M";
    }
    if (os.totalmem() >= gb(8)) {
        return "-Xmx1200M";
    }
    return "-Xmx800M";

}

function buildCompilerJVMPath(context: vscode.ExtensionContext) :string {
    const jars = ['rascal-lsp.jar', 'rascal.jar', 'rascal-core.jar', 'typepal.jar'];
    return jars.map(j => context.asAbsolutePath(path.join('.', 'assets', 'jars', j))).join(path.delimiter);
}

function buildTerminalJVMPath(context: vscode.ExtensionContext) :string {
    const jars = ['rascal-lsp.jar', 'rascal.jar'];
    return jars.map(j => context.asAbsolutePath(path.join('.', 'assets', 'jars', j))).join(path.delimiter);
}

function buildRascalServerOptions(context: vscode.ExtensionContext, main:string): ServerOptions {
    const classpath = buildCompilerJVMPath(context);
    return {
        command: 'java',
        args: ['-Dlog4j2.configurationFactory=org.rascalmpl.vscode.lsp.LogRedirectConfiguration', '-Dlog4j2.level=DEBUG',
            '-Drascal.lsp.deploy=true', '-Drascal.compilerClasspath=' + classpath,
            main.includes("Parametric") ? calculateDSLMemoryReservation() : calculateRascalMemoryReservation(),
            '-cp', classpath, main],
    };
}

function getJavaExecutable():string {
    const { JAVA_HOME } = process.env;

    const name = os.platform() === 'win32' ? 'java.exe' : 'java';
    return JAVA_HOME ? path.join(JAVA_HOME, 'bin', name) : name;
}

function connectToRascalLanguageServerSocket(port: number): Promise<net.Socket> {
    return new Promise((connected, failed) => {
        const maxTries = 20;
        const host = '127.0.0.1';
        let retryDelay = 0;
        const client = new net.Socket();
        var tries = 0;

        function retry(err?: Error) : net.Socket | void {
            if (tries <= maxTries) {
                setTimeout (() => {
                    tries++;
                    retryDelay = Math.min(2500, retryDelay + 250);
                    client.connect(port, host);
                }, retryDelay);
            }
            else {
                return failed("Connection retries exceeded" + (err ? (": " + err.message) : ""));
            }
        }

        client.setTimeout(1000);
        client.on('timeout', retry);
        client.on('error', retry);
        client.once('connect', () => {
            client.setTimeout(0);
            client.removeAllListeners();
            return connected(client);
        });

        return retry();
    });
}

let contentPanels : vscode.WebviewPanel[] = [];

function showContentPanel(url: string) : void {
    let oldPanel:vscode.WebviewPanel|undefined = contentPanels.find(p => (p as vscode.WebviewPanel).title === url);

    if (oldPanel) {
        oldPanel.dispose();
    }

    const panel = vscode.window.createWebviewPanel(
            "text/html",
            url,
            vscode.ViewColumn.One,
            {
                    enableScripts: true,
            }
    );

    contentPanels.push(panel);
    loadURLintoPanel(panel, url);

    panel.onDidDispose((e) => {
            contentPanels.splice(contentPanels.indexOf(panel), 1);
            // TODO: possibly clean up the server side while we are at it?
    });
}

function loadURLintoPanel(panel:vscode.WebviewPanel, url:string): void {
    panel.webview.html = `
            <!DOCTYPE html>
            <html lang="en">
            <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body>
            <iframe
                id="iframe-rascal-content"
                src="${url}"
                frameborder="0"
                sandbox="allow-scripts allow-forms allow-same-origin allow-pointer-lock allow-downloads allow-top-navigation"
                style="display: block; margin: 0px; overflow: hidden; position: absolute; width: 100%; height: 100%; visibility: visible;"
            >
            Loading ${url}...
            </iframe>
            </body>
            </html>`;
}

interface BrowseParameter {
    uri: string;
    mimetype: string;
    title:string;
}

interface URIParameter {
    uri:string;
}

interface LanguageParameter {
    pathConfig: string 		// rascal pathConfig constructor as a string
    name: string; 			// name of the language
    extension:string; 		// extension for files in this language
    mainModule: string; 	// main module to locate mainFunction in
    mainFunction: string; 	// main function which contributes the language implementation
}

interface LocationContent {
    content: string;
}

interface EditorCommand {
    name: string;
}
