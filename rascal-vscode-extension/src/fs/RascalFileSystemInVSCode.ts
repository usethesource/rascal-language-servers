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
import { randomUUID } from 'crypto';
import path from 'path';
import * as vscode from 'vscode';
import { BaseLanguageClient, ResponseError } from 'vscode-languageclient';
import { ISourceLocation, ISourceLocationChanged, ISourceLocationRequest, JsonRpcRequest } from './JsonRpcMessages';
import { RemoteIOError } from './RemoteIOError';
import { ISourceLocationInput, ISourceLocationOutput, ISourceLocationWatcher } from './URIResolverInterfaces';

export class RascalFileSystemInVSCode implements vscode.FileSystemProvider {
    private readonly _emitter = new vscode.EventEmitter<vscode.FileChangeEvent[]>();
    readonly onDidChangeFile: vscode.Event<vscode.FileChangeEvent[]> = this._emitter.event;
    private readonly protectedSchemes: string[] = ["file", "http", "https", "unknown"];
    private activeWatches: Map<[uri: vscode.Uri, recursive: boolean], [string, boolean][]> = new Map();
    private readonly inputClient: ISourceLocationInput;
    private readonly outputClient: ISourceLocationOutput;
    private readonly watchClient: ISourceLocationWatcher;

    // Unique identifier for the current file system instance
    private readonly vfsWatchId = randomUUID();

    /**
     * Registers a single FileSystemProvider for every URI scheme that Rascal supports, except
     * for file, http and https.
     *
     * @param client to use as a server for the file system provider methods
     */
    constructor (client: BaseLanguageClient, private readonly logger: vscode.LogOutputChannel) {
        client.onNotification("rascal/vfs/watcher/sourceLocationChanged", (event: ISourceLocationChanged) => {
            logger.debug("[RascalFileSystemInVSCode] sourceLocationChanged", event.root, event.type);
            if (event.watchId.slice(-this.vfsWatchId.length) !== this.vfsWatchId) {
                // Watch id does not match our instance watch id; this notification is not for us
                return;
            }
            RascalFileSystemInVSCode.notifyWatchers(vscode.Uri.parse(event.root), this.translateFileChangeType(event.type.valueOf()), this.activeWatches, this._emitter, "RascalFileSystemInVSCode", this.logger);
        });
        this.inputClient = buildClientProxy(client, "rascal/vfs/input/", logger);
        this.outputClient = buildClientProxy(client, "rascal/vfs/output/", logger);
        this.watchClient = buildClientProxy(client, "rascal/vfs/watcher/", logger);
    }

    private translateFileChangeType(rascalFileChangeType: number): vscode.FileChangeType {
        switch (rascalFileChangeType) {
            case 1: return vscode.FileChangeType.Created;
            case 2: return vscode.FileChangeType.Deleted;
            case 3: return vscode.FileChangeType.Changed;
        }
        throw new Error(`Unexpected FileChangeType ${rascalFileChangeType}`);
    }

    static notifyWatchers(eventUri: vscode.Uri, type: vscode.FileChangeType, activeWatches: Map<[vscode.Uri, boolean], readonly [string, boolean][]>, emitter: vscode.EventEmitter<vscode.FileChangeEvent[]>, logPrefix: string, logger: vscode.LogOutputChannel): void {
        // Iterating over all active watches
        watches: for (const [[uri, recursive], excludes] of activeWatches) {
            if (eventUri.scheme !== uri.scheme || eventUri.authority !== uri.authority) {
                // Scheme or authority does not match
                continue;
            }

            // A non-recursive watch applies to a file, or a directory's direct children
            if (!recursive && uri.path !== eventUri.path && this.ensureTrailingSlash(uri.path) !== this.ensureTrailingSlash(path.dirname(eventUri.path))) {
                // Current watch does not apply to the event uri
                continue;
            }

            // A recursive watch applies to a directory's children at arbitrary depth
            if (recursive && !eventUri.path.startsWith(this.ensureTrailingSlash(uri.path))) {
                // Current watch does not apply to the event uri
                continue;
            }

            // Current watch does apply to the event uri; checking whether it is excluded in this watch
            for (const [exclude, isGlob] of excludes) {
                const isAbsolute = path.isAbsolute(exclude);
                if (isAbsolute && this.excludeMatchesUri(eventUri.path, exclude, isGlob)
                    || !isAbsolute && this.excludeMatchesUri(eventUri.path, path.join(uri.path, exclude), isGlob)) {
                    // Event uri was excluded in current watch
                    continue watches;
                }
            }

            // Current watch applies to the event uri and no exclude matches
            const callbackEvent: vscode.FileChangeEvent = { type: type, uri: eventUri };
            logger.debug(`[${logPrefix}] Emitting watch callback event`, callbackEvent);
            emitter.fire([callbackEvent]);
            break;
        }
    }

