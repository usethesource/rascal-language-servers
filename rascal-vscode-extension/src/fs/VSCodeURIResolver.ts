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
import { integer, URI } from "vscode-languageclient";

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
}

// Rascal's interface reduce to a subset we can support
interface ISourceLocationOutput {
    writeFile(req: WriteFileRequest ): Promise<IOResult>;
    mkDirectory(req: ISourceLocationRequest): Promise<IOResult>;
    remove(req: ISourceLocationRequest): Promise<IOResult>;
    rename(req: RenameRequest): Promise<IOResult>;
}

function connectOutputHandler(connection: rpc.MessageConnection, handler: ISourceLocationOutput) {
    function req<Arg> (method: string, h: rpc.RequestHandler1<Arg, IOResult, void>) {
        connection.onRequest(
            new rpc.RequestType1<Arg, IOResult, void>("rascal/vfs/output/" + method),
            h.bind(handler));
    }
    req<WriteFileRequest>("writeFile", handler.writeFile);
    req<ISourceLocationRequest>("mkDirectory", handler.mkDirectory);
    req<ISourceLocationRequest>("remove", handler.remove);
    req<RenameRequest>("rename", handler.rename);
}

// Rascal's interface reduce to a subset we can support
interface ISourceLocationWatcher {
    watch(newWatch: WatchRequest): Promise<IOResult>;
    unwatch(removeWatch: WatchRequest): Promise<IOResult>;
}

