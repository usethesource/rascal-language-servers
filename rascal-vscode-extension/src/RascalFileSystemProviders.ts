/*
 * Copyright (c) 2018-2021, NWO-I CWI and Swat.engineering
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
import { create } from 'node:domain';
import { normalize } from 'node:path';
import * as vscode from 'vscode';
import { TextEncoder } from 'node:util';
import {LanguageClient, LanguageClientOptions, ServerOptions, StreamInfo, integer } from 'vscode-languageclient/node';

export class RascalFileSystemProvider implements vscode.FileSystemProvider {
    readonly client: LanguageClient;
    private readonly _emitter = new vscode.EventEmitter<vscode.FileChangeEvent[]>();
    readonly onDidChangeFile: vscode.Event<vscode.FileChangeEvent[]> = this._emitter.event;
    private readonly protectedSchemes:string[] = ["file", "http", "https", "unknown"];

    /**
     * Registers a single FileSystemProvider for every URI scheme that Rascal supports, except
     * for file, http and https.
     *
     * @param client to use as a server for the file system provider methods
     */
    constructor (client:LanguageClient) {
        this.client = client;

        client.onNotification("rascal/filesystem/onDidChangeFile", (event:vscode.FileChangeEvent) => {
            this._emitter.fire([event]);
        });
    }

    registerSchemes(schemes:string[]):void {
        schemes
            .filter(s => !this.protectedSchemes.includes(s))
            .forEach(s => vscode.workspace.registerFileSystemProvider(s, this));
    }

    watch(uri: vscode.Uri, options: { recursive: boolean; excludes: string[]; }): vscode.Disposable {
        this.client.sendRequest<void>("rascal/filesystem/watch", {
            uri: uri.toString(),
            recursive:options.recursive,
            excludes: options.excludes
        });

        return new vscode.Disposable(() => {
            this.client.sendRequest<void>("rascal/filesystem/unwatch", {
                uri: uri.toString()
            });
        });
    }

    stat(uri: vscode.Uri): vscode.FileStat | Thenable<vscode.FileStat> {
        return this.client.sendRequest<vscode.FileStat>("rascal/filesystem/stat", {uri: uri.toString()});
    }

    readDirectory(uri: vscode.Uri): [string, vscode.FileType][] | Thenable<[string, vscode.FileType][]> {
        return this.client.sendRequest<[string, vscode.FileType][]>("rascal/filesystem/readDirectory", {uri: uri.toString()});
    }

    createDirectory(uri: vscode.Uri): void | Thenable<void> {
        return this.client.sendRequest<void>("rascal/filesystem/createDirectory", {uri: uri.toString()});
    }

    readFile(uri: vscode.Uri): Uint8Array | Thenable<Uint8Array> {
        return this.client.sendRequest<LocationContent>("rascal/filesystem/readFile", {uri: uri.toString()})
            .then(content => content.content)
            .then(str => new TextEncoder().encode(str));
    }

    writeFile(uri: vscode.Uri, content: Uint8Array, options: { create: boolean; overwrite: boolean; }): void | Thenable<void> {
        return this.client.sendRequest<void>("rascal/filesystem/writeFile", {uri: uri.toString(), create:options.create, overwrite:options.overwrite});
    }

    delete(uri: vscode.Uri, options: { recursive: boolean; }): void | Thenable<void> {
        return this.client.sendRequest<void>("rascal/filesystem/delete", {uri: uri.toString(), recursive:options.recursive});
    }

    rename(oldUri: vscode.Uri, newUri: vscode.Uri, options: { overwrite: boolean; }): void | Thenable<void> {
        return this.client.sendRequest<void>("rascal/filesystem/rename", {oldUri: oldUri.toString(), newUri: newUri.toString(), overwrite: options.overwrite});
    }
}

interface LocationContent {
    content: string;
}
