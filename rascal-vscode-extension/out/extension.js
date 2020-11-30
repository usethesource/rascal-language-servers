"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.deactivate = exports.activate = void 0;
const path = require("path");
const vscode_languageclient_1 = require("vscode-languageclient");
const main = 'RascalLanguageServer';
const version = '1.0.0';
// this method is called when your extension is activated
// your extension is activated the very first time the command is executed
function activate(context) {
    console.log('Rascal MPL extension is active!');
    // Get the java home from the process environment.
    const { JAVA_HOME } = process.env;
    console.log(`Using java from JAVA_HOME: ${JAVA_HOME}`);
    // If java home is available continue.
    if (JAVA_HOME) {
        // Java execution path.
        let excecutable = path.join(JAVA_HOME, 'bin', 'java');
        // path to the launcher.jar rascal-lsp/target/org.rascalmpl.rascal-lsp-1.0-SNAPSHOT.jar
        let classPath = path.join(__dirname, '..', 'rascal-lsp', 'target', 'rascal-lsp-' + version + '.jar');
        const args = ['-cp', classPath];
        // Set the server options 
        // -- java execution path
        // -- argument to be pass when executing the java command
        let serverOptions = {
            command: excecutable,
            args: [...args, main],
            options: {}
        };
        // Options to control the language client
        let clientOptions = {
            // Register the server for plain text documents
            documentSelector: [{ scheme: 'file', language: 'rascalmpl' }]
        };
        // Create the language client and start the client.
        let disposable = new vscode_languageclient_1.LanguageClient('rascal-mpl', 'Rascal MPL Language Server', serverOptions, clientOptions).start();
        // Disposables to remove on deactivation.
        context.subscriptions.push(disposable);
    }
}
exports.activate = activate;
// this method is called when your extension is deactivated
function deactivate() { }
exports.deactivate = deactivate;
//# sourceMappingURL=extension.js.map