    static ensureTrailingSlash(path: string): string {
        return path.endsWith("/") ? path : path + "/";
    }

    static excludeMatchesUri(uri: string, exclude: string, isGlob: boolean) {
        return (isGlob && path.matchesGlob(uri, exclude)) || (!isGlob && exclude === uri);
    }

    // VS Code omits the leading two slashes from URIs if the autority is empty *and* the scheme is not equal to "file"
    // Rascal does not support this style of URIs, so we add the slashes before sending the URI over
    static toRascalUri(uri: vscode.Uri): string {
        const uriString = uri.toString();
        if (uri.authority === "" && uri.scheme !== "file") {
            const colon = uri.scheme.length + 1;
            return `${uriString.slice(0, colon)}//${uriString.slice(colon)}`;
        }
        return uriString;
    }

    static getLocation(arg: vscode.Uri | JsonRpcRequest): ISourceLocation {
        if (arg instanceof vscode.Uri) {
            return RascalFileSystemInVSCode.toRascalUri(arg);
        }
        if ("loc" in arg) {
            return arg.loc as ISourceLocation;
        }
        if ("from" in arg) {
            return arg.from as ISourceLocation;
        }
        if ("root" in arg) {
            return arg.root as ISourceLocation;
        }
        return "unknown";
    }

    private isUnknownFileSystem(scheme: string): boolean {
        return vscode.workspace.fs.isWritableFileSystem(scheme) === undefined;
    }

    /**
     * Attempts to register all schemes.
     * @param schemes The list of schemes to register for this provider
     */
    tryRegisterSchemes(schemes: string[]) {
        schemes
            .filter(s => !this.protectedSchemes.includes(s))
            // we add support for schemes that look inside a jar
            .concat(schemes
                .filter(s => s !== "jar" && s !== "zip" && s !== "compressed")
                .map(s => "jar+" + s))
            .filter(this.isUnknownFileSystem)
            .forEach(s => {
                try {
                    vscode.workspace.registerFileSystemProvider(s, this);
                    this.logger.debug(`Rascal VFS registered scheme: ${s}`);
                } catch (error) {
                    if (this.isUnknownFileSystem(s)) {
                        this.logger.error(`Unable to register scheme: ${s}\n${error}`);
                    } else {
                        this.logger.debug(`Rascal VFS lost the race to register scheme: ${s}, which in most cases is fine`);
                    }
                }
            });
    }

    watch(uri: vscode.Uri, options: { recursive: boolean; excludes: string[]; }): vscode.Disposable {
        this.logger.debug("[RascalFileSystemInVSCode] watch: ", uri);
        const watchKey: [vscode.Uri, boolean] = [uri, options.recursive];
        const watchId = `${uri}-${options.recursive}-${this.vfsWatchId}`;
        void this.watchClient.watch({
            loc: RascalFileSystemInVSCode.toRascalUri(uri),
            recursive: options.recursive,
            watchId: watchId
        }).then(_v => {
            this.activeWatches.set(watchKey, options.excludes.map(e => [e, RascalFileSystemInVSCode.isGlob(e)]));
        }).catch(r => {
            throw RemoteIOError.translateResponseError(r as ResponseError, uri, this.logger);
        });

        return new vscode.Disposable(async () => {
            this.logger.debug("[RascalFileSystemInVSCode] unwatch: ", uri, options.recursive);
            await this.watchClient.unwatch({
                loc: RascalFileSystemInVSCode.toRascalUri(uri),
                recursive: options.recursive,
                watchId: watchId
            });
            this.activeWatches.delete(watchKey);
        });
    }

    static isGlob(exclude: string): boolean {
        return exclude.indexOf("*") + exclude.indexOf("?") + exclude.indexOf("[") + exclude.indexOf("{") !== -4;
    }

    async stat(uri: vscode.Uri): Promise<vscode.FileStat> {
        this.logger.debug("[RascalFileSystemInVSCode] stat: ", uri);
        const stat = await this.inputClient.stat(uriToRequest(uri));
        const result: vscode.FileStat = {
            type: stat.isFile ? vscode.FileType.File : vscode.FileType.Directory,
            ctime: stat.created,
            mtime: stat.lastModified,
            size: stat.size
        };
        if (!stat.isWritable) {
            result.permissions = vscode.FilePermission.Readonly;
        }
        return result;
    }

