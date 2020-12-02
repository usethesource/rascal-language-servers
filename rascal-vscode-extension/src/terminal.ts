'use strict';

import * as vscode from 'vscode';
import { Terminal } from 'xterm';
import { AttachAddon } from 'xterm-addon-attach';



export function activateTerminal(context: vscode.ExtensionContext) {
	context.subscriptions.push(
		vscode.commands.registerCommand('rascalmpl.createTerminal', () => {
		  // Create and show a new webview
		  const panel = vscode.window.createWebviewPanel(
			'rascalTerminal',
			'Rascal Terminal', 
			vscode.ViewColumn.One, 
			{} 
		  );

		  // TODO: find out which project to run the terminal for
		  var project = 'file:///Users/jurgenv/git/salix';

		  // TODO: start the terminal server and get its port number
		  var port = startServer(context, project);
		  panel.webview.html = getTerminalViewHTML(port);
		})
	  );
}

function startServer(context: vscode.ExtensionContext, project:string) : number {
	// TODO: start the Websocket on the server side and connect a Rascal REPL
	return 8888; // TODO: proper port number
}

function getTerminalViewHTML(port:number) {
	const terminalId = 'rascal-terminal-container';

	return `
<!DOCTYPE html>
<html lang="en">
  <head>
	  <meta charset="UTF-8">
	  <meta name="viewport" content="width=device-width, initial-scale=1.0">
	  <title>Rascal Terminal</title>
	  <script src="node_modules/xterm/dist/xterm.js"></script>
  </head>
  <body>
	  <div width="100%" id="${terminalId}"></div>
	  <script>
        term = new Terminal({
			cursorBlink: true,
			rows: 20,
			cursorStyle: 'block',
			convertEol: true
		});
		// socket = new WebSocket('ws://localhost:' + ${port});
		// attachAddon = new AttachAddon(socket);
		// term.loadAddon(attachAddon);
		term.open(document.getElementById('${terminalId}'));
      </script>
  </body>
</html>`;
}