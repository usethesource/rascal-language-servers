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

declare type ISourceLocation = URI;

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
    readFile(req: ISourceLocation): Promise<string>;
    exists(req: ISourceLocation): Promise<boolean>;
    lastModified(req: ISourceLocation): Promise<number>;
    created(req: ISourceLocation): Promise<number>;
    isDirectory(req: ISourceLocation): Promise<boolean>;
    isFile(req: ISourceLocation): Promise<boolean>;
    list(req: ISourceLocation): Promise<[string, vscode.FileType][]>;
    size(req: ISourceLocation): Promise<number>;
    fileStat(req: ISourceLocation): Promise<FileAttributesResult>;
    isReadable(req: ISourceLocation): Promise<boolean>;
    isWritable(req: ISourceLocation): Promise<boolean>;
}

function connectInputHandler(connection: rpc.MessageConnection, handler: ISourceLocationInput, toClear: Disposable[]) {
    function req<T> (method: string, h: rpc.RequestHandler1<ISourceLocation, T, void>) {
        toClear.push(connection.onRequest(
            new rpc.RequestType1<ISourceLocation, T, void>("rascal/vfs/input/" + method),
            h.bind(handler)));
    }
    req<string>("readFile", handler.readFile);
    req<boolean>("exists", handler.exists);
    req<number>("lastModified", handler.lastModified);
    req<number>("created", handler.created);
    req<boolean>("isDirectory", handler.isDirectory);
    req<boolean>("isFile", handler.isFile);
    req<[string, vscode.FileType][]>("list", handler.list);
    req<number>("size", handler.size);
    req<FileAttributesResult>("stat", handler.fileStat);
    req<boolean>("isReadable", handler.isReadable);
    req<boolean>("isWritable", handler.isWritable);
}

// Rascal's interface reduced to a subset we can support
interface ISourceLocationOutput {
    writeFile(req: WriteFileRequest ): Promise<void>;
    mkDirectory(req: ISourceLocation): Promise<void>;
    remove(req: ISourceLocation, recursive: boolean): Promise<void>;
    rename(req: RenameRequest): Promise<void>;
}

function connectOutputHandler(connection: rpc.MessageConnection, handler: ISourceLocationOutput, toClear: Disposable[]) {
    function req1<Arg, ReturnT> (method: string, h: rpc.RequestHandler1<Arg, ReturnT, void>) {
        toClear.push(connection.onRequest(
            new rpc.RequestType1<Arg, ReturnT, void>("rascal/vfs/output/" + method),
            h.bind(handler)));
    }
    function req2<Arg0, Arg1, ReturnT> (method: string, h: rpc.RequestHandler2<Arg0, Arg1, ReturnT, void>) {
        toClear.push(connection.onRequest(
            new rpc.RequestType2<Arg0, Arg1, ReturnT, void>("rascal/vfs/output/" + method),
            h.bind(handler)));
    }
    req1("writeFile", handler.writeFile);
    req1("mkDirectory", handler.mkDirectory);
    req2("remove", handler.remove);
    req1("rename", handler.rename);
}

// Rascal's interface reduced to a subset we can support
interface ISourceLocationWatcher {
    watch(newWatch: WatchRequest): Promise<void>;
    unwatch(removeWatch: WatchRequest): Promise<void>;
}

function connectWatchHandler(connection: rpc.MessageConnection, handler: ISourceLocationWatcher, toClear: Disposable[]) {
    function req<ArgT, ResultT> (method: string, h: rpc.RequestHandler1<ArgT, ResultT, void>) {
        toClear.push(connection.onRequest(
            new rpc.RequestType1<ArgT, ResultT, void>("rascal/vfs/watcher/" + method),
            h.bind(handler)));
    }
    req("watch", handler.watch);
    req("unwatch", handler.unwatch);
}

// client side implementation receiving watch events
export interface WatchEventReceiver {
    emitWatch(event: ISourceLocationChanged): void;
}

function buildWatchReceiver(connection: rpc.MessageConnection) : WatchEventReceiver {
    return {
        emitWatch : (e) => {
            connection.sendNotification(new rpc.NotificationType1<ISourceLocationChanged>("rascal/vfs/watcher/emitWatch"), e);
        }
    };
}

// Messages (requests and responses)

export interface FileAttributesResult {
    exists : boolean;
    type: vscode.FileType;
    ctime: number;
    mtime: number;
    size: number;
    permissions: vscode.FilePermission;
}

export interface WriteFileRequest {
    uri: ISourceLocation;
    content: string;
    append: boolean;
}

export interface RenameRequest {
    from: ISourceLocation;
    to: ISourceLocation;
    overwrite: boolean;
}

export interface WatchRequest {
    uri: ISourceLocation;
    /**
     * subscription id, this helps the calling in linking up to the original request
     * as the watches are recursive
     */
    watcher: string;
    recursive: boolean;
}

