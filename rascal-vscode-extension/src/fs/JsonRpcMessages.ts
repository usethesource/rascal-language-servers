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
import { URI } from 'vscode-languageclient';

export declare type ISourceLocation = URI;

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

export interface WatchRequest extends ISourceLocationRequest {
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

export interface BooleanResponse {
    value: boolean
}

export interface NumberResponse {
    value: number
}

export interface TimestampResponse {
    value: number
}

export interface SourceLocationResponse {
    loc: ISourceLocation
}

export interface DirectoryListingResponse {
    entries: DirectoryEntry[]
}

export interface DirectoryEntry {
    name: string;
    types: vscode.FileType[]
}
