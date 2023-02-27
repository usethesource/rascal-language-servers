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
import * as net from 'net';
import * as os from 'os';
import * as path from 'path';

import { integer, LanguageClient, LanguageClientOptions, ServerOptions, StreamInfo } from 'vscode-languageclient/node';
import { getJavaExecutable } from '../auto-jvm/JavaLookup';
import { RascalFileSystemProvider } from '../fs/RascalFileSystemProviders';
import { VSCodeUriResolverServer } from '../fs/VSCodeURIResolver';



export async function activateLanguageClient(
    { language, title, jarPath, vfsServer, isParametricServer = false, deployMode = true, devPort = -1, dedicated = false, lspArg = "" } :
        {language: string, title: string, jarPath: string, vfsServer: VSCodeUriResolverServer, isParametricServer: boolean, deployMode: boolean, devPort: integer, dedicated: boolean, lspArg: string | undefined} )
    : Promise<LanguageClient> {
    const serverOptions: ServerOptions = deployMode
        ? await buildRascalServerOptions(jarPath, isParametricServer, dedicated, lspArg)
        : () => connectToRascalLanguageServerSocket(devPort) // we assume a server is running in debug mode
            .then((socket) => <StreamInfo> { writer: socket, reader: socket});

    const clientOptions = <LanguageClientOptions>{
        documentSelector: [{ scheme: '*', language: language }],
    };

    const client = new LanguageClient(language, title, serverOptions, clientOptions, !deployMode);

    await client.start();
    client.sendNotification("rascal/vfs/register", {
        port: vfsServer.port
    });
    client.onNotification("rascal/showContent", (bp:BrowseParameter) => {
        showContentPanel(bp.uri);
    });

    const schemesReply = client.sendRequest<string[]>("rascal/filesystem/schemes");

    schemesReply.then( schemes => {
        vfsServer.ignoreSchemes(schemes);
        new RascalFileSystemProvider(client).registerSchemes(schemes);
    });


    return client;
}

