// The module 'vscode' contains the VS Code extensibility API
// Import the module and reference it with the alias vscode in your code below
import * as vscode from 'vscode';
import * as path from 'path';
import * as net from 'net';
import * as cp from 'child_process';
import * as os from 'os';

import { ErrorAction, LanguageClient, LanguageClientOptions, Message, ServerOptions, StreamInfo, Trace, ErrorHandler, CloseAction, ProtocolRequestType0, integer } from 'vscode-languageclient/node';
import { Server } from 'http';
import { REPL_MODE_SLOPPY } from 'node:repl';

 
const deployMode = false;
const main = 'org.rascalmpl.vscode.lsp.RascalLanguageServer';

let childProcess: cp.ChildProcessWithoutNullStreams;

let developmentPort = 8888;

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
	const serverOptions: ServerOptions = deployMode 
		? buildRascalServerOptions(context)
		: () => connectToRascalLanguageServerSocket(developmentPort) // we assume a server is running in debug mode
			.then((socket) => <StreamInfo> { writer: socket, reader: socket});

	const clientOptions: LanguageClientOptions = {
		documentSelector: [{ scheme: 'file', language: 'rascalmpl' }],
		// progressOnInitialization: true,
	};

	const client = new LanguageClient('rascalmpl', 'Rascal MPL Language Server', serverOptions, clientOptions, true);
		
	context.subscriptions.push(client.start());
	
	registerTerminalCommand(context, client);
}

export function deactivate() {
	if (childProcess) {
		childProcess.kill('SIGKILL');
	}
}

function registerTerminalCommand(context: vscode.ExtensionContext, client:LanguageClient) {
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

		const reply:Promise<IDEServicesConfiguration> = client.sendRequest("rascal/supplyIDEServicesConfiguration");

		reply.then((cfg:IDEServicesConfiguration) => {
			let terminal = vscode.window.createTerminal({
				cwd: path.dirname(uri.fsPath),
				shellPath: getJavaExecutable(),
				shellArgs: [
					'-cp' , buildJVMPath(context), 
					'org.rascalmpl.vscode.lsp.terminal.LSPTerminalREPL',
					'--ideServicesPort ', 
					'' + cfg.port
				],
				name: 'Rascal Terminal',
			});

			context.subscriptions.push(disposable);
			terminal.show(false);
		});
	});
}

function buildJVMPath(context: vscode.ExtensionContext) :string {
	const jars = ['rascal-lsp.jar', 'rascal.jar', 'rascal-core.jar', 'typepal.jar'];
	return jars.map(j => context.asAbsolutePath('./dist/' + j)).join(path.delimiter);
}

function buildRascalServerOptions(context: vscode.ExtensionContext): ServerOptions {
	const classpath = buildJVMPath(context);
	return {
		command: 'java',
		args: ['-Dlog4j2.configurationFactory=org.rascalmpl.vscode.lsp.LogRedirectConfiguration', '-Dlog4j2.level=TRACE', 
			'-Drascal.lsp.deploy=true', '-Drascal.compilerClasspath=' + classpath, 
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

interface RascalTerminalContentLink extends vscode.TerminalLink {
	url: string
	contentType: string
	contentId: string
}