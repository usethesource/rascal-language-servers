/* eslint-disable no-console */
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
import { Disposable } from "vscode";
import * as rpc from 'vscode-jsonrpc/node';
import { JsonRpcServer } from "../util/JsonRpcServer";
import { BooleanResponse, Capabilities, Capability, CapabilityLevel, CopyRequest, DirectoryListingResponse, FileAttributes, ISourceLocation, ISourceLocationChanged, ISourceLocationChangeType, ISourceLocationRequest, LocationContentResponse, NumberResponse, RemoveRequest, RenameRequest, SetLastModifiedRequest, SourceLocationResponse, StringResponse, TimestampResponse, WatchRequest, WriteFileRequest } from './JsonRpcMessages';
import { RemoteIOError } from './RemoteIOError';
import { ILogicalSourceLocationResolver, ISourceLocationCommon, ISourceLocationInput, ISourceLocationOutput, ISourceLocationWatcher } from './URIResolverInterfaces';

/**
 * VS Code implements this and offers it to the rascal-lsp server
 */
interface VSCodeResolverServer extends ISourceLocationCommon, ISourceLocationInput, ISourceLocationOutput, ISourceLocationWatcher, ILogicalSourceLocationResolver { }

/**
 * Rascal side should implement this on the other side of the stream
 */
//interface VSCodeResolverClient extends WatchEventReceiver {}

function connectHandlers(connection: rpc.MessageConnection, handler: VSCodeResolverServer, disposables: Disposable[], logger: vscode.LogOutputChannel) {
    disposables.push(connection.onRequest((request, arg, _token) => {
        const [rascal, vfs, direction, method] = request.split("/");
        if (rascal !== "rascal" || vfs !== "vfs" || !direction || !method) {
            logger.error(`[VSCodeFileSystemInRascal] Incorrect request: [${rascal},${vfs},${direction},${method}]`);
            throw new rpc.ResponseError(RemoteIOError.unknown, `Unknown request ${request}`);
        }
        if (typeof arg !== "object") {
            logger.error(`[VSCodeFileSystemInRascal] Incorrect argument type: ${typeof arg}`);
            throw new rpc.ResponseError(RemoteIOError.notSupported, `Unexpected argument type ${typeof arg}`);
        }
        switch (direction) {
            case "input":
                return handleInputRequest(method, handler, arg);
            case "output":
                return handleOutputRequest(method, handler, arg);
            case "watcher":
                return handleWatchRequest(method, handler, arg);
            case "logical":
                return handleLogicalRequest(method, handler, arg);
            // common ones aren't prefixed:
            case "getCharset":
                return handler.getCharset(arg as ISourceLocationRequest);
            case "serverCapabilities":
                return handler.serverCapabilities();
        }
        logger.error(`[VSCodeFileSystemInRascal] Unexpected interface ${direction}`);
        throw new rpc.ResponseError(RemoteIOError.notSupported, `Unexpected interface ${direction}`);
    }));
}

function handleInputRequest(method: string, handler: ISourceLocationInput, arg: object) {
    if (!(method in handler)) {
        throw new rpc.ResponseError(RemoteIOError.notSupported, `Unexpected method ${method}`);
    }
    return handler[method as keyof ISourceLocationInput](arg as ISourceLocationRequest);
}

function handleOutputRequest(method: string, handler: ISourceLocationOutput, arg: object): Promise<object | void> {
    if (!(method in handler)) {
        throw new rpc.ResponseError(RemoteIOError.notSupported, `Unexpected method ${method}`);
    }
    const actualMethod = method as keyof ISourceLocationOutput;
    // As the functions of ISourceLocationOutput do not share their argument type,
    // this switch is necessary
    switch (actualMethod) {
        case 'writeFile':
            return handler[actualMethod](arg as WriteFileRequest);
        case 'mkDirectory':
        case 'isWritable':
            return handler[actualMethod](arg as ISourceLocationRequest);
        case 'remove':
            return handler[actualMethod](arg as RemoveRequest);
        case 'rename':
            return handler[actualMethod](arg as RenameRequest);
        case 'copy':
            return handler[actualMethod](arg as CopyRequest);
        case 'setLastModified':
            return handler[actualMethod](arg as SetLastModifiedRequest);
    }
}

function handleWatchRequest(method: string, handler: ISourceLocationWatcher, arg: object) {
    if (!(method in handler)) {
        throw new rpc.ResponseError(RemoteIOError.notSupported, `Unexpected method ${method}`);
    }
    return handler[method as keyof ISourceLocationWatcher](arg as WatchRequest);
}

