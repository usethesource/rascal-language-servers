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
import * as vscode from 'vscode';
import {BaseLanguageClient, ResponseError } from 'vscode-languageclient';

export class RascalFileSystemProvider implements vscode.FileSystemProvider {
    readonly client: BaseLanguageClient;
    private readonly _emitter = new vscode.EventEmitter<vscode.FileChangeEvent[]>();
    readonly onDidChangeFile: vscode.Event<vscode.FileChangeEvent[]> = this._emitter.event;
    private readonly protectedSchemes:string[] = ["file", "http", "https", "unknown"];

    /**
     * Registers a single FileSystemProvider for every URI scheme that Rascal supports, except
     * for file, http and https.
     *
     * @param client to use as a server for the file system provider methods
     */
    constructor (client:BaseLanguageClient) {
        this.client = client;

        client.onNotification("rascal/filesystem/onDidChangeFile", (event:vscode.FileChangeEvent) => {
            this._emitter.fire([event]);
        });
    }

    sendRequest<R>(uri : vscode.Uri, method: string): Promise<R>;
    sendRequest<R, A>(uri : vscode.Uri, method: string, param: A): Promise<R>;
    sendRequest<R, A>(uri : vscode.Uri, method: string, param?: A): Promise<R> {
        return this.client.sendRequest<R>(method, param ?? { uri: uri.toString()} )
            .catch((r: ResponseError) => {
                if (r !== undefined) {
                    this.client.debug("Got response error from the file system: ", r);
                    switch (r.code) {
                        case -1: throw vscode.FileSystemError.FileExists(uri);
                        case -2: throw vscode.FileSystemError.FileIsADirectory(uri);
                        case -3: throw vscode.FileSystemError.FileNotADirectory(uri);
                        case -4: throw vscode.FileSystemError.FileNotFound(uri);
                        case -5: throw vscode.FileSystemError.NoPermissions(uri);
                        case -6: throw vscode.FileSystemError.Unavailable(uri);
                        default: throw new vscode.FileSystemError(uri);
                    }
                }
                throw r;
            });
    }

    /**
     * Attemptes to register all schemes.
     * @param schemes The list of schemes to register for this provider
     */
    tryRegisterSchemes(schemes: string[]) {
        schemes
            .filter(s => !this.protectedSchemes.includes(s))
            // we add support for schemes that look inside a jar
            .concat(schemes
                .filter(s => s !== "jar" && s !== "zip" && s !== "compressed")
                .map(s => "jar+" + s))
            .filter(isUnknownFileSystem)
            .forEach(s => {
                try {
                    vscode.workspace.registerFileSystemProvider(s, this);
                    this.client.debug(`Rascal VFS registered scheme: ${s}`);
                } catch (error) {
                    if (isUnknownFileSystem(s)) {
                        this.client.error(`Unable to register scheme: ${s}\n${error}`);
                    }
                    else {
                        this.client.debug(`Rascal VFS lost the race to register scheme: ${s}, which in most cases is fine`);
                    }
                }
            });
    }

    watch(uri: vscode.Uri, options: { recursive: boolean; excludes: string[]; }): vscode.Disposable {
        this.sendRequest(uri, "rascal/filesystem/watch", <WatchParameters>{
            uri: uri.toString(),
            recursive:options.recursive,
            excludes: options.excludes
        });

        return new vscode.Disposable(() => {
            this.sendRequest(uri, "rascal/filesystem/unwatch", {
                uri: uri.toString()
            });
        });
    }

    stat(uri: vscode.Uri): vscode.FileStat | Thenable<vscode.FileStat> {
        return this.sendRequest(uri, "rascal/filesystem/stat");
    }

    readDirectory(uri: vscode.Uri): [string, vscode.FileType][] | Thenable<[string, vscode.FileType][]> {
        return this.sendRequest<FileWithType[]>(uri, "rascal/filesystem/readDirectory")
            .then(c => c.map(ft => [ft.name, ft.type]));
    }

    createDirectory(uri: vscode.Uri): void | Thenable<void> {
        return this.sendRequest(uri, "rascal/filesystem/createDirectory", {uri: uri.toString()});
    }

    readFile(uri: vscode.Uri): Uint8Array | Thenable<Uint8Array> {
        return this.sendRequest<LocationContent>(uri, "rascal/filesystem/readFile")
            .then(content => content.content)
            .then(str => Buffer.from(str, "base64"));
    }

    writeFile(uri: vscode.Uri, content: Uint8Array, options: { create: boolean; overwrite: boolean; }): void | Thenable<void> {
        return this.sendRequest(uri, "rascal/filesystem/writeFile", {
            uri: uri.toString(),
            create:options.create,
            overwrite:options.overwrite,
            content: Buffer.from(content).toString("base64")
        });
    }

    delete(uri: vscode.Uri, options: { recursive: boolean; }): void | Thenable<void> {
        return this.sendRequest(uri, "rascal/filesystem/delete", {uri: uri.toString(), recursive: options.recursive});
    }

    rename(oldUri: vscode.Uri, newUri: vscode.Uri, options: { overwrite: boolean; }): void | Thenable<void> {
        return this.sendRequest(oldUri, "rascal/filesystem/rename", {oldUri: oldUri.toString(), newUri: newUri.toString(), overwrite: options.overwrite});
    }
}

function isUnknownFileSystem(scheme : string) : boolean {
    return vscode.workspace.fs.isWritableFileSystem(scheme) === undefined;
}
interface LocationContent {
    content: string;
}

interface WatchParameters {
    uri: string;
    recursive: boolean;
    excludes:Array<string>;
}

interface FileWithType {
    name: string;
    type: vscode.FileType
}
