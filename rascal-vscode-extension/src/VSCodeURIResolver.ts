import { integer, NotificationType, URI } from "vscode-languageclient";
import * as rpc from 'vscode-jsonrpc/node';
import { Server, createServer, Socket, AddressInfo } from "net";
import { Disposable } from "vscode";
import * as vscode from 'vscode';
import { send } from "process";

declare type ISourceLocation = URI;

/**
 * VSCode implements this and offers it to the rascal-lsp server
 */
interface VSCodeResolverServer extends ISourceLocationInput, ISourceLocationOutput, ISourceLocationWatcher { }

/**
 * Rascal side should implement this on the other side of the stream
 */
interface VSCodeResolverClient extends WatchEventReceiver {}


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
    constructor() {
        this.server = createServer(newClient => {
            this.handleNewClient(newClient);
        });
        this.server.on('error', console.log);
        this.server.listen(0, "localhost", () => console.log("VFS: started listening on " + JSON.stringify(this.server.address())));
    }
    dispose() {
        this.server.close();
        this.activeClients.forEach(c => c.dispose());
    }

    private handleNewClient(newClient: Socket) {
        const connection = rpc.createMessageConnection(newClient, newClient);

        const client = new ResolverClient(connection);
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
    catch (e: any) {
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
    catch (e: any) {
        return {
            errorCode: 1,
            errorMessage: "" + e
        };
    }
}


class ResolverClient implements VSCodeResolverServer, Disposable  {
    private readonly connection: rpc.MessageConnection;
    private readonly watchListener: WatchEventReceiver;
    private readonly fs: vscode.FileSystem;
    private toClear: Disposable[] = [];
    constructor(connection: rpc.MessageConnection){
        this.fs = vscode.workspace.fs;
        this.connection = connection;
        this.watchListener = buildWatchReceiver(connection);
        connectInputHandler(connection, this);
        connectOutputHandler(connection, this);
        connectWatchHandler(connection, this);
    }



    async readFile(req: ISourceLocationRequest): Promise<ReadFileResult> {
        return asyncCatcher(async () => <ReadFileResult>{
            errorCode: 0,
            contents: Buffer.from(
                await this.fs.readFile(toUri(req))
            ).toString("base64")
        });
    }
    async exists(req: ISourceLocationRequest): Promise<BooleanResult> {
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

    list(req: ISourceLocationRequest): Promise<DirectoryListingResult> {
        return asyncCatcher(async () => <DirectoryListingResult>{
            errorCode: 0,
            entries: (await this.fs.readDirectory(toUri(req)))
                .map(([entry, _type], _index) => entry)
        });
    }



    writeFile(req: WriteFileRequest): Promise<IOResult> {
        return asyncVoidCatcher(
            this.fs.writeFile(toUri(req), Buffer.from(req.content, "base64"))
        );
    }
    mkDirectory(req: ISourceLocationRequest): Promise<IOResult> {
        return asyncVoidCatcher(this.fs.createDirectory(toUri(req)));
    }
    remove(req: ISourceLocationRequest): Promise<IOResult> {
        return asyncVoidCatcher(this.fs.delete(toUri(req)));
    }
    rename(req: RenameRequest): Promise<IOResult> {
        return asyncVoidCatcher(this.fs.rename(
            toUri(req.from),
            toUri(req.to),
            { overwrite: req.overwrite })
        );
    }

    private activeWatches = new Map<string, WatcherCallbacks>();

    watch(newWatch: WatchRequest): Promise<IOResult> {
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
        let watcher = this.activeWatches.get(removeWatch.uri);
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
        } catch (_e: any) {
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
        case ISourceLocationChangeType.deleted:
            // we have to guess, since we cannot ask anymore.
            const filePart = uri.path.substring(uri.path.lastIndexOf('/'));
            return filePart.lastIndexOf('.') > 0 ? ISourceLocationType.file : ISourceLocationType.directory;

    }
}