export enum ISourceLocationChangeType {
    created = 1,
    deleted = 2,
    modified = 3
}

export interface ISourceLocationChanged {
    watchId: string;
    location: ISourceLocation;
    changeType: ISourceLocationChangeType;
}

enum ErrorCodes {
    generic = -1,
    fileSystem = -2,
    nativeRascal = -3
}

export class VSCodeUriResolverServer extends JsonRpcServer {
    private rascalNativeSchemes: Set<string> = new Set();
    constructor(debug: boolean, private readonly logger: vscode.LogOutputChannel) {
        super("VFS", connection => new ResolverClient(connection, debug, this.rascalNativeSchemes, this.logger), logger);
    }

    ignoreSchemes(toIgnore: string[]) {
        toIgnore.forEach(v => this.rascalNativeSchemes.add(v));
    }
}

async function asyncCatcher<T>(build: () => Thenable<T>): Promise<T> {
    try {
        return await build();
    }
    catch (e: unknown) {
        if (e instanceof vscode.FileSystemError) {
            throw new rpc.ResponseError(ErrorCodes.fileSystem, e.message, e.code);
        }
        if (e instanceof rpc.ResponseError) {
            throw e;
        }
        throw new rpc.ResponseError(ErrorCodes.generic, "" + e);
    }
}

async function asyncVoidCatcher(run: (() => Promise<void>) | Thenable<void>): Promise<void> {
    return asyncCatcher<void>(() => {
        if (typeof run === "function") {
            return run();
        }
        else {
            return run;
        }
    });
}

class ResolverClient implements VSCodeResolverServer, Disposable  {
    private readonly connection: rpc.MessageConnection;
    private readonly watchListener: WatchEventReceiver;
    private readonly fs: vscode.FileSystem;
    private readonly rascalNativeSchemes: Set<string>;
    private toClear: Disposable[] = [];
    constructor(connection: rpc.MessageConnection, debug: boolean, rascalNativeSchemes: Set<string>, private readonly logger: vscode.LogOutputChannel){
        this.rascalNativeSchemes = rascalNativeSchemes;
        this.fs = vscode.workspace.fs;
        this.connection = connection;
        if (debug) {
            connection.trace(rpc.Trace.Verbose, {
                log: (a) => {
                    this.logger.debug("[VFS]: " + a);
                }
            });
        }
        this.watchListener = buildWatchReceiver(connection);
        connectInputHandler(connection, this, this.toClear);
        connectOutputHandler(connection, this, this.toClear);
        connectWatchHandler(connection, this, this.toClear);
        connectLogicalResolver(connection, this, this.toClear);
    }

    toUri(loc: ISourceLocation): vscode.Uri {
        const uri = vscode.Uri.parse(loc);
        if (this.isRascalNative(uri)) {
            throw new rpc.ResponseError(ErrorCodes.nativeRascal, "Cannot request VFS jobs on native rascal URIs: " + loc);
        }
        return uri;
    }

    async readFile(loc: ISourceLocation): Promise<string> {
        this.logger.trace("[VFS] readFile: ", loc);
        return asyncCatcher(async () => Buffer.from(await this.fs.readFile(this.toUri(loc))).toString("base64"));
    }

    isRascalNative(loc: ISourceLocation | vscode.Uri) : boolean {
        //this.rascalNativeSchemes.has(uri.scheme)
        const scheme = typeof(loc) === "string" ? loc.substring(0, loc.indexOf(":")) : loc.scheme;
        return this.rascalNativeSchemes.has(scheme);
    }

    async exists(loc: ISourceLocation): Promise<boolean> {
        this.logger.trace("[VFS] exists: ", loc);
        try {
            await this.stat(loc);
            return true;
        }
        catch (_e) {
            return false;
        }
    }

    async fileStat(loc: ISourceLocation): Promise<FileAttributesResult> {
        return asyncCatcher(async () => {
            const fileInfo = await this.stat(loc);
            return {
                exists: true,
                type: fileInfo.type.valueOf(),
                ctime: fileInfo.ctime,
                mtime: fileInfo.mtime,
                size: fileInfo.size,
                permissions: fileInfo.permissions ? fileInfo.permissions.valueOf() : 0
            };
        });
    }

    private async stat(loc: ISourceLocation): Promise<vscode.FileStat> {
        this.logger.trace("[VFS] stat: ", loc);
        return this.fs.stat(this.toUri(loc));
    }

    private async numberResult(loc: ISourceLocation, mapper: (s: vscode.FileStat) => number): Promise<number> {
        return asyncCatcher(async () => mapper((await this.stat(loc))));
    }

    lastModified(loc: ISourceLocation): Promise<number> {
        this.logger.trace("[VFS] lastModified: ", loc);
        return this.numberResult(loc, f => f.mtime);
    }

