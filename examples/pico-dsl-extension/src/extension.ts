import * as vscode from 'vscode';
import { ParameterizedLanguageServer, VSCodeUriResolverServer, LanguageParameter } from '@usethesource/rascal-vscode-dsl-runtime';
import { join } from 'path';

export function activate(context: vscode.ExtensionContext) {
	// jar that contains the `pico-lsp` project
	const picoLSPJar = `|jar+file://${context.extensionUri.path}/assets/jars/pico-lsp.jar!|`;
	const language = <LanguageParameter>{
		pathConfig: `pathConfig(srcs=[${picoLSPJar}])`,
		name: "Pico", 
		extension: "pico", 
		mainModule: "lang::pico::LanguageServer", 
		mainFunction: "picoContributions"
	};
	console.log(language);
	// rascal vscode needs an instance of this class, if you register multiple languages, they can share this vfs instance
	const vfs = new VSCodeUriResolverServer(false); 
	// this starts the LSP server and connects it to rascal
	const lsp = new ParameterizedLanguageServer(context, 
		vfs, 
		calcJarPath(context), 
		true, 
		"pico", // vscode language ID
		"Pico", // vscode language Title (visible in the right bottom corner)
		language);
	// adding it to the subscriptions makes sure everything is closed down properly
	context.subscriptions.push(lsp);
}

function calcJarPath(context: vscode.ExtensionContext) {
	return context.asAbsolutePath(join('.', 'dist', 'rascal-lsp'));
}

export function deactivate() {}
