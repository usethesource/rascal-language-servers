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
import path from 'path';
import * as vscode from 'vscode';
import { BaseLanguageClient, ResponseError } from 'vscode-languageclient';
import { CopyRequest, DirectoryListingResponse, FileAttributes, ISourceLocationRequest, LocationContentResponse, RemoveRequest, RenameRequest, WatchRequest, WriteFileRequest } from './VSCodeFileSystemInRascal';
import { RemoteIOError } from './RemoteIOError';

export class RascalFileSystemInVSCode implements vscode.FileSystemProvider {
    readonly client: BaseLanguageClient;
    private readonly _emitter = new vscode.EventEmitter<vscode.FileChangeEvent[]>();
    readonly onDidChangeFile: vscode.Event<vscode.FileChangeEvent[]> = this._emitter.event;
    private readonly protectedSchemes: string[] = ["file", "http", "https", "unknown"];

    /**
     * Registers a single FileSystemProvider for every URI scheme that Rascal supports, except
     * for file, http and https.
     *
     * @param client to use as a server for the file system provider methods
     */
    constructor (client: BaseLanguageClient, private readonly logger: vscode.LogOutputChannel) {
        this.client = client;

        client.onNotification("rascal/vfs/watcher/fileChanged", (event: vscode.FileChangeEvent) => {
            this._emitter.fire([event]);
        });
    }

    // VS Code omits the leading two slashes from URIs if the autority is empty *and* the scheme is not equal to "file"
    // Rascal does not support this style of URIs, so we add the slashes before sending the URI over
    toRascalUri(uri: vscode.Uri | string): string {
        if (typeof(uri) === "string") {
            return uri;
        }
        const uriString = uri.toString();
        if (uri.authority === "" && uri.scheme !== "file") {
            const colon = uri.scheme.length + 1;
            return `${uriString.slice(0, colon)}//${uriString.slice(colon)}`;
        }
        return uriString;
    }