function handleLogicalRequest(method: string, handler: ILogicalSourceLocationResolver, arg: object) {
    if (!(method in handler)) {
        throw new rpc.ResponseError(RemoteIOError.notSupported, `Unexpected method ${method}`);
    }
    return handler[method as keyof ILogicalSourceLocationResolver](arg as ISourceLocationRequest);
}

// client side implementation receiving watch events
export interface WatchEventReceiver {
    emitWatch(event: ISourceLocationChanged): void;
}

function buildWatchReceiver(connection: rpc.MessageConnection, logger: vscode.LogOutputChannel): WatchEventReceiver {
    return {
        emitWatch: (e) => {
            logger.debug("[VSCodeFileSystemInRascal] Watch callback: sourceLocationChanged", e.root, e.type, e.watchId);
            void connection.sendNotification(new rpc.NotificationType1<ISourceLocationChanged>("rascal/vfs/watcher/sourceLocationChanged"), e);
        }
    };
}

export class VSCodeFileSystemInRascal extends JsonRpcServer {
    private rascalNativeSchemes: Set<string> = new Set();
    constructor(debug: boolean, private readonly logger: vscode.LogOutputChannel) {
        super("VFS", debug, connection => new ResolverClient(connection, debug, this.rascalNativeSchemes, this.logger), logger);
    }

    ignoreSchemes(toIgnore: string[]) {
        toIgnore.forEach(v => this.rascalNativeSchemes.add(v));
    }
}

function fullySupported(): Capability {
    return {
        level: CapabilityLevel.full,
        onlyForSchemes: undefined
    };
}

function unsupported(): Capability {
    return {
        level: CapabilityLevel.unsupported,
        onlyForSchemes: undefined
    };
}

class ResolverClient implements VSCodeResolverServer, Disposable  {
    private readonly watchListener: WatchEventReceiver;
    private readonly fs: vscode.FileSystem;
    private disposables: Disposable[] = [];
    constructor(private readonly connection: rpc.MessageConnection, debug: boolean, private readonly rascalNativeSchemes: Set<string>, private readonly logger: vscode.LogOutputChannel){
        this.fs = vscode.workspace.fs;
        if (debug) {
            void connection.trace(rpc.Trace.Verbose, {
                log: (a) => {
                    this.logger.debug("[VSCodeFileSystemInRascal]: " + a);
                }
            });
        }
        this.watchListener = buildWatchReceiver(connection, logger);
        connectHandlers(connection, this, this.disposables, logger);
    }

    async serverCapabilities(): Promise<Capabilities> {
        return {
            getCharset: fullySupported(),
            input: fullySupported(),
            output: fullySupported(),
            watch: fullySupported(),
            logical: unsupported(),
        };
    }

    async asyncCatcher<T>(build: () => Thenable<T>): Promise<T> {
        try {
            return await build();
        }
        catch (e: unknown) {
            throw RemoteIOError.translateFileSystemError(e, this.logger);
        }
    }

    async asyncVoidCatcher(run: (() => Promise<void>) | Thenable<void>): Promise<void> {
        return this.asyncCatcher<void>(() => {
            if (typeof run === "function") {
                return run();
            }
            else {
                return run;
            }
        });
    }

    toUri(loc: ISourceLocation): vscode.Uri {
        const uri = vscode.Uri.parse(loc);
        if (this.isRascalNative(uri)) {
            throw new rpc.ResponseError(RemoteIOError.isRascalNative, `Cannot perform VS Code file system operations on native Rascal locations: ${loc}`);
        }
        return uri;
    }

    async readFile(req: ISourceLocationRequest): Promise<LocationContentResponse> {
        this.logger.debug("[VSCodeFileSystemInRascal] readFile: ", req.loc);
        return this.asyncCatcher(async () => ({
            content: Buffer.from(await this.fs.readFile(this.toUri(req.loc))).toString("base64")
        }));
    }

    isRascalNative(loc: ISourceLocation | vscode.Uri): boolean {
        // Note that `ISourceLocation` === `URI` === `string` !== `vscode.Uri`
        const scheme = typeof(loc) === "string" ? loc.substring(0, loc.indexOf(":")) : loc.scheme;
        return this.rascalNativeSchemes.has(scheme);
    }

    async exists(req: ISourceLocationRequest): Promise<BooleanResponse> {
        this.logger.debug("[VSCodeFileSystemInRascal] exists: ", req.loc);
        try {
            await this.internalStat(req.loc);
            return { value: true };
        }
        catch (_e) {
            return { value: false };
        }
    }

    async stat(req: ISourceLocationRequest): Promise<FileAttributes> {
        return this.asyncCatcher(async () => {
            const fileInfo = await this.internalStat(req.loc);
            return {
                exists: true,
                isFile: (fileInfo.type | vscode.FileType.File) === vscode.FileType.File,
                created: fileInfo.ctime,
                lastModified: fileInfo.mtime,
                isReadable: true,
                isWritable: !fileInfo.permissions || (fileInfo.permissions | vscode.FilePermission.Readonly) === 0,
                size: fileInfo.size,
            };
        });
    }

