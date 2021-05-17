import { create } from 'node:domain';
import { normalize } from 'node:path';
import * as vscode from 'vscode';
import {LanguageClient, LanguageClientOptions, ServerOptions, StreamInfo, integer } from 'vscode-languageclient/node';

class RascalFileSystemProvider implements vscode.FileSystemProvider {
    readonly client: LanguageClient;
    private readonly _emitter = new vscode.EventEmitter<vscode.FileChangeEvent[]>();
    readonly onDidChangeFile: vscode.Event<vscode.FileChangeEvent[]> = this._emitter.event;

    constructor (context: vscode.ExtensionContext, client:LanguageClient) {
        this.client = client;

        client.onNotification("rascal/filesystem/onDidChangeFile", (event:vscode.FileChangeEvent) => {
            this._emitter.fire([event]);
        });

        let protectedSchemes:string[] = ["file", "http", "https"];

        this.client.sendRequest<string[]>("rascal/filesystem/schemes")
            .then(schemes => {
                schemes
                    .filter(s => !protectedSchemes.includes(s))
                    .forEach(s => {
                        vscode.workspace.registerFileSystemProvider(s, this);
                    });
            });
    }

    watch(uri: vscode.Uri, options: { recursive: boolean; excludes: string[]; }): vscode.Disposable {
        this.client.sendRequest<void>("rascal/filesystem/watch", {
            uri: uri,
            recursive:options.recursive,
            excludes: options.excludes
        });

        return new vscode.Disposable(() => {
            this.client.sendRequest<void>("rascal/filesystem/unwatch", {
                uri: uri
            });
        });
    }

    stat(uri: vscode.Uri): vscode.FileStat | Thenable<vscode.FileStat> {
        return this.client.sendRequest<vscode.FileStat>("rascal/filesystem/stat", {uri: uri});
    }

    readDirectory(uri: vscode.Uri): [string, vscode.FileType][] | Thenable<[string, vscode.FileType][]> {
        return this.client.sendRequest<[string, vscode.FileType][]>("rascal/filesystem/readDirectory", {uri: uri});
    }

    createDirectory(uri: vscode.Uri): void | Thenable<void> {
        return this.client.sendRequest<void>("rascal/filesystem/createDirectory", {uri: uri});
    }

    readFile(uri: vscode.Uri): Uint8Array | Thenable<Uint8Array> {
        return this.client.sendRequest<Uint8Array>("rascal/filesystem/readFile", {uri: uri});
    }

    writeFile(uri: vscode.Uri, content: Uint8Array, options: { create: boolean; overwrite: boolean; }): void | Thenable<void> {
        return this.client.sendRequest<void>("rascal/filesystem/writeFile", {uri: uri, create:create, overwrite:overwrite});
    }

    delete(uri: vscode.Uri, options: { recursive: boolean; }): void | Thenable<void> {
        return this.client.sendRequest<void>("rascal/filesystem/delete", {uri: uri, recursive:options.recursive});
    }

    rename(oldUri: vscode.Uri, newUri: vscode.Uri, options: { overwrite: boolean; }): void | Thenable<void> {
        return this.client.sendRequest<void>("rascal/filesystem/rename", {oldUri: oldUri, newUri: newUri});
    }
}
