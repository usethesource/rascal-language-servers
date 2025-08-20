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
import * as net from 'net';
import * as os from 'os';
import * as path from 'path';
import * as vscode from 'vscode';

import { integer, LanguageClient, LanguageClientOptions, ServerOptions, StreamInfo } from 'vscode-languageclient/node';
import { getJavaExecutable } from '../auto-jvm/JavaLookup';
import { RascalFileSystemProvider } from '../fs/RascalFileSystemProviders';
import { VSCodeUriResolverServer } from '../fs/VSCodeURIResolver';

enum LogLevel {
    fatal = "FATAL",
    error = "ERROR",
    warn = "WARN",
    info = "INFO",
    debug = "DEBUG",
    trace = "TRACE",
}

class JsonLogMessage {
    readonly timestamp: Date;
    readonly loglevel: LogLevel;
    readonly message: string;
    readonly threadName: string;
    readonly loggerName: string;

    constructor(timestamp: Date, loglevel: LogLevel, message: string, threadName: string, loggerName: string) {
        this.timestamp = timestamp;
        this.loglevel = loglevel;
        this.message = message;
        this.threadName = threadName;
        this.loggerName = loggerName;
    }

    static parse(str: string): JsonLogMessage {
        const obj = JSON.parse(str);
        const timestamp: Date = new Date(obj["@timestamp"]);
        const loglevel: LogLevel = obj["log.level"];
        const message: string = obj["message"];
        const threadName: string = obj["process.thread.name"];
        const loggerName: string = obj["log.logger"];

        if (!(timestamp && loglevel && message && threadName && loggerName)) {
            throw new Error(`Could not parse JSON:\n${str}`);
        }

        return new JsonLogMessage(timestamp, loglevel, message, threadName, loggerName);
    }
}

class JsonParserOutputChannel implements vscode.OutputChannel {
    readonly title: string;

    private readonly nested: vscode.LogOutputChannel;
    private readonly threadBuffers: Map<string, string>;

    // https://logging.apache.org/log4j/2.x/manual/json-template-layout.html#plugin-attr-truncatedStringSuffix
    private static readonly truncationSymbol = "…";
    private static readonly truncationSymbolLength = this.truncationSymbol.length;

    constructor(name: string) {
        this.nested = vscode.window.createOutputChannel(name, {log: true});
        this.title = name;
        this.threadBuffers = new Map();
    }

    private printBufferedMessage(json: JsonLogMessage): void {
        // no timestamp or log level, since LogOutputChannel functions add those
        const message = this.getAndResetBuffer(json.threadName);
        // this.nested.appendLine(`Buffered message: ${message}`);
        const log = `[${json.threadName}] ${json.loggerName} ${message}`;
        switch (json.loglevel) {
            case LogLevel.fatal: // intentional fall-trough
            case LogLevel.error: {
                this.nested.error(log);
                break;
            }
            case LogLevel.warn: {
                this.nested.warn(log);
                break;
            }
            case LogLevel.info: {
                this.nested.info(log);
                break;
            }
            case LogLevel.debug: {
                this.nested.debug(log);
                break;
            }
            case LogLevel.trace: {
                this.nested.trace(log);
                break;
            }
            default: {
                this.nested.appendLine(`[NOT WRAPPED] ${json.loglevel} ${log}`);
            }
        }
    }

    private getAndResetBuffer(threadName: string): string | undefined {
        const buffer = this.threadBuffers.get(threadName);
        this.threadBuffers.set(threadName, "");
        return buffer;
    }

    private appendBuffer(threadName: string, suffix: string): string {
        const curr = this.threadBuffers.get(threadName);
        const appended = curr + suffix;
        this.threadBuffers.set(threadName, appended);
        // this.nested.appendLine(`Buffer of [${threadName}] now has size ${appended.length}`);
        return appended;
    }

    private printJsonPayloads(payload: string): void {
        // this.nested.appendLine(`Payload: ${payload}`);
        const jsons = payload.trim().split("\n");
        // let i = 1;
        for (const json of jsons) {
            // this.nested.appendLine(`Printing JSON ${i++}/${jsons.length}: ${json}`);
            this.printPayload(json);
        }
    }

