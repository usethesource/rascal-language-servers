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
import { URI } from "vscode-languageclient";
import { JsonRpcServer } from "../util/JsonRpcServer";
import { RemoteIOError } from './RemoteIOError';

export declare type ISourceLocation = URI;

/**
 * VS Code implements this and offers it to the rascal-lsp server
 */
interface VSCodeResolverServer extends ISourceLocationInput, ISourceLocationOutput, ISourceLocationWatcher { }

/**
 * Rascal side should implement this on the other side of the stream
 */
//interface VSCodeResolverClient extends WatchEventReceiver {}


// Rascal's interface reduced to a subset we can support
interface ISourceLocationInput {
    readFile(req: ISourceLocationRequest): Promise<LocationContentResponse>;
    exists(req: ISourceLocationRequest): Promise<BooleanResponse>;
    lastModified(req: ISourceLocationRequest): Promise<TimestampResponse>;
    created(req: ISourceLocationRequest): Promise<TimestampResponse>;
    isDirectory(req: ISourceLocationRequest): Promise<BooleanResponse>;
    isFile(req: ISourceLocationRequest): Promise<BooleanResponse>;
    list(req: ISourceLocationRequest): Promise<DirectoryListingResponse>;
    size(req: ISourceLocationRequest): Promise<NumberResponse>;
    fileStat(req: ISourceLocationRequest): Promise<FileAttributes>;
    isReadable(req: ISourceLocationRequest): Promise<BooleanResponse>;
}

function connectInputHandler(connection: rpc.MessageConnection, handler: ISourceLocationInput, toClear: Disposable[]) {
    function req<T> (method: string, h: rpc.RequestHandler1<ISourceLocationRequest, T, void>) {
        toClear.push(connection.onRequest(
            new rpc.RequestType1<ISourceLocationRequest, T, void>("rascal/vfs/input/" + method),
            h.bind(handler)));
    }
    req<LocationContentResponse>("readFile", handler.readFile);
    req<BooleanResponse>("exists", handler.exists);
    req<TimestampResponse>("lastModified", handler.lastModified);
    req<TimestampResponse>("created", handler.created);
    req<BooleanResponse>("isDirectory", handler.isDirectory);
    req<BooleanResponse>("isFile", handler.isFile);
    req<DirectoryListingResponse>("list", handler.list);
    req<NumberResponse>("size", handler.size);
    req<FileAttributes>("stat", handler.fileStat);
    req<BooleanResponse>("isReadable", handler.isReadable);
}

// Rascal's interface reduced to a subset we can support
interface ISourceLocationOutput {
    writeFile(req: WriteFileRequest): Promise<void>;
    mkDirectory(req: ISourceLocationRequest): Promise<void>;
    remove(req: RemoveRequest): Promise<void>;
    rename(req: RenameRequest): Promise<void>;
    copy(req: CopyRequest): Promise<void>;
    isWritable(req: ISourceLocationRequest): Promise<BooleanResponse>;
}

function connectOutputHandler(connection: rpc.MessageConnection, handler: ISourceLocationOutput, toClear: Disposable[]) {
    function req<Arg, ReturnT> (method: string, h: rpc.RequestHandler1<Arg, ReturnT, void>) {
        toClear.push(connection.onRequest(
            new rpc.RequestType1<Arg, ReturnT, void>("rascal/vfs/output/" + method),
            h.bind(handler)));
    }

    req("writeFile", handler.writeFile);
    req("mkDirectory", handler.mkDirectory);
    req("remove", handler.remove);
    req("rename", handler.rename);
    req("isWritable", handler.isWritable);
}

// Rascal's interface reduced to a subset we can support
interface ISourceLocationWatcher {
    watch(newWatch: WatchRequest): Promise<void>;
    unwatch(removeWatch: WatchRequest): Promise<void>;
}

