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

import path from 'path/posix';
import * as vscode from 'vscode';

/* This class is used to testing purposes of the VFS forwarding to rascal */
export class TestVirtualFileSystem implements vscode.FileSystemProvider, vscode.Disposable {
    public static readonly rootScheme = vscode.Uri.from({scheme: "rascal-vscode-test", path: "/"});
    private readonly _emitter = new vscode.EventEmitter<vscode.FileChangeEvent[]>();
    readonly onDidChangeFile: vscode.Event<vscode.FileChangeEvent[]> = this._emitter.event;
    private readonly root = new DirEntry(TestVirtualFileSystem.rootScheme);
    private readonly fs : vscode.Disposable;
    private testFileWriteCounter: number = 0;
    private testFileWatcher: vscode.FileSystemWatcher | undefined = undefined;
    private activeWatches: Map<[uri: vscode.Uri, recursive: boolean], readonly string[]> = new Map();

    constructor(private readonly logger: vscode.LogOutputChannel) {
        this.fs = vscode.workspace.registerFileSystemProvider(TestVirtualFileSystem.rootScheme.scheme, this, {isCaseSensitive: true});
        logger.info("Rascal Test VFS is registered under: ", TestVirtualFileSystem.rootScheme.scheme);
        this.initializeTestFiles();
    }

    dispose() {
        this._emitter.dispose();
        this.fs.dispose();
        this.testFileWatcher?.dispose();
    }

    private initializeTestFiles() {
        // Write JSON file that is filled with Pico command contents.
        // Reading the file requires registration of the Pico language first.
        this.writeDynamicFile(vscode.Uri.from({scheme: TestVirtualFileSystem.rootScheme.scheme, path: "test.json"}), async () => {
            const result = await vscode.commands.executeCommand<object>("rascal-meta-command", "Pico", "testValueEncoding()");
            return Buffer.from(JSON.stringify(result, null, 2));
        }, {create: true, overwrite: true});

        this.initializeRemoteFsTestFiles();
    }

    private initializeRemoteFsTestFiles() {
        // The following files play a role in the remote file system UI tests (see `remotefs.test.ts`).
        // Reading these files has side effects.

        const remotefsApiTestRoot = vscode.Uri.joinPath(TestVirtualFileSystem.rootScheme, "remotefs-api-test");
        this.createDirectory(remotefsApiTestRoot);

        const rascalfsTestTmpDir = vscode.Uri.from({scheme: "tmp", path: "/rascal-remotefs-test/"});

        this.writeDynamicFile(vscode.Uri.joinPath(remotefsApiTestRoot, "test-rascalfs-initiate-watch"), async () => {
            this.logger.debug("[TVFS] Creating test watch");
            const watchPattern = new vscode.RelativePattern(rascalfsTestTmpDir, "rascalfs-watch-test");
            this.logger.debug(`starting watch on ${watchPattern.toString()}`);
            this.testFileWatcher = vscode.workspace.createFileSystemWatcher(watchPattern);
            this.testFileWatcher.onDidChange(_e => {
                this.logger.debug(`[TVFS] onDidChange triggered on test file`);
                this.testFileWriteCounter += 1;
            });
            this.testFileWatcher.onDidCreate(_e => {
                this.logger.debug(`[TVFS] onDidCreate triggered on test file`);
                this.testFileWriteCounter += 1;
            });
            this.testFileWatcher.onDidDelete(_e => {
                this.logger.debug(`[TVFS] onDidDelete triggered on test file`);
                this.testFileWriteCounter += 1;
            });
            return Buffer.from("Started watch");
        }, {create: true, overwrite: true});

        this.writeDynamicFile(vscode.Uri.joinPath(remotefsApiTestRoot, "test-rascalfs-counter"), async () => {
            return Buffer.from(`${this.testFileWriteCounter}`);
        }, {create: true, overwrite: true});

        this.writeDynamicFile(vscode.Uri.joinPath(remotefsApiTestRoot, "test-rascalfs-end-watch"), async () => {
            this.logger.debug("[TVFS] Ending test watch");
            await this.testFileWatcher?.dispose();
            this.testFileWatcher = undefined;
            return Buffer.from("Disposed of watch");
        }, {create: true, overwrite: true});

        this.writeDynamicFile(vscode.Uri.joinPath(remotefsApiTestRoot, "test-rascalfs-write"), async () => {
            const rascalTestFile = vscode.Uri.joinPath(rascalfsTestTmpDir, "rascal-test-file");
            this.logger.debug("[TVFS] Writing test file");
            await vscode.workspace.fs.writeFile(rascalTestFile, Buffer.from("hi"));
            return Buffer.from("File written");
        }, {create: true, overwrite: true});
    }