async function showContentPanel(url: string): Promise<void> {
    // dispose of old panel in case it existed
    const allOpenTabs = vscode.window.tabGroups.all.flatMap(tg => tg.tabs);
    const tabsForThisPanel = allOpenTabs.filter(t => t.input instanceof vscode.TabInputWebview && t.label === url);
    await vscode.window.tabGroups.close(tabsForThisPanel);

    const panel = vscode.window.createWebviewPanel(
        "text/html",
        url,
        vscode.ViewColumn.One,
        {
            enableScripts: true,
            portMapping: [
                { webviewPort: 9050, extensionHostPort: 9050},
                { webviewPort: 9051, extensionHostPort: 9051},
                { webviewPort: 9052, extensionHostPort: 9052},
                { webviewPort: 9053, extensionHostPort: 9053},
                { webviewPort: 9054, extensionHostPort: 9054},
                { webviewPort: 9055, extensionHostPort: 9055},
                { webviewPort: 9056, extensionHostPort: 9056},
                { webviewPort: 9057, extensionHostPort: 9057},
                { webviewPort: 9058, extensionHostPort: 9058},
                { webviewPort: 9059, extensionHostPort: 9059},
                { webviewPort: 9060, extensionHostPort: 9060},
                { webviewPort: 9061, extensionHostPort: 9061},
                { webviewPort: 9062, extensionHostPort: 9062},
                { webviewPort: 9063, extensionHostPort: 9063},
                { webviewPort: 9064, extensionHostPort: 9064},
                { webviewPort: 9065, extensionHostPort: 9065},
                { webviewPort: 9066, extensionHostPort: 9066},
                { webviewPort: 9067, extensionHostPort: 9067},
                { webviewPort: 9068, extensionHostPort: 9068},
                { webviewPort: 9069, extensionHostPort: 9069},
                { webviewPort: 9070, extensionHostPort: 9070},
                { webviewPort: 9071, extensionHostPort: 9071},
                { webviewPort: 9072, extensionHostPort: 9072},
                { webviewPort: 9073, extensionHostPort: 9073},
                { webviewPort: 9074, extensionHostPort: 9074},
                { webviewPort: 9075, extensionHostPort: 9075},
                { webviewPort: 9076, extensionHostPort: 9076},
                { webviewPort: 9077, extensionHostPort: 9077},
                { webviewPort: 9078, extensionHostPort: 9078},
                { webviewPort: 9079, extensionHostPort: 9079},
                { webviewPort: 9080, extensionHostPort: 9080},
                { webviewPort: 9081, extensionHostPort: 9081},
                { webviewPort: 9082, extensionHostPort: 9082},
                { webviewPort: 9083, extensionHostPort: 9083},
                { webviewPort: 9084, extensionHostPort: 9084},
                { webviewPort: 9085, extensionHostPort: 9085},
                { webviewPort: 9086, extensionHostPort: 9086},
                { webviewPort: 9087, extensionHostPort: 9087},
                { webviewPort: 9088, extensionHostPort: 9088},
                { webviewPort: 9089, extensionHostPort: 9089},
                { webviewPort: 9090, extensionHostPort: 9090},
                { webviewPort: 9091, extensionHostPort: 9091},
                { webviewPort: 9092, extensionHostPort: 9092},
                { webviewPort: 9093, extensionHostPort: 9093},
                { webviewPort: 9094, extensionHostPort: 9094},
                { webviewPort: 9095, extensionHostPort: 9095},
                { webviewPort: 9096, extensionHostPort: 9096},
                { webviewPort: 9097, extensionHostPort: 9097},
                { webviewPort: 9098, extensionHostPort: 9098},
                { webviewPort: 9099, extensionHostPort: 9099},
                { webviewPort: 9100, extensionHostPort: 9100},
                { webviewPort: 9101, extensionHostPort: 9101},
                { webviewPort: 9102, extensionHostPort: 9102},
                { webviewPort: 9103, extensionHostPort: 9103},
                { webviewPort: 9104, extensionHostPort: 9104},
                { webviewPort: 9105, extensionHostPort: 9105},
                { webviewPort: 9106, extensionHostPort: 9106},
                { webviewPort: 9107, extensionHostPort: 9107},
                { webviewPort: 9108, extensionHostPort: 9108},
                { webviewPort: 9109, extensionHostPort: 9109},
                { webviewPort: 9110, extensionHostPort: 9110},
                { webviewPort: 9111, extensionHostPort: 9111},
                { webviewPort: 9112, extensionHostPort: 9112},
                { webviewPort: 9113, extensionHostPort: 9113},
                { webviewPort: 9114, extensionHostPort: 9114},
                { webviewPort: 9115, extensionHostPort: 9115},
                { webviewPort: 9116, extensionHostPort: 9116},
                { webviewPort: 9117, extensionHostPort: 9117},
                { webviewPort: 9118, extensionHostPort: 9118},
                { webviewPort: 9119, extensionHostPort: 9119},
                { webviewPort: 9120, extensionHostPort: 9120},
                { webviewPort: 9121, extensionHostPort: 9121},
                { webviewPort: 9122, extensionHostPort: 9122},
                { webviewPort: 9123, extensionHostPort: 9123},
                { webviewPort: 9124, extensionHostPort: 9124},
                { webviewPort: 9125, extensionHostPort: 9125},
                { webviewPort: 9126, extensionHostPort: 9126},
                { webviewPort: 9127, extensionHostPort: 9127},
                { webviewPort: 9128, extensionHostPort: 9128},
                { webviewPort: 9129, extensionHostPort: 9129},
                { webviewPort: 9130, extensionHostPort: 9130},
                { webviewPort: 9131, extensionHostPort: 9131},
                { webviewPort: 9132, extensionHostPort: 9132},
                { webviewPort: 9133, extensionHostPort: 9133},
                { webviewPort: 9134, extensionHostPort: 9134},
                { webviewPort: 9135, extensionHostPort: 9135},
                { webviewPort: 9136, extensionHostPort: 9136},
                { webviewPort: 9137, extensionHostPort: 9137},
                { webviewPort: 9138, extensionHostPort: 9138},
                { webviewPort: 9139, extensionHostPort: 9139},
                { webviewPort: 9140, extensionHostPort: 9140},
                { webviewPort: 9141, extensionHostPort: 9141},
                { webviewPort: 9142, extensionHostPort: 9142},
                { webviewPort: 9143, extensionHostPort: 9143},
                { webviewPort: 9144, extensionHostPort: 9144},
                { webviewPort: 9145, extensionHostPort: 9145},
                { webviewPort: 9146, extensionHostPort: 9146},
                { webviewPort: 9147, extensionHostPort: 9147},
                { webviewPort: 9148, extensionHostPort: 9148},
                { webviewPort: 9149, extensionHostPort: 9149},
                { webviewPort: 9150, extensionHostPort: 9150},
                { webviewPort: 9151, extensionHostPort: 9151},
                { webviewPort: 9152, extensionHostPort: 9152},
                { webviewPort: 9153, extensionHostPort: 9153},
                { webviewPort: 9154, extensionHostPort: 9154},
                { webviewPort: 9155, extensionHostPort: 9155},
                { webviewPort: 9156, extensionHostPort: 9156},
                { webviewPort: 9157, extensionHostPort: 9157},
                { webviewPort: 9158, extensionHostPort: 9158},
                { webviewPort: 9159, extensionHostPort: 9159},
                { webviewPort: 9160, extensionHostPort: 9160},
                { webviewPort: 9161, extensionHostPort: 9161},
                { webviewPort: 9162, extensionHostPort: 9162},
                { webviewPort: 9163, extensionHostPort: 9163},
                { webviewPort: 9164, extensionHostPort: 9164},
                { webviewPort: 9165, extensionHostPort: 9165},
                { webviewPort: 9166, extensionHostPort: 9166},
                { webviewPort: 9167, extensionHostPort: 9167},
                { webviewPort: 9168, extensionHostPort: 9168},
                { webviewPort: 9169, extensionHostPort: 9169},
                { webviewPort: 9170, extensionHostPort: 9170},
                { webviewPort: 9171, extensionHostPort: 9171},
                { webviewPort: 9172, extensionHostPort: 9172},
                { webviewPort: 9173, extensionHostPort: 9173},
                { webviewPort: 9174, extensionHostPort: 9174},
                { webviewPort: 9175, extensionHostPort: 9175},
                { webviewPort: 9176, extensionHostPort: 9176},
                { webviewPort: 9177, extensionHostPort: 9177},
                { webviewPort: 9178, extensionHostPort: 9178},
                { webviewPort: 9179, extensionHostPort: 9179},
                { webviewPort: 9180, extensionHostPort: 9180},
                { webviewPort: 9181, extensionHostPort: 9181},
                { webviewPort: 9182, extensionHostPort: 9182},
                { webviewPort: 9183, extensionHostPort: 9183},
                { webviewPort: 9184, extensionHostPort: 9184},
                { webviewPort: 9185, extensionHostPort: 9185},
                { webviewPort: 9186, extensionHostPort: 9186},
                { webviewPort: 9187, extensionHostPort: 9187},
                { webviewPort: 9188, extensionHostPort: 9188},
                { webviewPort: 9189, extensionHostPort: 9189},
                { webviewPort: 9190, extensionHostPort: 9190},
                { webviewPort: 9191, extensionHostPort: 9191},
                { webviewPort: 9192, extensionHostPort: 9192},
                { webviewPort: 9193, extensionHostPort: 9193},
                { webviewPort: 9194, extensionHostPort: 9194},
                { webviewPort: 9195, extensionHostPort: 9195},
                { webviewPort: 9196, extensionHostPort: 9196},
                { webviewPort: 9197, extensionHostPort: 9197},
                { webviewPort: 9198, extensionHostPort: 9198},
                { webviewPort: 9199, extensionHostPort: 9199},
            ]
        }
    );

    loadURLintoPanel(panel, url);
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

async function buildRascalServerOptions(jarPath: string, isParametricServer: boolean, dedicated: boolean, lspArg : string | undefined): Promise<ServerOptions> {
    const classpath = buildCompilerJVMPath(jarPath, isParametricServer);
    const commandArgs = [
        '-Dlog4j2.configurationFactory=org.rascalmpl.vscode.lsp.LogRedirectConfiguration'
        , '-Dlog4j2.level=DEBUG'
        , '-Drascal.fallbackResolver=org.rascalmpl.vscode.lsp.uri.FallbackResolver'
        , '-Drascal.lsp.deploy=true'
        , '-Drascal.compilerClasspath=' + classpath
    ];
    let mainClass: string;
    if (isParametricServer) {
        mainClass = 'org.rascalmpl.vscode.lsp.parametric.ParametricLanguageServer';
        commandArgs.push(calculateDSLMemoryReservation(dedicated));
    }
    else {
        mainClass = 'org.rascalmpl.vscode.lsp.rascal.RascalLanguageServer';
        commandArgs.push(calculateRascalMemoryReservation());
    }
    commandArgs.push('-cp', classpath, mainClass);
    if (isParametricServer && dedicated && lspArg !== undefined) {
        commandArgs.push(lspArg);
    }
    return {
        command: await getJavaExecutable(),
        args: commandArgs
    };
}

function buildCompilerJVMPath(jarPath:string, isParametricServer: boolean) :string {
    const jars = ['rascal-lsp.jar', 'rascal.jar'];
    if (!isParametricServer) {
        jars.push('rascal-core.jar', 'typepal.jar');
    }
    return jars.map(j => path.join(jarPath, j)).join(path.delimiter);
}

function gb(amount: integer) {
    return amount * (1024 * 1024 * 1024);
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

function calculateDSLMemoryReservation(_dedicated: boolean) {
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

function connectToRascalLanguageServerSocket(port: number): Promise<net.Socket> {
    return new Promise((connected, failed) => {
        const maxTries = 20;
        const host = '127.0.0.1';
        let retryDelay = 0;
        const client = new net.Socket();
        let tries = 0;

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
            client.setNoDelay(true);
            client.removeAllListeners();
            return connected(client);
        });

        return retry();
    });
}
