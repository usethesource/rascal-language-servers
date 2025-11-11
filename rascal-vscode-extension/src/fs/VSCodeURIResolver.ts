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
import { AddressInfo, createServer, Server, Socket } from "net";
import * as vscode from 'vscode';
import { Disposable } from "vscode";
import * as rpc from 'vscode-jsonrpc/node';
import { URI } from "vscode-languageclient";

declare type ISourceLocation = URI;

/**
 * VSCode implements this and offers it to the rascal-lsp server
 */
interface VSCodeResolverServer extends ISourceLocationInput, ISourceLocationOutput, ISourceLocationWatcher { }

/**
 * Rascal side should implement this on the other side of the stream
 */
//interface VSCodeResolverClient extends WatchEventReceiver {}


// Rascal's interface reduce to a subset we can support
interface ISourceLocationInput {
    readFile(req: ISourceLocationRequest): Promise<ReadFileResult>;
    exists(req: ISourceLocationRequest): Promise<BooleanResult>;
    lastModified(req: ISourceLocationRequest): Promise<TimestampResult>;
    created(req: ISourceLocationRequest): Promise<TimestampResult>;
    isDirectory(req: ISourceLocationRequest): Promise<BooleanResult>;
    isFile(req: ISourceLocationRequest): Promise<BooleanResult>;
    list(req: ISourceLocationRequest): Promise<DirectoryListingResult>;
    size(req: ISourceLocationRequest): Promise<NumberResult>;
    fileStat(req: ISourceLocationRequest): Promise<FileAttributesResult>;
    isReadable(req: ISourceLocationRequest): Promise<BooleanResult>;
    isWritable(req: ISourceLocationRequest): Promise<BooleanResult>;
}


function connectInputHandler(connection: rpc.MessageConnection, handler: ISourceLocationInput) {
    function req<T> (method: string, h: rpc.RequestHandler1<ISourceLocationRequest, T, void>) {
        connection.onRequest(
            new rpc.RequestType1<ISourceLocationRequest, T, void>("rascal/vfs/input/" + method),
            h.bind(handler));
    }
    req<ReadFileResult>("readFile", handler.readFile);
    req<BooleanResult>("exists", handler.exists);
    req<TimestampResult>("lastModified", handler.lastModified);
    req<TimestampResult>("created", handler.created);
    req<BooleanResult>("isDirectory", handler.isDirectory);
    req<BooleanResult>("isFile", handler.isFile);
    req<DirectoryListingResult>("list", handler.list);
    req<NumberResult>("size", handler.size);
    req<FileAttributesResult>("stat", handler.fileStat);
    req<BooleanResult>("isReadable", handler.isReadable);
    req<BooleanResult>("isWritable", handler.isWritable);
}

// Rascal's interface reduce to a subset we can support
interface ISourceLocationOutput {
    writeFile(req: WriteFileRequest ): Promise<void>;
    mkDirectory(req: ISourceLocationRequest): Promise<void>;
    remove(req: ISourceLocationRequest): Promise<void>;
    rename(req: RenameRequest): Promise<void>;
}

function connectOutputHandler(connection: rpc.MessageConnection, handler: ISourceLocationOutput) {
    function req<Arg, ReturnT> (method: string, h: rpc.RequestHandler1<Arg, ReturnT, void>) {
        connection.onRequest(
            new rpc.RequestType1<Arg, ReturnT, void>("rascal/vfs/output/" + method),
            h.bind(handler));
    }
    req("writeFile", handler.writeFile);
    req("mkDirectory", handler.mkDirectory);
    req("remove", handler.remove);
    req("rename", handler.rename);
}

// Rascal's interface reduce to a subset we can support
interface ISourceLocationWatcher {
    watch(newWatch: WatchRequest): Promise<void>;
    unwatch(removeWatch: WatchRequest): Promise<void>;
}