    private locate(uri: vscode.Uri) : FSEntry {
        const [_, result] = this.locateWithParent(uri);
        if (!result) {
            throw vscode.FileSystemError.FileNotFound(uri);
        }
        return result;
    }

    private locateWithParent(uri:vscode.Uri) : [FSEntry, FSEntry | undefined] {
        if (uri.path === "/") {
            return [this.root, this.root];
        }
        let result : FSEntry = this.root;
        const parts = uri.path.substring(1).split(path.sep);
        for (const chunk of parts.splice(0, parts.length - 1)) {
            if (result.isDir()) {
                const entry = result.getEntry(chunk);
                if (!entry) {
                    throw vscode.FileSystemError.FileNotFound(uri);
                }
                result = entry;
            }
            else {
                throw vscode.FileSystemError.FileNotADirectory(this.parentUri(uri));
            }
        }
        // now the last part
        if (!result.isDir()) {
            throw vscode.FileSystemError.FileNotADirectory(this.parentUri(uri));
        }
        return [result, result.getEntry(parts[0]!)];

    }

    watch(uri: vscode.Uri, options: { readonly recursive: boolean; readonly excludes: readonly string[]; }): vscode.Disposable {
        this.logger.debug("[TVFS] watch ", uri);
        const watchKey: [vscode.Uri, boolean] = [uri, options.recursive];
        this.activeWatches.set(watchKey, options.excludes);

        return new vscode.Disposable(async () => {
            this.logger.debug("[TVFS] Watch disposed");
            this.activeWatches.delete(watchKey);
        });
    }

    notifyWatchers(targetUri: vscode.Uri, type: vscode.FileChangeType) {
        this.logger.debug("[TVFS] notifyWatchers ", targetUri);
        // Iterating over all active watches
        watches: for (const [[uri, recursive], excludes] of this.activeWatches) {
            if (targetUri.scheme !== uri.scheme || targetUri.authority !== uri.authority) {
                continue;
            }

            if (!recursive && uri.path !== targetUri.path && this.ensureTrailingSlash(uri.path) !== this.ensureTrailingSlash(path.dirname(targetUri.path))) {
                continue;
            }

            if (recursive && !targetUri.path.startsWith(this.ensureTrailingSlash(uri.path))) {
                // Current watch does not apply to the event uri
                continue;
            }

            // Current watch does apply to the event uri; checking whether it is excluded in this watch
            for (const exclude of excludes) {
                const isAbsolute = path.isAbsolute(exclude);
                const isGlob = exclude.indexOf("*") + exclude.indexOf("?") + exclude.indexOf("[") + exclude.indexOf("{") !== -4;
                if (isAbsolute && this.excludeMatchesUri(targetUri.path, exclude, isGlob)
                    || !isAbsolute && this.excludeMatchesUri(targetUri.path, path.join(uri.path, exclude), isGlob)) {
                    // Event uri was excluded in current watch
                    continue watches;
                }
            }

            // Current watch applies to the event uri and no exclude matches
            const callbackEvent = <vscode.FileChangeEvent>{ type: type, uri: targetUri };
            this.logger.debug("[TVFS] watch callback event", callbackEvent);
            this._emitter.fire([callbackEvent]);
            break;
        }
    }

    private ensureTrailingSlash(path: string): string {
        return path.endsWith("/") ? path : path + "/";
    }

    private excludeMatchesUri(uri: string, exclude: string, isGlob: boolean) {
        return (isGlob && path.matchesGlob(uri, exclude)) || (!isGlob && exclude === uri);
    }

    stat(uri: vscode.Uri): vscode.FileStat {
        this.logger.debug("[TVFS] stat: ", uri);
        return this.locate(uri).stat();
    }

    readDirectory(uri: vscode.Uri): [string, vscode.FileType][] {
        this.logger.debug("[TVFS] readDirectory: ", uri);
        const entry = this.locate(uri);
        if (!entry.isDir()) {
            throw vscode.FileSystemError.FileNotADirectory(uri);
        }
        return entry.list();
    }