    created(loc: ISourceLocation): Promise<number> {
        this.logger.trace("[VFS] created: ", loc);
        return this.numberResult(loc, f => f.ctime);
    }

    size(loc: ISourceLocation): Promise<number> {
        this.logger.trace("[VFS] size: ", loc);
        return this.numberResult(loc, f => f.size);
    }

    private async boolResult(loc: ISourceLocation, mapper: (s :vscode.FileStat) => boolean): Promise<boolean> {
        return asyncCatcher(async () => mapper((await this.stat(loc))));
    }

    isDirectory(loc: ISourceLocation): Promise<boolean> {
        this.logger.trace("[VFS] isDirectory: ", loc);
        return this.boolResult(loc, f => f.type === vscode.FileType.Directory);
    }

    isFile(loc: ISourceLocation): Promise<boolean> {
        this.logger.trace("[VFS] isFile: ", loc);
        // TODO: figure out how to handle vscode.FileType.Symlink
        return this.boolResult(loc, f => f.type === vscode.FileType.File);
    }

    isReadable(loc: ISourceLocation): Promise<boolean> {
        this.logger.trace("[VFS] isReadable: ", loc);
        // if we can do a stat, we can read
        return this.boolResult(loc, _ => true);
    }

    async isWritable(loc: ISourceLocation): Promise<boolean> {
        this.logger.trace("[VFS] isWritable: ", loc);
        const scheme = this.toUri(loc).scheme;
        const writable = this.fs.isWritableFileSystem(scheme);
        if (writable === undefined) {
            throw new rpc.ResponseError(ErrorCodes.fileSystem, "Unsupported scheme: " + scheme, "Unsupported file system");
        }
        if (!writable) {
            // not a writable file system, so no need to check the uri
            return false;
        }
        return this.boolResult(loc, f => f.permissions === undefined || (f.permissions & vscode.FilePermission.Readonly) === 0);
    }

    async list(loc: ISourceLocation): Promise<[string, vscode.FileType][]> {
        this.logger.trace("[VFS] list: ", loc);
        return asyncCatcher(async () => {
            return await this.fs.readDirectory(this.toUri(loc));
        });
    }

    async writeFile(req: WriteFileRequest): Promise<void> {
        this.logger.trace("[VFS] writeFile: ", req.uri);
        return asyncVoidCatcher(
            this.fs.writeFile(this.toUri(req.uri), Buffer.from(req.content, "base64"))
        );
    }
    async mkDirectory(req: ISourceLocation): Promise<void> {
        this.logger.trace("[VFS] mkDirectory: ", req);
        return asyncVoidCatcher(this.fs.createDirectory(this.toUri(req)));
    }
    async remove(req: ISourceLocation, recursive: boolean): Promise<void> {
        this.logger.trace("[VFS] remove: ", req);
        return asyncVoidCatcher(this.fs.delete(this.toUri(req), {"recursive" : recursive}));
    }
    async rename(req: RenameRequest): Promise<void> {
        this.logger.trace("[VFS] rename: ", req.from, req.to);
        const from = this.toUri(req.from);
        const to = this.toUri(req.to);
        return asyncVoidCatcher(this.fs.rename(from, to, { overwrite: req.overwrite }));
    }

    private readonly activeWatches = new Map<string, WatcherCallbacks>();

    async watch(newWatch: WatchRequest): Promise<void> {
        this.logger.trace("[VFS] watch: ", newWatch.uri);
        const watchKey = newWatch.uri + newWatch.recursive;
        if (!this.activeWatches.has(watchKey)) {
            const watcher = new WatcherCallbacks(this.toUri(newWatch.uri), newWatch.recursive, this.watchListener, newWatch.watcher);
            this.activeWatches.set(watchKey, watcher);
            this.toClear.push(watcher);
            return;
        }
        throw new rpc.ResponseError(ErrorCodes.fileSystem, 'Watch already defined for: ' + newWatch.uri, 'AlreadyDefined');
    }

    async unwatch(removeWatch: WatchRequest): Promise<void> {
        this.logger.trace("[VFS] unwatch: ", removeWatch.uri);
        const watchKey = removeWatch.uri + removeWatch.recursive;
        const watcher = this.activeWatches.get(watchKey);
        if (watcher) {
            this.activeWatches.delete(watchKey);
            watcher.dispose();
            const index = this.toClear.indexOf(watcher);
            if (index >= 0) {
                this.toClear.splice(index, 1);
            }
            return;
        }
        throw new rpc.ResponseError(ErrorCodes.fileSystem, 'Watch not defined for: ' + removeWatch.uri, 'NotDefined');
    }

    dispose() {
        this.activeWatches.clear();
        this.toClear.forEach(c => c.dispose());
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
            watchId: this.watchId,
            changeType: changeType,
            location: uri.toString()
        });
    }

    dispose() {
        this.toClear.forEach(c => c.dispose());
        this.toClear.splice(0);
    }
}