function connectWatchHandler(connection: rpc.MessageConnection, handler: ISourceLocationWatcher) {
    function req<ArgT, ResultT> (method: string, h: rpc.RequestHandler1<ArgT, ResultT, void>) {
        connection.onRequest(
            new rpc.RequestType1<ArgT, ResultT, void>("rascal/vfs/watcher/" + method),
            h.bind(handler));
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

interface ISourceLocationRequest {
    uri: ISourceLocation;
}

interface ReadFileResult {
    /**
     * base64 encoding of file
     */
    contents: string;
}

export interface BooleanResult {
    result: boolean;
}


export interface TimestampResult {
    /**
     * Epoch seconds
     */
    timestamp: number;
}

export interface DirectoryListingResult {
    entries: string[];
    areDirectory: boolean[]
}

export interface NumberResult {
    result: number;
}

export interface FileAttributesResult {
    exists : boolean;
    type: vscode.FileType;
    ctime: number;
    mtime: number;
    size: number;
    permissions: vscode.FilePermission;
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


export interface WatchRequest extends ISourceLocationRequest {
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


export class VSCodeUriResolverServer implements Disposable {
    private readonly server: Server;
    private activeClients: ResolverClient[] = [];
    private rascalNativeSchemes: Set<string> = new Set();
    constructor(debug: boolean, private readonly logger: vscode.LogOutputChannel) {
        this.server = createServer(newClient => {
            if (debug) {
                this.logger.info("VFS: new connection: " + JSON.stringify(newClient));
            }
            newClient.setNoDelay(true);
            this.handleNewClient(newClient, debug);
        });
        this.server.on('error', this.logger.error);
        this.server.listen(0, "localhost", () => this.logger.info("VFS: started listening on " + JSON.stringify(this.server.address())));
    }

    ignoreSchemes(toIgnore: string[]) {
        toIgnore.forEach(v => this.rascalNativeSchemes.add(v));
    }

    dispose() {
        this.server.close();
        this.activeClients.forEach(c => c.dispose());
    }

    private handleNewClient(newClient: Socket, debug: boolean) {
        const connection = rpc.createMessageConnection(newClient, newClient, {
            log: (msg) => this.logger.trace(`VFS: ${msg}`),
            error: (msg) => this.logger.error(`VFS: ${msg}`),
            warn: (msg) => this.logger.warn(`VFS: ${msg}`),
            info: (msg) => this.logger.info(`VFS: ${msg}`),
        });
        newClient.on("error", e => {
            this.logger.error(`VFS (socket): ${e}`);
        });

        const client = new ResolverClient(connection, debug, this.rascalNativeSchemes, this.logger);
        this.activeClients.push(client);

        newClient.on('end', () => {
            const index = this.activeClients.indexOf(client, 0);
            if (index >= 0) {
                this.activeClients.splice(index, 1);
            }
            client.dispose();
        });
        connection.listen();
    }


    get port(): number {
        return (this.server.address() as AddressInfo).port;
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
        connectInputHandler(connection, this);
        connectOutputHandler(connection, this);
        connectWatchHandler(connection, this);
    }

    toUri(req: ISourceLocationRequest | ISourceLocation): vscode.Uri {
        if (typeof req !== 'string') {
            req = req.uri;
        }
        const uri = vscode.Uri.parse(req);
        if (this.isRascalNative(uri)) {
            throw new rpc.ResponseError(ErrorCodes.nativeRascal, "Cannot request VFS jobs on native rascal URIs: " + req);
        }
        return uri;
    }



    async readFile(req: ISourceLocationRequest): Promise<ReadFileResult> {
        this.logger.trace("[VFS] readFile: ", req.uri);
        return asyncCatcher(async () => <ReadFileResult>{
            errorCode: 0,
            contents: Buffer.from(
                await this.fs.readFile(this.toUri(req))
            ).toString("base64")
        });
    }

    isRascalNative(req: ISourceLocationRequest | vscode.Uri) : boolean {
        //this.rascalNativeSchemes.has(uri.scheme)
        const scheme = "scheme" in req ? req.scheme : req.uri.substring(0, req.uri.indexOf(":"));
        return this.rascalNativeSchemes.has(scheme);
    }

    async exists(req: ISourceLocationRequest): Promise<BooleanResult> {
        this.logger.trace("[VFS] exists: ", req.uri);
        try {
            await this.stat(req);
            return { result: true };
        }
        catch (_e) {
            return { result: false };
        }
    }

    async fileStat(req: ISourceLocationRequest): Promise<FileAttributesResult> {
        return asyncCatcher(async () => {
            const fileInfo = await this.stat(req);
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

    private async stat(req: ISourceLocationRequest): Promise<vscode.FileStat> {
        this.logger.trace("[VFS] stat: ", req.uri);
        return this.fs.stat(this.toUri(req));
    }

    private async timeStampResult(req: ISourceLocationRequest, mapper: (s :vscode.FileStat) => number): Promise<TimestampResult> {
        return asyncCatcher(async () => <TimestampResult>{
            timestamp: mapper((await this.stat(req)))
        });
    }

    lastModified(req: ISourceLocationRequest): Promise<TimestampResult> {
        this.logger.trace("[VFS] lastModified: ", req.uri);
        return this.timeStampResult(req, f => f.mtime);
    }
    created(req: ISourceLocationRequest): Promise<TimestampResult> {
        this.logger.trace("[VFS] created: ", req.uri);
        return this.timeStampResult(req, f => f.ctime);
    }

    private async numberResult(req: ISourceLocationRequest, mapper: (s: vscode.FileStat) => number): Promise<NumberResult> {
        return asyncCatcher(async () => <NumberResult>{
            result: mapper((await this.stat(req)))
        });
    }

    size(req: ISourceLocationRequest): Promise<NumberResult> {
        this.logger.trace("[VFS] size: ", req.uri);
        return this.numberResult(req, f => f.size);
    }

    private async boolResult(req: ISourceLocationRequest, mapper: (s :vscode.FileStat) => boolean): Promise<BooleanResult> {
        return asyncCatcher(async () => <BooleanResult>{
            result: mapper((await this.stat(req)))
        });
    }

    isDirectory(req: ISourceLocationRequest): Promise<BooleanResult> {
        this.logger.trace("[VFS] isDirectory: ", req.uri);
        return this.boolResult(req, f => f.type === vscode.FileType.Directory);
    }

    isFile(req: ISourceLocationRequest): Promise<BooleanResult> {
        this.logger.trace("[VFS] isFile: ", req.uri);
        // TODO: figure out how to handle vscode.FileType.Symlink
        return this.boolResult(req, f => f.type === vscode.FileType.File);
    }

    isReadable(req: ISourceLocationRequest): Promise<BooleanResult> {
        this.logger.trace("[VFS] isReadable: ", req.uri);
        // if we can do a stat, we can read
        return this.boolResult(req, _ => true);
    }

    async isWritable(req: ISourceLocationRequest): Promise<BooleanResult> {
        this.logger.trace("[VFS] isWritable: ", req.uri);
        const scheme = this.toUri(req).scheme;
        const writable = this.fs.isWritableFileSystem(scheme);
        if (writable === undefined) {
            throw new rpc.ResponseError(ErrorCodes.fileSystem, "Unsupported scheme: " + scheme, "Unsupported file system");
        }
        if (!writable) {
            // not a writable file system, so no need to check the uri
            return {result : false };
        }
        return this.boolResult(req, f => f.permissions === undefined || (f.permissions & vscode.FilePermission.Readonly) === 0);
    }

    async list(req: ISourceLocationRequest): Promise<DirectoryListingResult> {
        this.logger.trace("[VFS] list: ", req.uri);
        return asyncCatcher(async () => {
            const entries = await this.fs.readDirectory(this.toUri(req));
            return <DirectoryListingResult>{
                entries: entries.map(([entry, _type], _index) => entry),
                areDirectory: entries.map(([_entry, type], _index) => type === vscode.FileType.Directory)
            };
        });
    }



    async writeFile(req: WriteFileRequest): Promise<void> {
        this.logger.trace("[VFS] writeFile: ", req.uri);
        return asyncVoidCatcher(
            this.fs.writeFile(this.toUri(req), Buffer.from(req.content, "base64"))
        );
    }
    async mkDirectory(req: ISourceLocationRequest): Promise<void> {
        this.logger.trace("[VFS] mkDirectory: ", req.uri);
        return asyncVoidCatcher(this.fs.createDirectory(this.toUri(req)));
    }
    async remove(req: ISourceLocationRequest): Promise<void> {
        this.logger.trace("[VFS] remove: ", req.uri);
        return asyncVoidCatcher(this.fs.delete(this.toUri(req)));
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