    parentUri(uri: vscode.Uri) {
        return uri.with({path : path.dirname(uri.path)});
    }

    createDirectory(uri: vscode.Uri) {
        this.logger.debug("[TVFS] createDirectory: ", uri);
        this.notifyWatchers(uri, vscode.FileChangeType.Created);
        const [parent, self] = this.locateWithParent(uri);
        if (!parent.isDir()) {
            throw vscode.FileSystemError.FileNotADirectory(this.parentUri(uri));
        }
        if (self) {
            throw vscode.FileSystemError.FileExists(uri);
        }
        parent.putEntry(uri, new DirEntry(uri));
    }

    readFile(uri: vscode.Uri): Uint8Array | Promise<Uint8Array> {
        this.logger.debug("[TVFS] readFile: ", uri);
        const entry = this.locate(uri);
        if (!entry.isFile()) {
            throw vscode.FileSystemError.FileIsADirectory(uri);
        }
        return entry.read();
    }

    writeFile(uri: vscode.Uri, content: Uint8Array, options: { readonly create: boolean; readonly overwrite: boolean; }): void {
        this.logger.debug("[TVFS] writeFile: ", uri, options);
        this.notifyWatchers(uri, vscode.FileChangeType.Changed);
        const [parent, childConst] = this.locateWithParent(uri);
        let child = childConst;
        if (!parent.isDir()) {
            throw vscode.FileSystemError.FileNotADirectory(this.parentUri(uri));
        }
        if (!child && !options.create) {
            throw vscode.FileSystemError.FileNotFound(uri);
        }
        if (child && !options.overwrite) {
            throw vscode.FileSystemError.FileExists(uri);
        }

        child ??= parent.putEntry(uri, new FileEntry(uri));
        if (!child.isFile()) {
            throw vscode.FileSystemError.FileIsADirectory(uri);
        }
        child.write(content);
    }

    private writeDynamicFile(uri: vscode.Uri, contentReader: () => Promise<Uint8Array>, options: { readonly create: boolean; readonly overwrite: boolean; }): void {
        this.logger.debug("[TVFS] writeDynamicFile: ", uri, options);
        this.notifyWatchers(uri, vscode.FileChangeType.Changed);
        const [parent, childConst] = this.locateWithParent(uri);
        let child = childConst;
        if (!parent.isDir()) {
            throw vscode.FileSystemError.FileNotADirectory(this.parentUri(uri));
        }
        if (!child && !options.create) {
            throw vscode.FileSystemError.FileNotFound(uri);
        }
        if (child && !options.overwrite) {
            throw vscode.FileSystemError.FileExists(uri);
        }

        child ??= parent.putEntry(uri, new DynamicFileEntry(uri, contentReader));
        if (!child.isFile()) {
            throw vscode.FileSystemError.FileIsADirectory(uri);
        }
    }

    delete(uri: vscode.Uri, options: { readonly recursive: boolean; }) {
        this.logger.debug("[TVFS] delete: ", uri);
        this.notifyWatchers(uri, vscode.FileChangeType.Deleted);
        if (uri.path === "/") {
            throw vscode.FileSystemError.NoPermissions(uri);
        }
        const [parent, entry] = this.locateWithParent(uri);
        if (!parent.isDir()) {
            throw vscode.FileSystemError.FileNotADirectory(this.parentUri(uri));
        }
        if (!entry) {
            throw vscode.FileSystemError.FileNotFound(uri);
        }
        if (entry.isDir()) {
            if (!entry.isEmpty && !options.recursive) {
                throw vscode.FileSystemError.NoPermissions(uri.toString() + " is not empty");
            }
        }
        parent.deleteEntry(uri);
    }

    rename(oldUri: vscode.Uri, newUri: vscode.Uri, options: { readonly overwrite: boolean; }) {
        this.logger.debug("[TVFS] rename: ", oldUri, newUri);
        this.notifyWatchers(newUri, vscode.FileChangeType.Created);
        this.notifyWatchers(oldUri, vscode.FileChangeType.Deleted);
        this.copy(oldUri, newUri, options);
        this.delete(oldUri, { recursive: true });
    }