    private printPayload(payload: string): void {
        try {
            // this.nested.appendLine(`Trying to print as JSON: ${payload}`);
            this.printJsonPayLoad(payload);
        } catch (e) {
            if (e instanceof SyntaxError) {
                // this was not JSON at all... fall back to default behaviour
                this.nested.appendLine(payload);
            } else {
                this.nested.appendLine(`Error while logging ${payload}: ${e}`);
                throw e;
            }
        }
    }

    private cleanMessage(message: string): [string, boolean] {
        if (message.endsWith(JsonParserOutputChannel.truncationSymbol)) {
            return [message.slice(0, -JsonParserOutputChannel.truncationSymbolLength), true];
        }
        return [message, false];
    }

    private printJsonPayLoad(payload: string): void {
        const json: JsonLogMessage = JsonLogMessage.parse(payload);
        if (!this.threadBuffers.has(json.threadName)) {
            this.threadBuffers.set(json.threadName, "");
        }

        const [message, isTruncated] = this.cleanMessage(json.message);
        if (isTruncated) {
            this.appendBuffer(json.threadName, message);
        } else {
            this.appendBuffer(json.threadName, message);
            this.printBufferedMessage(json);
        }
    }

    append(payload: string): void {
        // console.log(`append (${payload.length}): ${payload}`);
        this.printJsonPayloads(payload);
    }

    appendLine(payload: string): void {
        // console.log(`appendLine (${payload.length}): ${payload}`);
        this.printJsonPayloads(payload);
    }

    show(preserveFocus?: unknown): void {
        this.nested.show(preserveFocus as boolean);
    }
    replace(value: string): void {
        this.nested.replace(value);
    }
    clear(): void {
        this.nested.clear();
    }
    hide(): void {
        this.nested.hide();
    }
    dispose(): void {
        this.nested.dispose();
    }
}

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
        outputChannel: new JsonParserOutputChannel(title)
    };

    const client = new LanguageClient(language, title, serverOptions, clientOptions, !deployMode);

    await client.start();
    client.sendNotification("rascal/vfs/register", {
        port: vfsServer.port
    });
    client.onNotification("rascal/showContent", (bp:BrowseParameter) => {
        showContentPanel(bp.uri, bp.title, bp.viewColumn);
    });

    const schemesReply = client.sendRequest<string[]>("rascal/filesystem/schemes");

    schemesReply.then( schemes => {
        vfsServer.ignoreSchemes(schemes);
        new RascalFileSystemProvider(client).tryRegisterSchemes(schemes);
    });

    return client;
}

async function showContentPanel(url: string, title:string, viewColumn:integer): Promise<void> {
    // dispose of old panel in case it existed
    const externalURL = (await vscode.env.asExternalUri(vscode.Uri.parse(url))).toString();
    const allOpenTabs = vscode.window.tabGroups.all.flatMap(tg => tg.tabs);
    const tabsForThisPanel = allOpenTabs.filter(t => t.input instanceof vscode.TabInputWebview && t.label === externalURL);

    await vscode.window.tabGroups.close(tabsForThisPanel);

    const panel = vscode.window.createWebviewPanel(
        "text/html",
        title,
        {
            viewColumn: viewColumn,
            preserveFocus: true /* the next editor should appear in the old column */
        },
        {
            enableScripts: true,
        }
    );

    loadURLintoPanel(panel, externalURL);
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
    viewColumn:integer;
}

async function buildRascalServerOptions(jarPath: string, isParametricServer: boolean, dedicated: boolean, lspArg : string | undefined): Promise<ServerOptions> {
    const classpath = buildCompilerJVMPath(jarPath);
    const commandArgs = [
        '-Dlog4j2.configurationFactory=org.rascalmpl.vscode.lsp.LogJsonConfiguration'
        , '-Dlog4j2.level=DEBUG' // TODO Remove this minimum
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

function buildCompilerJVMPath(jarPath:string) :string {
    return ['rascal-lsp.jar', 'rascal.jar']
        .map(j => path.join(jarPath, j))
        .join(path.delimiter);
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
