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

import { Dirent } from 'fs';
import path from 'path/posix';
import * as vscode from 'vscode';

export class TestVirtualFileSystem implements vscode.FileSystemProvider {
    private readonly _emitter = new vscode.EventEmitter<vscode.FileChangeEvent[]>();
    readonly onDidChangeFile: vscode.Event<vscode.FileChangeEvent[]> = this._emitter.event;
    private readonly root = new RootEntry();

    watch(_uri: vscode.Uri, _options: { readonly recursive: boolean; readonly excludes: readonly string[]; }): vscode.Disposable {
        // we don't have changes to files
        // so we never generate watch events
    }
    stat(uri: vscode.Uri): vscode.FileStat {
        return this.root.lookup(uri).stat();
    }

    readDirectory(uri: vscode.Uri): [string, vscode.FileType][] {
        const entry = this.root.lookup(uri);
        if (!entry.isDir()) {
            throw vscode.FileSystemError.FileNotADirectory(uri);
        }
        return entry.list(uri);
    }

    parentUri(uri: vscode.Uri) {
        return uri.with({path : path.dirname(uri.path)});
    }

    createDirectory(uri: vscode.Uri) {
        const parentUri = this.parentUri(uri);
        const parent = this.root.lookup(parentUri);
        if (!parent.isDir()) {
            throw vscode.FileSystemError.FileNotADirectory(parentUri);
        }
        parent.createDirectory(uri);
    }

    readFile(uri: vscode.Uri): Uint8Array {
        const entry = this.root.lookup(uri);
        if (!entry.isFile()) {
            throw vscode.FileSystemError.FileIsADirectory(uri);
        }
        return entry.read();
    }
    writeFile(uri: vscode.Uri, content: Uint8Array, options: { readonly create: boolean; readonly overwrite: boolean; }): void {
        const parent = this.root.lookup(this.parentUri(uri));
        if (!parent.isDir()) {
            throw vscode.FileSystemError.FileNotADirectory(this.parentUri(uri));
        }
        let child = parent.getEntry(uri);
        if (!child && !options.create) {
            throw vscode.FileSystemError.FileNotFound(uri);
        }
        if (child && !options.overwrite) {
            throw vscode.FileSystemError.FileExists(uri);
        }

        if (!child) {
            child = parent.createFile(uri);
        }
        if (!child.isFile()) {
            throw vscode.FileSystemError.FileIsADirectory(uri);
        }
        child.write(content);
    }
    delete(uri: vscode.Uri, options: { readonly recursive: boolean; }) {
        this.root.delete(uri, options.recursive);
    }
    rename(oldUri: vscode.Uri, newUri: vscode.Uri, options: { readonly overwrite: boolean; }) {
        const old = this.root.lookup(oldUri);
        this.root.put(newUri, old, options.overwrite);
        this.root.deleteRaw(oldUri);
    }
    copy(source: vscode.Uri, destination: vscode.Uri, options: { readonly overwrite: boolean; }) {
        const sourceEntry = this.root.lookup(source);
        this.root.put(destination, sourceEntry.clone(), options.overwrite);
    }
}




abstract class FSEntry {
    abstract clone(): FSEntry;
    abstract stat(): vscode.FileStat;

    constructor(protected readonly self: string) {}

    protected lastPart(uri: vscode.Uri) {
        return path.basename(uri.path);

    }

    isFile(): this is FileEntry {
        return false;
    }

    isDir(): this is DirEntry {
        return false;
    }
}


class DirEntry extends FSEntry {
    override clone(): FSEntry {
        throw new Error('Method not implemented.');
    }
    override stat(): vscode.FileStat {
        throw new Error('Method not implemented.');
    }
    protected readonly children: Map<string, FSEntry> = new Map();

    deleteRaw(uri: vscode.Uri) {
        if (!this.children.delete(this.lastPart(uri))) {
            throw vscode.FileSystemError.FileNotFound(uri);
        }
    }

    protected putRaw(newUri: vscode.Uri, newEntry: FSEntry, overwrite: boolean) {
        const key = this.lastPart(newUri);
        if (!overwrite && this.children.has(key)) {
            throw vscode.FileSystemError.FileExists(newUri);
        }
        this.children.set(key, newEntry);
    }
    createFile(uri: vscode.Uri): FileEntry  {
        throw new Error('Method not implemented.');
    }
    getEntry(uri: vscode.Uri | string): FSEntry {
        throw new Error('Method not implemented.');
    }
    createDirectory(uri: vscode.Uri) {
        throw new Error('Method not implemented.');
    }
    list(uri: vscode.Uri): [string, vscode.FileType][] {
        throw new Error('Method not implemented.');
    }
    lookup(uri: vscode.Uri): FSEntry {

        if (!entry) {
            throw vscode.FileSystemError.FileNotFound(uri);
        }
        throw new Error('Method not implemented.');
    }

    override isDir(): this is DirEntry {
        return true;
    }

}

class FileEntry extends FSEntry {
    override clone(): FSEntry {
        throw new Error('Method not implemented.');
    }
    override stat(): vscode.FileStat {
        throw new Error('Method not implemented.');
    }
    override isFile(): this is FileEntry {
        return true;
    }

    write(content: Uint8Array<ArrayBufferLike>) {
        throw new Error('Method not implemented.');
    }

    read(): Uint8Array<ArrayBufferLike> {
        throw new Error('Method not implemented.');
    }
}

class RootEntry extends DirEntry {

    constructor() {
        super("");
    }

    private locateEntry(uri: vscode.Uri) {
        if (uri.path === "/") {
            return this;
        }
        let result: FSEntry | undefined = undefined;
        for (const chunk of uri.path.split(path.sep)) {
            if (!result || result.isDir()) {
                result = (result??this).getEntry(chunk);
                if (!result) {
                    return undefined;
                }
            }
            else {
                return undefined;
            }
        }
    }
    delete(uri: vscode.Uri, recursive: boolean) {
        throw new Error('Method not implemented.');
    }

    put(newUri: vscode.Uri, newEntry: FSEntry, overwrite: boolean): void {


    }


}