    copy(sourceUri: vscode.Uri, destinationUri: vscode.Uri, options: { readonly overwrite: boolean; }) {
        this.logger.debug("[TVFS] copy: ", sourceUri, destinationUri);
        this.notifyWatchers(destinationUri, vscode.FileChangeType.Created);
        const [sourceParent, source] = this.locateWithParent(sourceUri);
        if (!sourceParent.isDir()) {
            throw vscode.FileSystemError.FileNotADirectory(this.parentUri(sourceUri));
        }
        if (!source) {
            throw vscode.FileSystemError.FileNotFound(sourceUri);
        }
        const [destinationParent, destination] = this.locateWithParent(destinationUri);
        if (!destinationParent.isDir()) {
            throw vscode.FileSystemError.FileNotADirectory(this.parentUri(destinationUri));
        }
        if (destination && !options.overwrite) {
            throw vscode.FileSystemError.NoPermissions(destinationUri);
        }
        destinationParent.putEntry(destinationUri, source.clone(destinationUri));
    }
}




abstract class FSEntry {
    constructor(protected readonly self: vscode.Uri, public lastModified: Date) {}

    protected lastPart(uri: vscode.Uri) {
        return path.basename(uri.path);

    }

    abstract clone(newName: vscode.Uri): FSEntry;
    abstract stat(): vscode.FileStat;

    isFile(): this is FileEntry {
        return false;
    }

    isDir(): this is DirEntry {
        return false;
    }

    protected changed() {
        this.lastModified = new Date();

    }
}


class DirEntry extends FSEntry {
    private readonly children: Map<string, FSEntry> = new Map();

    constructor(name: vscode.Uri) {
        super(name, new Date());
    }

    get isEmpty() { return this.children.size === 0; }

    override clone(newName: vscode.Uri): FSEntry {
        const result = new DirEntry(newName);
        for (const [name,entry] of this.children) {
            const copy = entry.clone(newName.with({path : path.join(newName.path, name)}));
            copy.lastModified = entry.lastModified;
            result.children.set(name, copy);
        }
        return result;
    }

    override stat(): vscode.FileStat {
        return {
            ctime: this.lastModified.getTime(),
            mtime: this.lastModified.getTime(),
            size: this.children.size,
            type: vscode.FileType.Directory,
        };
    }

    deleteEntry(uri: vscode.Uri) {
        if (!this.children.delete(this.lastPart(uri))) {
            throw vscode.FileSystemError.FileNotFound(uri);
        }
        this.changed();
    }

    putEntry(newUri: vscode.Uri, newEntry: FSEntry): FSEntry {
        this.children.set(this.lastPart(newUri), newEntry);
        this.changed();
        return newEntry;
    }

    getEntry(uri: vscode.Uri | string): FSEntry | undefined {
        if (typeof uri !== 'string') {
            uri = this.lastPart(uri);
        }
        return this.children.get(uri);
    }

    list(): [string, vscode.FileType][] {
        const result : [string, vscode.FileType][] = [];
        for (const [name, entry] of this.children) {
            result.push([name, entry.isFile() ? vscode.FileType.File : vscode.FileType.Directory]);
        }
        return result;
    }

    override isDir(): this is DirEntry {
        return true;
    }

}

class FileEntry extends FSEntry {
    private content: Uint8Array<ArrayBufferLike>;

    constructor(name: vscode.Uri) {
        super(name, new Date());
        this.content = Uint8Array.of();
    }

    override clone(newName: vscode.Uri): FSEntry {
        const result = new FileEntry(newName);
        result.content = Uint8Array.from(this.content);
        return result;
    }

    override stat(): vscode.FileStat {
        return {
            ctime: this.lastModified.getTime(),
            mtime: this.lastModified.getTime(),
            size: this.content.length,
            type: vscode.FileType.File
        };
    }

    override isFile(): this is FileEntry {
        return true;
    }

    write(content: Uint8Array<ArrayBufferLike>) {
        this.content = Uint8Array.from(content);
        this.changed();
    }

    read(): Uint8Array | Promise<Uint8Array> {
        return Uint8Array.from(this.content);
    }
}

/**
 * Special read-only file entry that allows for on-demand contents.
 */
class DynamicFileEntry extends FileEntry {
    constructor(name: vscode.Uri, private readonly reader: () => Promise<Uint8Array>) {
        super(name);
    }

    override async read(): Promise<Uint8Array> {
        return this.reader();
    }

    override write(_content: Uint8Array) {
        throw vscode.FileSystemError.NoPermissions(this.self);
    }

    override clone(newName: vscode.Uri): FSEntry {
        return new DynamicFileEntry(newName, this.reader);
    }
}