    sendRequest<R>(uri: vscode.Uri | string, method: string): Promise<R>;
    sendRequest<R, A>(uri: vscode.Uri | string, method: string, param: A): Promise<R>;
    sendRequest<R, A>(uri: vscode.Uri | string, method: string, param?: A): Promise<R> {
        return this.client.sendRequest<R>(method, param ?? <ISourceLocationRequest>{ loc: this.toRascalUri(uri) } )
            .catch((r: ResponseError) => {
                throw RemoteIOError.translateResponseError(r, uri, this.logger);
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
                    this.logger.debug(`Rascal VFS registered scheme: ${s}`);
                } catch (error) {
                    if (isUnknownFileSystem(s)) {
                        this.logger.error(`Unable to register scheme: ${s}\n${error}`);
                    }
                    else {
                        this.logger.debug(`Rascal VFS lost the race to register scheme: ${s}, which in most cases is fine`);
                    }
                }
            });
    }

    watch(uri: vscode.Uri, options: { recursive: boolean; excludes: string[]; }): vscode.Disposable {
        this.logger.trace("[RascalFileSystemInVSCode] watch: ", uri);
        this.sendRequest(uri, "rascal/vfs/watcher/watch", <WatchRequest>{
            loc: this.toRascalUri(uri),
            recursive: options.recursive
        });

        return new vscode.Disposable(() => {
            this.sendRequest(uri, "rascal/vfs/watcher/unwatch", {
                loc: this.toRascalUri(uri),
                recursive: options.recursive
            });
        });
    }

    stat(uri: vscode.Uri): vscode.FileStat | Thenable<vscode.FileStat> {
        this.logger.trace("[RascalFileSystemInVSCode] stat: ", uri);
        return this.sendRequest<FileAttributes>(uri, "rascal/vfs/input/stat").then(a =>
            <vscode.FileStat>{
                type: a.isFile ? vscode.FileType.File : vscode.FileType.Directory,
                ctime: a.created,
                mtime: a.lastModified,
                size: a.size,
                permissions: a.isWritable ? undefined : vscode.FilePermission.Readonly
            }
        );
    }

    readDirectory(uri: vscode.Uri): [string, vscode.FileType][] | Thenable<[string, vscode.FileType][]> {
        this.logger.trace("[RascalFileSystemInVSCode] readDirectory: ", uri);
        return this.sendRequest<DirectoryListingResponse>(uri, "rascal/vfs/input/list")
            .then(c => c.entries.map(ft => [ft.name, ft.types.reduce((a, i) => a | i)]));
    }

    createDirectory(uri: vscode.Uri): void | Thenable<void> {
        this.logger.trace("[RascalFileSystemInVSCode] createDirectory: ", uri);
        return this.sendRequest(uri, "rascal/vfs/output/mkDirectory");
    }

    readFile(uri: vscode.Uri): Uint8Array | Thenable<Uint8Array> {
        this.logger.trace("[RascalFileSystemInVSCode] readFile: ", uri);
        return this.sendRequest<LocationContentResponse>(uri, "rascal/vfs/input/readFile")
            .then(r => Buffer.from(r.content, "base64"));
    }

    writeFile(uri: vscode.Uri, content: Uint8Array, options: { create: boolean; overwrite: boolean; }): void | Thenable<void> {
        // The `create` and `overwrite` options are handled on this side
        this.logger.trace("[RascalFileSystemInVSCode] writeFile: ", uri);
        const parentUri = uri.with({ path: path.dirname(uri.path) });
        Promise.all([
            this.sendRequest<FileAttributes>(uri, "rascal/vfs/input/stat"),
            this.sendRequest<FileAttributes>(parentUri, "rascal/vfs/input/stat")]
        ).then(r => {
            const fileStat = r[0];
            const parentStat = r[1];
            if (!fileStat.exists && !options.create) {
                throw vscode.FileSystemError.FileNotFound(`File ${uri} does not exist and \`create\` was not set`);
            }
            if (!parentStat.exists && options.create) {
                throw vscode.FileSystemError.FileNotFound(`Parent of ${uri} does not exist but \`create\` was set`);
            }
            if (fileStat.exists && options.create && !options.overwrite) {
                throw vscode.FileSystemError.FileExists(`File ${uri} exists and \`create\` was set, but \`override\` was not set`);
            }
            return this.sendRequest(uri, "rascal/vfs/output/writeFile", <WriteFileRequest>{
                loc: this.toRascalUri(uri),
                content: Buffer.from(content).toString("base64"),
                append: false
            });
        });
    }

    delete(uri: vscode.Uri, options: { recursive: boolean; }): void | Thenable<void> {
        this.logger.trace("[RascalFileSystemInVSCode] delete: ", uri);
        return this.sendRequest(uri, "rascal/vfs/output/remove", <RemoveRequest>{ loc: this.toRascalUri(uri), recursive: options.recursive });
    }

    rename(oldUri: vscode.Uri, newUri: vscode.Uri, options: { overwrite: boolean; }): void | Thenable<void> {
        this.logger.trace("[RascalFileSystemInVSCode] rename: ", oldUri, newUri);
        return this.sendRequest(oldUri, "rascal/vfs/output/rename", <RenameRequest>{ from: this.toRascalUri(oldUri), to: this.toRascalUri(newUri), overwrite: options.overwrite });
    }

    copy(source: vscode.Uri, target: vscode.Uri, options?: { overwrite?: boolean; }): Thenable<void> {
        this.logger.trace("[RascalFileSystemInVSCode] copy: ", source, target);
        return this.sendRequest(source, "rascal/vfs/output/copy", <CopyRequest>{ from: this.toRascalUri(source), to: this.toRascalUri(target), recursive: true, overwrite: (options && options.overwrite) ?? false });
    }
}

function isUnknownFileSystem(scheme: string): boolean {
    return vscode.workspace.fs.isWritableFileSystem(scheme) === undefined;
}
