// The module 'vscode' contains the VS Code extensibility API
// Import the module and reference it with the alias vscode in your code below
import * as vscode from 'vscode';
import * as path from 'path';
import { LanguageClient, LanguageClientOptions, ServerOptions } from 'vscode-languageclient';
import { fileURLToPath } from 'url';

const main: string = 'org.rascalmpl.vscode.lsp.RascalLanguageServer';
const version: string = '1.0.0-SNAPSHOT';
let contentPanels : any[] = [];

// this method is called when your extension is activated
// your extension is activated the very first time the command is executed
export function activate(context: vscode.ExtensionContext) {

	console.log('Rascal MPL extension is active!');

	// Get the java home from the process environment.
	const { JAVA_HOME } = process.env;

	console.log(`Using java from JAVA_HOME: ${JAVA_HOME}`);
	// If java home is available continue.
	if (JAVA_HOME) {
		// Java execution path.
		let executable: string = path.join(JAVA_HOME, 'bin', 'java');

		// path to the launcher.jar rascal-lsp/target/org.rascalmpl.rascal-lsp-1.0-SNAPSHOT.jar
		let classPath = path.join(context.extensionPath, 'dist', 'rascal-lsp-' + version + '.jar');
		const args: string[] = ['-cp', classPath];
		
		console.log('Using classpath: ' + classPath);
		console.log('Using executable' + executable);

		// Set the server options 
		// -- java execution path
		// -- argument to be pass when executing the java command
		let serverOptions: ServerOptions = {
			command: executable,
			args: [...args, main],
			options: {}
		};

		// Options to control the language client
		let clientOptions: LanguageClientOptions = {
			// Register the server for plain text documents
			documentSelector: [{ scheme: 'file', language: 'rascalmpl' }]
		};

		// Create the language client and start the client.
		let disposable = new LanguageClient('rascalmpl', 'Rascal MPL Language Server', serverOptions, clientOptions).start();

		// Disposables to remove on deactivation.
		context.subscriptions.push(disposable);

		console.log('Activating Rascal Terminal...');
		activateTerminal(context, executable);
	}
}

// this method is called when your extension is deactivated
export function deactivate() {}

function activateTerminal(context: vscode.ExtensionContext, executable:string) {
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
			shellPath: executable,
			shellArgs: ['-cp' , context.asAbsolutePath('./dist/rascal-lsp-' + version + '.jar'), 'org.rascalmpl.shell.RascalShell'],
			name: 'Rascal Terminal',
		});

		// Let the link provider start a webview to serve the content in as a side-effect.
		// have to check that there is maximally one view per port.
		vscode.window.registerTerminalLinkProvider({
			provideTerminalLinks: (context, token) => {
				const startIndex = (context.line as string).indexOf('Serving visual content at |');

				if (startIndex === -1) {
					return [];
			  	}

			  	var pattern: RegExp = new RegExp('Serving visual content at |http://localhost:(?<thePort>\d+)/|');
			  	var result = pattern.exec(context.line);

			  	if (result != null) {
					let port = result.groups!.thePort;
					let matchAt = result.index;
					let contentType = 'rascal-content-' + port;

					if (contentPanels.find(p => p.viewType == contentType) == undefined) {
						const panel = vscode.window.createWebviewPanel(
							contentType,
							'Title',
							vscode.ViewColumn.One,
							{}
						);

						contentPanels.push(panel);
					
						panel.webview
						panel.webview.html = `
						<!DOCTYPE html>
						<html lang="en">
						<head>
							<meta charset="UTF-8">
							<meta name="viewport" content="width=device-width, initial-scale=1.0">
							<meta http-equiv="refresh" content="1;http://localhost:9050/">
						</head>
						<body>
						<iframe src="http://localhost:9050/" frameborder="0" sandbox="allow-same-origin allow-pointer-lock" style="display: block; margin: 0px; overflow: hidden; position: absolute; width: 100%; height: 100%; visibility: visible;">
						Loading content...
						</iframe>
						</body>
						</html>`;

						panel.onDidDispose((e) => {
							contentPanels.splice(contentPanels.indexOf(panel), 1);
						});
					}

			  		return [
						{
				  			matchAt,
				  			length: result?.length,
				  			tooltip: 'Content is being served',
				  			data: result.groups!.thePort
						}
					];	
				}	
			  
				return [];
			},
			handleTerminalLink: (link: any) => {
			  vscode.window.showInformationMessage(`Link activated (data = ${link.data})`);
			}
		});
		  
		terminal.show(false);
    	context.subscriptions.push(disposable);
	});
}