function connectWatchHandler(connection: rpc.MessageConnection, handler: ISourceLocationWatcher, toClear: Disposable[]) {
    function req<T> (method: string, h: rpc.RequestHandler1<WatchRequest, T, void>) {
        toClear.push(connection.onRequest(
            new rpc.RequestType1<WatchRequest, T, void>("rascal/vfs/watcher/" + method),
            h.bind(handler)));
    }
    req("watch", handler.watch);
    req("unwatch", handler.unwatch);
}

interface ILogicalSourceLocationResolver {
    resolve(req: ISourceLocationRequest): Promise<SourceLocationResponse>
}

function connectLogicalResolver(connection: rpc.MessageConnection, handler: ILogicalSourceLocationResolver, toClear: Disposable[]) {
    toClear.push(connection.onRequest(
        new rpc.RequestType1<ISourceLocationRequest, SourceLocationResponse, void>("rascal/vfs/logical/resolveLocation"), handler.resolve.bind(handler)
    ));
}

// client side implementation receiving watch events
export interface WatchEventReceiver {
    emitWatch(event: ISourceLocationChanged): void;
}

function buildWatchReceiver(connection: rpc.MessageConnection): WatchEventReceiver {
    return {
        emitWatch: (e) => {
            connection.sendNotification(new rpc.NotificationType1<ISourceLocationChanged>("rascal/vfs/watcher/sourceLocationChanged"), e);
        }
    };
}

// Messages (requests and responses)

export interface ISourceLocationRequest {
    loc: ISourceLocation;
}

export interface WriteFileRequest extends ISourceLocationRequest {
    content: string;
    append: boolean;
}

export interface RenameRequest {
    from: ISourceLocation;
    to: ISourceLocation;
    overwrite: boolean;
}

export interface CopyRequest {
    from: ISourceLocation;
    to: ISourceLocation;
    recursive: boolean;
    overwrite: boolean;
}

export interface RemoveRequest extends ISourceLocationRequest {
    recursive: boolean;
}

export interface WatchRequest {
    loc: ISourceLocation;
    /**
     * subscription id, this helps the calling in linking up to the original request
     * as the watches are recursive
     */
    watchId: string;
    recursive: boolean;
}

export interface FileAttributes {
    exists: boolean;
    isFile: boolean;
    created: number;
    lastModified: number;
    isWritable: boolean;
    isReadable: boolean;
    size: number;
}

export enum ISourceLocationChangeType {
    created = 1,
    deleted = 2,
    modified = 3
}

export interface ISourceLocationChanged {
    root: ISourceLocation;
    type: ISourceLocationChangeType;
    watchId: string;
}

export interface LocationContentResponse {
    /**
     * Base64-encoded content of a location
     */
    content: string
}

interface BooleanResponse {
    value: boolean
}

interface NumberResponse {
    value: number
}

interface TimestampResponse {
    value: number
}

interface SourceLocationResponse {
    loc: ISourceLocation
}

export interface DirectoryListingResponse {
    entries: DirectoryEntry[]
}

export interface DirectoryEntry {
    name: string;
    types: vscode.FileType[]
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

class ResolverClient implements VSCodeResolverServer, Disposable  {
    private readonly watchListener: WatchEventReceiver;
    private readonly fs: vscode.FileSystem;
    private disposables: Disposable[] = [];
    constructor(private readonly connection: rpc.MessageConnection, debug: boolean, private readonly rascalNativeSchemes: Set<string>, private readonly logger: vscode.LogOutputChannel){
        this.fs = vscode.workspace.fs;
        if (debug) {
            connection.trace(rpc.Trace.Verbose, {
                log: (a) => {
                    this.logger.debug("[VSCodeFileSystemInRascal]: " + a);
                }
            });
        }
        this.watchListener = buildWatchReceiver(connection);
        connectInputHandler(connection, this, this.disposables);
        connectOutputHandler(connection, this, this.disposables);
        connectWatchHandler(connection, this, this.disposables);
        connectLogicalResolver(connection, this, this.disposables);
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
        this.logger.trace("[VSCodeFileSystemInRascal] readFile: ", req.loc);
        return this.asyncCatcher(async () => <LocationContentResponse>{
            content: Buffer.from(await this.fs.readFile(this.toUri(req.loc))).toString("base64")
        });
    }