    private async internalStat(loc: ISourceLocation): Promise<vscode.FileStat> {
        this.logger.debug("[VSCodeFileSystemInRascal] stat: ", loc);
        return this.fs.stat(this.toUri(loc));
    }

    private async numberResult(loc: ISourceLocation, mapper: (s: vscode.FileStat) => number): Promise<NumberResponse> {
        return this.asyncCatcher(async () => ({ value: mapper((await this.internalStat(loc))) }));
    }

    lastModified(req: ISourceLocationRequest): Promise<TimestampResponse> {
        this.logger.debug("[VSCodeFileSystemInRascal] lastModified: ", req.loc);
        return this.numberResult(req.loc, f => f.mtime);
    }

    created(req: ISourceLocationRequest): Promise<TimestampResponse> {
        this.logger.debug("[VSCodeFileSystemInRascal] created: ", req.loc);
        return this.numberResult(req.loc, f => f.ctime);
    }

    size(req: ISourceLocationRequest): Promise<NumberResponse> {
        this.logger.debug("[VSCodeFileSystemInRascal] size: ", req.loc);
        return this.numberResult(req.loc, f => f.size);
    }

    private async boolResultFromStat(loc: ISourceLocation, mapper: (s: vscode.FileStat) => boolean): Promise<BooleanResponse> {
        return this.asyncCatcher(async () => ({ value: mapper(await this.internalStat(loc)) }));
    }

    isDirectory(req: ISourceLocationRequest): Promise<BooleanResponse> {
        this.logger.debug("[VSCodeFileSystemInRascal] isDirectory: ", req.loc);
        return this.boolResultFromStat(req.loc, f => (f.type & vscode.FileType.Directory) !== 0);
    }

    isFile(req: ISourceLocationRequest): Promise<BooleanResponse> {
        this.logger.debug("[VSCodeFileSystemInRascal] isFile: ", req.loc);
        // TODO: figure out how to handle vscode.FileType.Symlink
        return this.boolResultFromStat(req.loc, f => (f.type & vscode.FileType.File) !== 0);
    }

    isReadable(req: ISourceLocationRequest): Promise<BooleanResponse> {
        this.logger.debug("[VSCodeFileSystemInRascal] isReadable: ", req.loc);
        // if we can do a stat, we can read
        return this.boolResultFromStat(req.loc, _ => true);
    }

    async isWritable(req: ISourceLocationRequest): Promise<BooleanResponse> {
        this.logger.debug("[VSCodeFileSystemInRascal] isWritable: ", req.loc);
        const scheme = this.toUri(req.loc).scheme;
        const writable = this.fs.isWritableFileSystem(scheme);
        if (writable === undefined) {
            throw new rpc.ResponseError(RemoteIOError.unsupportedScheme, `Unsupported scheme: ${scheme}`);
        }
        if (!writable) {
            // not a writable file system, so no need to check the uri
            return { value: false };
        }
        return this.boolResultFromStat(req.loc, f => f.permissions === undefined || (f.permissions & vscode.FilePermission.Readonly) === 0);
    }

    async list(req: ISourceLocationRequest): Promise<DirectoryListingResponse> {
        this.logger.debug("[VSCodeFileSystemInRascal] list: ", req.loc);
        return this.asyncCatcher(async () => ({ entries:
            (await this.fs.readDirectory(this.toUri(req.loc))).map(entry => ({ name: entry[0], types: this.decodeFileTypeBitmask(entry[1]) }))
        }));
    }

    decodeFileTypeBitmask(input: number): vscode.FileType[] {
        return input === 0 ? [vscode.FileType.Unknown] : [vscode.FileType.File, vscode.FileType.Directory, vscode.FileType.SymbolicLink].filter(t => t === (t & input));
    }

    async getCharset(req: ISourceLocationRequest): Promise<StringResponse> {
        return { value: (await vscode.workspace.openTextDocument(req.loc)).encoding };
    }

    async writeFile(req: WriteFileRequest): Promise<void> {
        this.logger.debug("[VSCodeFileSystemInRascal] writeFile: ", req.loc);
        const loc = this.toUri(req.loc);
        let prefix: Buffer<ArrayBuffer> = Buffer.of();
        if (req.append && await this.exists(req)) {
            prefix = Buffer.from(await this.fs.readFile(loc));
        }
        return this.asyncVoidCatcher(
            this.fs.writeFile(loc, Buffer.concat([prefix, Buffer.from(req.content, "base64")]))
        );
    }

