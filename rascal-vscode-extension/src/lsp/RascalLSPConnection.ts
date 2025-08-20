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

    private static readonly timestampKey = "@timestamp";
    private static readonly loglevelKey = "log.level";
    private static readonly messageKey = "message";
    private static readonly threadNameKey = "process.thread.name";
    private static readonly loggerNameKey = "log.logger";

    constructor(timestamp: Date, loglevel: LogLevel, message: string, threadName: string, loggerName: string) {
        this.timestamp = timestamp;
        this.loglevel = loglevel;
        this.message = message;
        this.threadName = threadName;
        this.loggerName = loggerName;
    }

    static parse(str: string): JsonLogMessage {
        const obj = JSON.parse(str);
        const timestamp: Date = new Date(obj[JsonLogMessage.timestampKey]);
        const loglevel: LogLevel = obj[JsonLogMessage.loglevelKey];
        const message: string = obj[JsonLogMessage.messageKey];
        const threadName: string = obj[JsonLogMessage.threadNameKey];
        const loggerName: string = obj[JsonLogMessage.loggerNameKey];

        return new JsonLogMessage(timestamp, loglevel, message, threadName, loggerName);
    }
}

class JsonParserOutputChannel implements vscode.OutputChannel {
    readonly title: string;

    private readonly nested: vscode.LogOutputChannel;

    constructor(name: string) {
        this.nested = vscode.window.createOutputChannel(name, {log: true});
        this.title = name;
    }

    private printLogOutput(loglevel: LogLevel, message: string) {
        switch (loglevel) {
            case LogLevel.fatal: // intentional fall-trough
            case LogLevel.error: {
                this.nested.error(message);
                break;
            }
            case LogLevel.warn: {
                this.nested.warn(message);
                break;
            }
            case LogLevel.info: {
                this.nested.info(message);
                break;
            }
            case LogLevel.debug: {
                this.nested.debug(message);
                break;
            }
            case LogLevel.trace: {
                this.nested.trace(message);
                break;
            }
            default: {
                this.nested.appendLine(`${loglevel} ${message}`);
            }
        }
    }

    private printPayload(payload: string): void {
        for (const line of payload.trim().split("\n")) {
            try {
                this.printJsonPayLoad(line);
            } catch (e) {
                if (e instanceof SyntaxError) {
                    // this was not JSON at all
                    this.printNonJsonPayload(line);
                } else {
                    this.nested.appendLine(`Error while logging ${line}: ${e}`);
                    throw e;
                }
            }
        }
    }

    private printNonJsonPayload(payload: string): void {
        // try to find the log level and message
        const jsDefault = /^[^[]*\[(?<level>\w+) [^\]]*\]\s*(?<message>.*)$/si;
        const log4jDefault = /^\s*[^\s]+\s+(?<thread>\w+)\s+(?<level>\w+)\s+(?<message>.*)$/si;

        for (const pattern of [jsDefault, log4jDefault]) {
            const matches = pattern.exec(payload);
            if (matches?.groups && "level" in matches.groups && "message" in matches.groups) {
                const logLevel = matches.groups["level"].toLocaleUpperCase() as LogLevel;
                let message = matches.groups["message"];
                if ("thread" in matches.groups) {
                    message = `[${matches.groups["thread"]}] ${message}`;
                }
                this.printLogOutput(logLevel, message);
                return;
            }
        }

        // we do not understand the structure of this log message; just print it as-is
        this.nested.appendLine(payload);
    }

    private printJsonPayLoad(payload: string): void {
        const json: JsonLogMessage = JsonLogMessage.parse(payload);
        // no timestamp or log level, since LogOutputChannel functions add those
        const log = `[${json.threadName}] ${json.loggerName} ${json.message}`;
        this.printLogOutput(json.loglevel, log);
    }

    append(payload: string): void {
        this.printPayload(payload);
    }

    appendLine(payload: string): void {
        this.printPayload(payload);
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