    isRascalNative(loc: ISourceLocation | vscode.Uri): boolean {
        // Note that `ISourceLocation` === `URI` === `string` !== `vscode.Uri`
        const scheme = typeof(loc) === "string" ? loc.substring(0, loc.indexOf(":")) : loc.scheme;
        return this.rascalNativeSchemes.has(scheme);
    }

    async exists(req: ISourceLocationRequest): Promise<BooleanResponse> {
        this.logger.trace("[VSCodeFileSystemInRascal] exists: ", req.loc);
        try {
            await this.stat(req.loc);
            return { value: true };
        }
        catch (_e) {
            return { value: false };
        }
    }

    async fileStat(req: ISourceLocationRequest): Promise<FileAttributes> {
        return this.asyncCatcher(async () => {
            const fileInfo = await this.stat(req.loc);
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

    private async stat(loc: ISourceLocation): Promise<vscode.FileStat> {
        this.logger.trace("[VSCodeFileSystemInRascal] stat: ", loc);
        return this.fs.stat(this.toUri(loc));
    }

    private async numberResult(loc: ISourceLocation, mapper: (s: vscode.FileStat) => number): Promise<NumberResponse> {
        return this.asyncCatcher(async () => <NumberResponse>{ value: mapper((await this.stat(loc))) });
    }

    lastModified(req: ISourceLocationRequest): Promise<TimestampResponse> {
        this.logger.trace("[VSCodeFileSystemInRascal] lastModified: ", req.loc);
        return this.numberResult(req.loc, f => f.mtime);
    }

    created(req: ISourceLocationRequest): Promise<TimestampResponse> {
        this.logger.trace("[VSCodeFileSystemInRascal] created: ", req.loc);
        return this.numberResult(req.loc, f => f.ctime);
    }

    size(req: ISourceLocationRequest): Promise<NumberResponse> {
        this.logger.trace("[VSCodeFileSystemInRascal] size: ", req.loc);
        return this.numberResult(req.loc, f => f.size);
    }

    private async boolResult(loc: ISourceLocation, mapper: (s: vscode.FileStat) => boolean): Promise<BooleanResponse> {
        return this.asyncCatcher(async () => <BooleanResponse>{ value: mapper((await this.stat(loc))) });
    }

    isDirectory(req: ISourceLocationRequest): Promise<BooleanResponse> {
        this.logger.trace("[VSCodeFileSystemInRascal] isDirectory: ", req.loc);
        return this.boolResult(req.loc, f => (f.type & vscode.FileType.Directory) !== 0);
    }

    isFile(req: ISourceLocationRequest): Promise<BooleanResponse> {
        this.logger.trace("[VSCodeFileSystemInRascal] isFile: ", req.loc);
        // TODO: figure out how to handle vscode.FileType.Symlink
        return this.boolResult(req.loc, f => (f.type & vscode.FileType.File) !== 0);
    }

    isReadable(req: ISourceLocationRequest): Promise<BooleanResponse> {
        this.logger.trace("[VSCodeFileSystemInRascal] isReadable: ", req.loc);
        // if we can do a stat, we can read
        return this.boolResult(req.loc, _ => true);
    }

    async isWritable(req: ISourceLocationRequest): Promise<BooleanResponse> {
        this.logger.trace("[VSCodeFileSystemInRascal] isWritable: ", req.loc);
        const scheme = this.toUri(req.loc).scheme;
        const writable = this.fs.isWritableFileSystem(scheme);
        if (writable === undefined) {
            throw new rpc.ResponseError(RemoteIOError.unsupportedScheme, `Unsupported scheme: ${scheme}`);
        }
        if (!writable) {
            // not a writable file system, so no need to check the uri
            return { value: false };
        }
        return this.boolResult(req.loc, f => f.permissions === undefined || (f.permissions & vscode.FilePermission.Readonly) === 0);
    }

    async list(req: ISourceLocationRequest): Promise<DirectoryListingResponse> {
        this.logger.trace("[VSCodeFileSystemInRascal] list: ", req.loc);
        return this.asyncCatcher(async () => <DirectoryListingResponse>{ entries:
            (await this.fs.readDirectory(this.toUri(req.loc))).map(entry => <DirectoryEntry>{ name: entry[0], types: this.decodeFileTypeBitmask(entry[1]) })
        });
    }

    decodeFileTypeBitmask(input: number): vscode.FileType[] {
        return input === 0 ? [vscode.FileType.Unknown] : [vscode.FileType.File, vscode.FileType.Directory, vscode.FileType.SymbolicLink].filter(t => t === (t & input));
    }

    async writeFile(req: WriteFileRequest): Promise<void> {
        this.logger.trace("[VSCodeFileSystemInRascal] writeFile: ", req.loc);
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
        this.logger.trace("[VSCodeFileSystemInRascal] mkDirectory: ", req.loc);
        return this.asyncVoidCatcher(this.fs.createDirectory(this.toUri(req.loc)));
    }

    async remove(req: RemoveRequest): Promise<void> {
        this.logger.trace("[VSCodeFileSystemInRascal] remove: ", req.loc);
        return this.asyncVoidCatcher(this.fs.delete(this.toUri(req.loc), { recursive: req.recursive }));
    }

    async rename(req: RenameRequest): Promise<void> {
        this.logger.trace("[VSCodeFileSystemInRascal] rename: ", req.from, req.to);
        return this.asyncVoidCatcher(this.fs.rename(this.toUri(req.from), this.toUri(req.to), { overwrite: req.overwrite }));
    }

    async copy(req: CopyRequest): Promise<void> {
        this.logger.trace("[VSCodeFileSystemInRascal] copy: ", req.from, req.to);
        if (!req.recursive && await this.isDirectory({ loc: req.from })) {
            throw new rpc.ResponseError(RemoteIOError.isADirectory, 'Non-recursive copy requested on a directory', req);
        }
        return this.asyncVoidCatcher(this.fs.copy(this.toUri(req.from), this.toUri(req.to), { overwrite: req.overwrite }));
    }

    private readonly activeWatches = new Map<string, WatcherCallbacks>();

    async watch(newWatch: WatchRequest): Promise<void> {
        this.logger.trace("[VSCodeFileSystemInRascal] watch: ", newWatch.loc);
        const watchKey = newWatch.loc + newWatch.recursive;
        if (this.activeWatches.has(watchKey)) {
            throw new rpc.ResponseError(RemoteIOError.watchAlreadyDefined, `Watch already defined: ${newWatch.loc}`);
        }
        const watcher = new WatcherCallbacks(this.toUri(newWatch.loc), newWatch.recursive, this.watchListener, newWatch.watchId);
        this.activeWatches.set(watchKey, watcher);
        this.disposables.push(watcher);
    }

    async unwatch(removeWatch: WatchRequest): Promise<void> {
        this.logger.trace("[VSCodeFileSystemInRascal] unwatch: ", removeWatch.loc);
        const watchKey = removeWatch.loc + removeWatch.recursive;
        const watcher = this.activeWatches.get(watchKey);
        if (watcher === undefined) {
            throw new rpc.ResponseError(RemoteIOError.watchNotDefined, `Watch not defined: ${removeWatch.loc}`);
        }
        this.activeWatches.delete(watchKey);
        watcher.dispose();
        const index = this.disposables.indexOf(watcher);
        if (index >= 0) {
            this.disposables.splice(index, 1);
        }
    }

    async resolve(req: ISourceLocationRequest): Promise<SourceLocationResponse> {
        this.logger.trace("[VSCodeFileSystemInRascal] resolve: ", req.loc);
        return <SourceLocationResponse>{ loc: req.loc };
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
    constructor(uri: vscode.Uri, recursive: boolean, watchListener: WatchEventReceiver, watchId: string) {
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
        this.watchListener.emitWatch({
            root: uri.toString(),
            type: changeType,
            watchId: this.watchId
        });
    }

    dispose() {
        this.toClear.forEach(c => c.dispose());
        this.toClear.splice(0);
    }
}