function connectWatchHandler(connection: rpc.MessageConnection, handler: ISourceLocationWatcher) {
    function req<Arg> (method: string, h: rpc.RequestHandler1<Arg, IOResult, void>) {
        connection.onRequest(
            new rpc.RequestType1<Arg, IOResult, void>("rascal/vfs/watcher/" + method),
            h.bind(handler));
    }
    req<WatchRequest>("watch", handler.watch);
    req<WatchRequest>("unwatch", handler.unwatch);
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

interface IOResult {
    /**
     * Error type occurred (or 0 if success)
     */
    errorCode: integer;
    errorMessage?: string;
}

interface ReadFileResult extends IOResult {
    /**
     * base64 encoding of file
     */
    contents?: string;
}

export interface BooleanResult extends IOResult {
    result?: boolean;
}


export interface TimestampResult extends IOResult {
    /**
     * Epoch seconds
     */
    timestamp?: number;
}

export interface DirectoryListingResult extends IOResult {
    entries?: string[];
    areDirectory?: boolean[]
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
}


export enum ISourceLocationChangeType {
    created = 1,
    deleted = 2,
    modified = 3
}

export enum ISourceLocationType {
    file = 1,
    directory = 2
}

export interface ISourceLocationChanged {
    watchId: string;
    location: ISourceLocation;
    changeType: ISourceLocationChangeType;
    type: ISourceLocationType;
}


export class VSCodeUriResolverServer implements Disposable {
    private readonly server: Server;
    private activeClients: ResolverClient[] = [];
    private rascalNativeSchemes: Set<string> = new Set();
    constructor(debug: boolean) {
        this.server = createServer(newClient => {
            if (debug) {
                console.log("VFS: new connection: " + JSON.stringify(newClient));
            }
            newClient.setNoDelay(true);
            this.handleNewClient(newClient, debug);
        });
        this.server.on('error', console.log);
        this.server.listen(0, "localhost", () => console.log("VFS: started listening on " + JSON.stringify(this.server.address())));
    }

    ignoreSchemes(toIgnore: string[]) {
        toIgnore.forEach(v => this.rascalNativeSchemes.add(v));
    }

    dispose() {
        this.server.close();
        this.activeClients.forEach(c => c.dispose());
    }

    private handleNewClient(newClient: Socket, debug: boolean) {
        function makeLogger(prefix: string, onlyDebug: boolean): (m:string) => void {
            if (onlyDebug && !debug) {
                return (_s) => { return ; };
            }
            return (m) => console.log(prefix + ": " + m);
        }
        const connection = rpc.createMessageConnection(newClient, newClient, {
            log: makeLogger("VFS: [TRACE]", true),
            error: makeLogger("VFS: [ERROR]", false),
            warn: makeLogger("VFS: [WARN]", true),
            info: makeLogger("VFS: [INFO]", true),
        });
        newClient.on("error", e => {
            console.log("VFS: [SOCKET-ERROR]: " + e);
        });

        const client = new ResolverClient(connection, debug, this.rascalNativeSchemes);
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

function toUri(req: ISourceLocationRequest | ISourceLocation): vscode.Uri {
    if (typeof req !== 'string') {
        req = req.uri;
    }
    return vscode.Uri.parse(req);
}

async function asyncCatcher<T>(build: () => Promise<T>): Promise<T | IOResult> {
    try {
        return await build();
    }
    catch (e: unknown) {
        return <IOResult>{
            errorCode: 1,
            errorMessage: "" + e
        };
    }
}

async function asyncVoidCatcher(run: (() => Promise<void>) | Thenable<void>): Promise<IOResult> {
    try {
        if (typeof run === "function") {
            await run();
        }
        else {
            await run;
        }
        return {
            errorCode: 0
        };
    }
    catch (e: unknown) {
        return {
            errorCode: 1,
            errorMessage: "" + e
        };
    }
}


async function buildIOError(message: string, errorCode = -1): Promise<IOResult> {
    return {
        errorCode: errorCode,
        errorMessage: message
    };
}
class ResolverClient implements VSCodeResolverServer, Disposable  {
    private readonly connection: rpc.MessageConnection;
    private readonly watchListener: WatchEventReceiver;
    private readonly fs: vscode.FileSystem;
    private readonly rascalNativeSchemes: Set<string>;
    private toClear: Disposable[] = [];
    constructor(connection: rpc.MessageConnection, debug: boolean, rascalNativeSchemes: Set<string>){
        this.rascalNativeSchemes = rascalNativeSchemes;
        this.fs = vscode.workspace.fs;
        this.connection = connection;
        if (debug) {
            connection.trace(rpc.Trace.Verbose, {
                log: (a) => {
                    console.log("[VFS]: " + a);
                }
            });
        }
        this.watchListener = buildWatchReceiver(connection);
        connectInputHandler(connection, this);
        connectOutputHandler(connection, this);
        connectWatchHandler(connection, this);
    }



    async readFile(req: ISourceLocationRequest): Promise<ReadFileResult> {
        if (this.isRascalNative(req)) {
            return buildIOError("Cannot read a from a rascal uri: " + req.uri);
        }
        return asyncCatcher(async () => <ReadFileResult>{
            errorCode: 0,
            contents: Buffer.from(
                await this.fs.readFile(toUri(req))
            ).toString("base64")
        });
    }

    isRascalNative(req: ISourceLocationRequest) : boolean {
        const scheme = req.uri.substring(0, req.uri.indexOf(":"));
        return this.rascalNativeSchemes.has(scheme);
    }

    async exists(req: ISourceLocationRequest): Promise<BooleanResult> {
        if (this.isRascalNative(req)) {
            return buildIOError("Cannot exist on a rascal uri: " + req.uri);
        }
        try {
            await this.stat(req);
            return {
                errorCode: 0,
                result: true
            };
        }
        catch (_e) {
            return {
                errorCode: 0,
                result: false
            };
        }
    }
    private async stat(req: ISourceLocationRequest): Promise<vscode.FileStat> {
        const uri = toUri(req);
        if (this.rascalNativeSchemes.has(uri.scheme)) {
            throw new Error("Cannot stat a URI that's actually on the rascal side: " + req.uri);
        }
        return this.fs.stat(toUri(req));
    }

    private async timeStampResult(req: ISourceLocationRequest, mapper: (s :vscode.FileStat) => number): Promise<TimestampResult> {
        return asyncCatcher(async () => <TimestampResult>{
            errorCode: 0,
            timestamp: mapper((await this.stat(req)))
        });
    }

    lastModified(req: ISourceLocationRequest): Promise<TimestampResult> {
        return this.timeStampResult(req, f => f.mtime);
    }
    created(req: ISourceLocationRequest): Promise<TimestampResult> {
        return this.timeStampResult(req, f => f.ctime);
    }

    private async boolResult(req: ISourceLocationRequest, mapper: (s :vscode.FileStat) => boolean): Promise<BooleanResult> {
        return asyncCatcher(async () => <BooleanResult>{
            errorCode: 0,
            result: mapper((await this.stat(req)))
        });
    }

    isDirectory(req: ISourceLocationRequest): Promise<BooleanResult> {
        return this.boolResult(req, f => f.type === vscode.FileType.Directory);
    }

    isFile(req: ISourceLocationRequest): Promise<BooleanResult> {
        // TODO: figure out how to handle vscode.FileType.Symlink
        return this.boolResult(req, f => f.type === vscode.FileType.File);
    }



    async list(req: ISourceLocationRequest): Promise<DirectoryListingResult> {
        if (this.isRascalNative(req)) {
            return buildIOError("Cannot list on a rascal uri: " + req.uri);
        }
        return asyncCatcher(async () => {
            const entries = await this.fs.readDirectory(toUri(req));
            return <DirectoryListingResult>{
                errorCode: 0,
                entries: entries.map(([entry, _type], _index) => entry),
                areDirectory: entries.map(([_entry, type], _index) => type === vscode.FileType.Directory)
            };
        });
    }



    async writeFile(req: WriteFileRequest): Promise<IOResult> {
        if (this.isRascalNative(req)) {
            return buildIOError("Cannot writeFile on a rascal uri: " + req.uri);
        }
        return asyncVoidCatcher(
            this.fs.writeFile(toUri(req), Buffer.from(req.content, "base64"))
        );
    }
    async mkDirectory(req: ISourceLocationRequest): Promise<IOResult> {
        if (this.isRascalNative(req)) {
            return buildIOError("Cannot mkDirectory on a rascal uri: " + req.uri);
        }
        return asyncVoidCatcher(this.fs.createDirectory(toUri(req)));
    }
    async remove(req: ISourceLocationRequest): Promise<IOResult> {
        if (this.isRascalNative(req)) {
            return buildIOError("Cannot remove on a rascal uri: " + req.uri);
        }
        return asyncVoidCatcher(this.fs.delete(toUri(req)));
    }
    rename(req: RenameRequest): Promise<IOResult> {
        const from = toUri(req.from);
        const to = toUri(req.to);
        if (this.rascalNativeSchemes.has(from.scheme) || this.rascalNativeSchemes.has(to.scheme)) {
            return buildIOError("Cannot rename on a rascal uri: " + req.from + " and " + req.to);
        }
        return asyncVoidCatcher(this.fs.rename(from, to, { overwrite: req.overwrite }));
    }

    private activeWatches = new Map<string, WatcherCallbacks>();

    watch(newWatch: WatchRequest): Promise<IOResult> {
        if (this.isRascalNative(newWatch)) {
            return buildIOError("Cannot watch on a rascal uri: " + newWatch.uri);
        }
        if (!this.activeWatches.has(newWatch.uri)) {
            const watcher = new WatcherCallbacks(newWatch.uri, this.watchListener, newWatch.watcher);
            this.activeWatches.set(newWatch.uri, watcher);
            this.toClear.push(watcher);
            return Promise.resolve({ errorCode: 0 });
        }
        return Promise.resolve({
            errorCode: 1,
            errorMessage: 'Watch already defined for: ' + newWatch.uri
        });
    }



    unwatch(removeWatch: WatchRequest): Promise<IOResult> {
        if (this.isRascalNative(removeWatch)) {
            return buildIOError("Cannot watch on a rascal uri: " + removeWatch.uri);
        }
        const watcher = this.activeWatches.get(removeWatch.uri);
        if (watcher) {
            this.activeWatches.delete(removeWatch.uri);
            watcher.dispose();
            const index = this.toClear.indexOf(watcher);
            if (index >= 0) {
                this.toClear.splice(index, 1);
            }
            return Promise.resolve({ errorCode: 0 });
        }
        return Promise.resolve({
            errorCode: 1,
            errorMessage: 'Watch not defined for: ' + removeWatch.uri
        });
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
    constructor(uri: string, watchListener: WatchEventReceiver, watchId: string) {
        this.watchId = watchId;
        this.watchListener = watchListener;
        const newWatcher = vscode.workspace.createFileSystemWatcher(
            new vscode.RelativePattern(toUri(uri), '**/*')
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
            location: uri.toString(),
            type: await determineType(uri, changeType)
        });
    }

    dispose() {
        this.toClear.forEach(c => c.dispose());
        this.toClear.splice(0);
    }
}

async function determineType(uri: vscode.Uri, changeType: ISourceLocationChangeType): Promise<ISourceLocationType> {
    // vscode is not offering us information if the change was a directory of a file
    // so we have to use some heuristics (or actually ask the FS) to find out
    switch (changeType) {
        case ISourceLocationChangeType.created:
            if ((await vscode.workspace.fs.stat(uri)).type === vscode.FileType.Directory) {
                return ISourceLocationType.directory;
            } else {

                return ISourceLocationType.file;
            }
        case ISourceLocationChangeType.modified:
            // no modified events for directories according to documentation
            return ISourceLocationType.file;
        case ISourceLocationChangeType.deleted: {
            // we have to guess, since we cannot ask anymore.
            const filePart = uri.path.substring(uri.path.lastIndexOf('/'));
            return filePart.lastIndexOf('.') > 0 ? ISourceLocationType.file : ISourceLocationType.directory;
        }

    }
}
