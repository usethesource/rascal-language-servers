// The module 'vscode' contains the VS Code extensibility API
// Import the module and reference it with the alias vscode in your code below
import * as vscode from 'vscode';
import * as path from 'path';
import * as net from 'net';
import * as cp from 'child_process';
import * as os from 'os';

import { LanguageClient, LanguageClientOptions, ServerOptions, StreamInfo, Trace } from 'vscode-languageclient';
import { fileURLToPath } from 'url';
import { cpuUsage } from 'process';

let findFreePort = require('find-port-free-sync');
 
const deployMode = true;
const main: string = 'org.rascalmpl.vscode.lsp.RascalLanguageServer';
const version: string = '1.0.0-SNAPSHOT';

let developmentPort = 8888;

let contentPanels : any[] = [];

export function getRascalExtensionDeploymode() : boolean {
	return deployMode;
}

export function activate(context: vscode.ExtensionContext) {
	const serverOptions: ServerOptions = deployMode 
		? () => findFreeServerPort()
		    .then((port) => startRascalLanguageServerProcess(port, context.extensionPath))
			.then((port) => connectToRascalLanguageServerSocket(port))
			.then((socket) => <StreamInfo> {writer: socket, reader: socket})
		: () => connectToRascalLanguageServerSocket(developmentPort) // we assume a server is running in debug mode
			.then((socket) => <StreamInfo> { writer: socket, reader: socket});

	const clientOptions: LanguageClientOptions = {
		documentSelector: [{ scheme: 'file', language: 'rascalmpl' }]
	};

	const client = new LanguageClient('rascalmpl', 'Rascal MPL Language Server', serverOptions, clientOptions);
		
	client.trace = Trace.Verbose;

	context.subscriptions.push(client.start());

	activateTerminal(context);
}

// this method is called when your extension is deactivated
export function deactivate() {}

function activateTerminal(context: vscode.ExtensionContext) {
	let disposable = vscode.commands.registerCommand('rascalmpl.createTerminal', () => {
        let editor = vscode.window.activeTextEditor;
		if (!editor) {
			return;
		}

		let document = editor.document;
		if (!document) {
			return;
		}

		let uri = document.uri;
		if (!uri) {
			return;
		}

		let terminal = vscode.window.createTerminal({
			cwd: path.dirname(uri.fsPath),
			shellPath: getJavaExecutable(),
			shellArgs: ['-cp' , context.asAbsolutePath('./dist/rascal-lsp-' + version + '.jar'), '-Drascal.useSystemBrowser=false','org.rascalmpl.shell.RascalShell'],
			name: 'Rascal Terminal',
		});

		registerContentViewSupport();
		context.subscriptions.push(disposable);
		terminal.show(false);
	});
}

function registerContentViewSupport() {
	vscode.window.registerTerminalLinkProvider({
		provideTerminalLinks: (context, token) => {
			var pattern: RegExp = new RegExp('Serving \'(?<theTitle>[^\']+)\' at \\|http://localhost:(?<thePort>[0-9]+)/\\|');
			var result:RegExpExecArray = pattern.exec(context.line)!;

			if (result !== null) {
				let port = result.groups!.thePort;
				let matchAt = result.index;
				let title = result.groups!.theTitle;

				return [
					{
						startIndex: matchAt,
						length: result?.input.length,
						tooltip: 'Click to view ' + title,
						url: 'http://localhost:' + port + '/',
						contentType: 'text/html',
						contentId: title
					}
				];
			}	
		  
			return [];
		},
		handleTerminalLink: (link: vscode.TerminalLink) => {
			let theLink = (link as RascalTerminalContentLink);
			let contentType = theLink.contentType;
			let url = theLink.url;
			let oldPanel:vscode.WebviewPanel = contentPanels.find(p => (p as vscode.WebviewPanel).title === theLink.contentId);

			if (oldPanel === undefined) {
				const panel = vscode.window.createWebviewPanel(
					theLink.contentType,
					theLink.contentId,
					vscode.ViewColumn.One,
					{
						enableScripts: true,
					}
				);

				contentPanels.push(panel);
				panel.webview.html = `
				<!DOCTYPE html>
				<html lang="en">
				<head>
					<meta charset="UTF-8">
					<meta name="viewport" content="width=device-width, initial-scale=1.0">
				</head>
				<body>
				<iframe id="iframe-rascal-content" src="${url}" frameborder="0" sandbox="allow-scripts allow-forms allow-same-origin allow-pointer-lock allow-downloads allow-top-navigation" style="display: block; margin: 0px; overflow: hidden; position: absolute; width: 100%; height: 100%; visibility: visible;">
				Loading ${theLink.contentId}...
				</iframe>
				</body>
				</html>`;

				panel.onDidDispose((e) => {
					contentPanels.splice(contentPanels.indexOf(panel), 1);
				});
			} else {
				oldPanel.reveal(vscode.ViewColumn.One, false);
			}
		}
	});
}

function findFreeServerPort() : Thenable<number> {
	return new Promise((started, failed) => {
		try {
			let port = findFreePort({start: 8888, end: 8999, num: 1, ip: '127.0.0.1'});
			started(port);
		}
		catch (e) {
			failed(e);
		}
	});
}

function startRascalLanguageServerProcess(portNumber:number, extensionPath: string): Thenable<number> {
	return new Promise((started, failed) => {
		const classPath = path.join(extensionPath, 'dist', 'rascal-lsp-' + version + '.jar');
		const args: string[] = ['-cp', classPath, 'org.rascalmpl.vscode.lsp.RascalLanguageServer', '-port', '' + portNumber];		

		try {
			cp.spawn(getJavaExecutable(), args);
			return started(portNumber);
		}
		catch(e) {
			return failed(e);
		}
	});
}

function getJavaExecutable():string {
	const { JAVA_HOME } = process.env;	
	
	const name = os.platform() === 'win32' ? 'java.exe' : 'java';
	return JAVA_HOME ? path.join(JAVA_HOME, 'bin', name) : name;
}


function connectToRascalLanguageServerSocket(port: number): Thenable<net.Socket> {
    return new Promise((connected, failed) => {
		const maxTries = 10;
		const host = '127.0.0.1';
		const retryDelay = 10000;
		const client = new net.Socket();
		
		var tries = 0;
		
        function retry(err?: Error) : net.Socket | void {
            if (tries <= maxTries) {
                tries++;
				return client.connect(port, host);
            }
            else {
                return failed("Connection retries exceeded" + (err ? (": " + err.message) : ""));
            }
		}
		
        client.setTimeout(retryDelay);
        client.on('timeout', retry);
        client.on('error', retry);
        client.once('connect', () => {
            // client.setTimeout(0); 
            // client.removeAllListeners(); 
            return connected(client);
		});
		
        return retry();
    });
}

interface RascalTerminalContentLink extends vscode.TerminalLink {
	url: string
	contentType: string
	contentId: string
}