    async readDirectory(uri: vscode.Uri): Promise<[string, vscode.FileType][]> {
        this.logger.debug("[RascalFileSystemInVSCode] readDirectory: ", uri);
        return (await this.inputClient.list(uriToRequest(uri)))
            .entries.map(ft => [ft.name, ft.types.reduce((a, i) => a | i)]);
    }

    async createDirectory(uri: vscode.Uri): Promise<void> {
        this.logger.debug("[RascalFileSystemInVSCode] createDirectory: ", uri);
        return this.outputClient.mkDirectory(uriToRequest(uri));
    }

    async readFile(uri: vscode.Uri): Promise<Uint8Array<ArrayBufferLike>> {
        this.logger.debug("[RascalFileSystemInVSCode] readFile: ", uri);
        return Buffer.from((await this.inputClient.readFile(uriToRequest(uri))).content, "base64");
    }

    async writeFile(uri: vscode.Uri, content: Uint8Array, options: { create: boolean; overwrite: boolean; }): Promise<void> {
        // The `create` and `overwrite` options are handled on this side, as this function should comply with the requirements
        // as specified by vscode.FileSystemProvider.writeFile; ISourceLocationOutput does not support `create` and `overwrite`.
        this.logger.debug("[RascalFileSystemInVSCode] writeFile: ", uri);
        const parentUri = uri.with({ path: path.dirname(uri.path) });
        const [fileStat, parentStat] = await Promise.all([
            this.inputClient.stat(uriToRequest(uri)),
            this.inputClient.stat(uriToRequest(parentUri))
        ]);
        if (!fileStat.exists && !options.create) {
            throw vscode.FileSystemError.FileNotFound(`File ${uri} does not exist and \`create\` was not set`);
        }
        if (!parentStat.exists && options.create) {
            throw vscode.FileSystemError.FileNotFound(`Parent of ${uri} does not exist but \`create\` was set`);
        }
        if (fileStat.exists && options.create && !options.overwrite) {
            throw vscode.FileSystemError.FileExists(`File ${uri} exists and \`create\` was set, but \`override\` was not set`);
        }
        return this.outputClient.writeFile({
            loc: RascalFileSystemInVSCode.toRascalUri(uri),
            content: Buffer.from(content).toString("base64"),
            append: false
        });
    }

    async delete(uri: vscode.Uri, options: { recursive: boolean; }): Promise<void> {
        this.logger.debug("[RascalFileSystemInVSCode] delete: ", uri);
        return this.outputClient.remove({
            loc: RascalFileSystemInVSCode.toRascalUri(uri),
            recursive: options.recursive
        });
    }

    async rename(oldUri: vscode.Uri, newUri: vscode.Uri, options: { overwrite: boolean; }): Promise<void> {
        this.logger.debug("[RascalFileSystemInVSCode] rename: ", oldUri, newUri);
        return this.outputClient.rename({
            from: RascalFileSystemInVSCode.toRascalUri(oldUri),
            to: RascalFileSystemInVSCode.toRascalUri(newUri),
            overwrite: options.overwrite
        });
    }

    async copy(source: vscode.Uri, target: vscode.Uri, options?: { overwrite?: boolean; }): Promise<void> {
        this.logger.debug("[RascalFileSystemInVSCode] copy: ", source, target);
        return this.outputClient.copy({
            from: RascalFileSystemInVSCode.toRascalUri(source),
            to: RascalFileSystemInVSCode.toRascalUri(target),
            recursive: true,
            overwrite: options?.overwrite ?? false
        });
    }
}

function buildClientProxy<T extends ISourceLocationInput | ISourceLocationOutput | ISourceLocationWatcher>(client: BaseLanguageClient, methodPrefix: string, logger: vscode.LogOutputChannel): T {
    return new Proxy({} as T, {
        get(_ignored, fieldName: string, _self) {
            return async function(arg: JsonRpcRequest) {
                try {
                    return await client.sendRequest(methodPrefix + fieldName, arg);
                } catch (r) {
                    throw RemoteIOError.translateResponseError(r as ResponseError, RascalFileSystemInVSCode.getLocation(arg), logger);
                }
            };
        }
    });
}

function uriToRequest(uri: vscode.Uri): ISourceLocationRequest {
    return { loc: RascalFileSystemInVSCode.toRascalUri(uri) };
}