    async mkDirectory(req: ISourceLocationRequest): Promise<void> {
        this.logger.debug("[VSCodeFileSystemInRascal] mkDirectory: ", req.loc);
        return this.asyncVoidCatcher(this.fs.createDirectory(this.toUri(req.loc)));
    }

    async remove(req: RemoveRequest): Promise<void> {
        this.logger.debug("[VSCodeFileSystemInRascal] remove: ", req.loc);
        return this.asyncVoidCatcher(this.fs.delete(this.toUri(req.loc), { recursive: req.recursive }));
    }

    async rename(req: RenameRequest): Promise<void> {
        this.logger.debug("[VSCodeFileSystemInRascal] rename: ", req.from, req.to);
        return this.asyncVoidCatcher(this.fs.rename(this.toUri(req.from), this.toUri(req.to), { overwrite: req.overwrite }));
    }

    async copy(req: CopyRequest): Promise<void> {
        this.logger.debug("[VSCodeFileSystemInRascal] copy: ", req.from, req.to);
        if (!req.recursive && await this.isDirectory({ loc: req.from })) {
            throw new rpc.ResponseError(RemoteIOError.isADirectory, 'Non-recursive copy requested on a directory', req);
        }
        return this.asyncVoidCatcher(this.fs.copy(this.toUri(req.from), this.toUri(req.to), { overwrite: req.overwrite }));
    }

    async setLastModified(req: SetLastModifiedRequest): Promise<void> {
        throw new rpc.ResponseError(RemoteIOError.notSupported, 'setLastModified is not supported by VS Code', req);
    }

    private readonly activeWatches = new Map<string, WatcherCallbacks>();

    async watch(req: WatchRequest): Promise<void> {
        this.logger.debug("[VSCodeFileSystemInRascal] watch: ", req.loc);
        if (this.activeWatches.has(req.watchId)) {
            throw new rpc.ResponseError(RemoteIOError.watchAlreadyDefined, `Watch already defined: ${req.loc}`);
        }
        const watcher = new WatcherCallbacks(this.toUri(req.loc), req.recursive, this.watchListener, req.watchId, this.logger);
        this.activeWatches.set(req.watchId, watcher);
        this.disposables.push(watcher);
    }

    async unwatch(req: WatchRequest): Promise<void> {
        this.logger.debug("[VSCodeFileSystemInRascal] unwatch: ", req.loc);
        const watcher = this.activeWatches.get(req.watchId);
        if (watcher === undefined) {
            throw new rpc.ResponseError(RemoteIOError.watchNotDefined, `Watch not defined: ${req.loc}`);
        }
        this.activeWatches.delete(req.watchId);
        watcher.dispose();
        const index = this.disposables.indexOf(watcher);
        if (index >= 0) {
            this.disposables.splice(index, 1);
        }
    }

    async resolveLocation(_req: ISourceLocationRequest): Promise<SourceLocationResponse> {
        throw new rpc.ResponseError(RemoteIOError.notSupported, "resolving logical requests is not supported, as VS Code does not have this feature");
    }

    dispose() {
        this.activeWatches.clear();
        this.disposables.forEach(c => c.dispose());
        try {
            this.connection.end();
        } catch (_e: unknown) {
            // ignore errors here, ase we are disposing anyway
        }
    }
}

class WatcherCallbacks implements Disposable {
    private readonly watchId: string;
    private readonly toClear: Disposable[] = [];
    private readonly watchListener: WatchEventReceiver;
    constructor(uri: vscode.Uri, recursive: boolean, watchListener: WatchEventReceiver, watchId: string, private readonly logger: vscode.LogOutputChannel) {
        this.watchId = watchId;
        this.watchListener = watchListener;
        const newWatcher = vscode.workspace.createFileSystemWatcher(
            new vscode.RelativePattern(uri, recursive ? '**/*' : '*')
        );
        this.toClear.push(newWatcher);
        newWatcher.onDidCreate(e => this.sendWatchEvent(e, ISourceLocationChangeType.created), this.toClear);
        newWatcher.onDidChange(e => this.sendWatchEvent(e, ISourceLocationChangeType.modified), this.toClear);
        newWatcher.onDidDelete(e => this.sendWatchEvent(e, ISourceLocationChangeType.deleted), this.toClear);
    }

    private async sendWatchEvent(uri: vscode.Uri, changeType: ISourceLocationChangeType) {
        this.logger.info("sendWatchEvent {}", uri);
        this.watchListener.emitWatch({
            root: uri.toString(),
            type: changeType,
            watchId: this.watchId
        });
    }

    dispose() {
        this.logger.info("WatcherCallbacks disposed");
        this.toClear.forEach(c => c.dispose());
        this.toClear.splice(0);
    }
}
