// The module 'vscode' contains the VS Code extensibility API
// Import the module and reference it with the alias vscode in your code below
import * as vscode from 'vscode';
import * as path from 'path';
import { LanguageClient, LanguageClientOptions, ServerOptions } from 'vscode-languageclient';
import { fileURLToPath } from 'url';
import { activateTerminal } from './content-viewer';

const main: string = 'org.rascalmpl.vscode.lsp.RascalLanguageServer';
const version: string = '1.0.0-SNAPSHOT';

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

		terminal.sendText('1 + 1');
		terminal.show();

		return 'ok';
    });

    context.subscriptions.push(disposable);
}