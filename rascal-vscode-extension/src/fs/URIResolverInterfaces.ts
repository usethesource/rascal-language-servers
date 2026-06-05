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
import { ISourceLocationRequest, LocationContentResponse, BooleanResponse, TimestampResponse, DirectoryListingResponse, NumberResponse, FileAttributes, StringResponse, WriteFileRequest, RemoveRequest, RenameRequest, CopyRequest, SetLastModifiedRequest, WatchRequest, SourceLocationResponse, Capabilities } from './JsonRpcMessages';

export interface ISourceLocationCommon {
    getCharset(req: ISourceLocationRequest): Promise<StringResponse>;
    serverCapabilities(): Promise<Capabilities>;
}

export interface ISourceLocationInput {
    readFile(req: ISourceLocationRequest): Promise<LocationContentResponse>;
    exists(req: ISourceLocationRequest): Promise<BooleanResponse>;
    lastModified(req: ISourceLocationRequest): Promise<TimestampResponse>;
    created(req: ISourceLocationRequest): Promise<TimestampResponse>;
    isDirectory(req: ISourceLocationRequest): Promise<BooleanResponse>;
    isFile(req: ISourceLocationRequest): Promise<BooleanResponse>;
    list(req: ISourceLocationRequest): Promise<DirectoryListingResponse>;
    size(req: ISourceLocationRequest): Promise<NumberResponse>;
    stat(req: ISourceLocationRequest): Promise<FileAttributes>;
    isReadable(req: ISourceLocationRequest): Promise<BooleanResponse>;
}

export interface ISourceLocationOutput {
    writeFile(req: WriteFileRequest): Promise<void>;
    mkDirectory(req: ISourceLocationRequest): Promise<void>;
    remove(req: RemoveRequest): Promise<void>;
    rename(req: RenameRequest): Promise<void>;
    copy(req: CopyRequest): Promise<void>;
    isWritable(req: ISourceLocationRequest): Promise<BooleanResponse>;
    setLastModified(req: SetLastModifiedRequest): Promise<void>;
}

export interface ISourceLocationWatcher {
    watch(newWatch: WatchRequest): Promise<void>;
    unwatch(removeWatch: WatchRequest): Promise<void>;
}

export interface ILogicalSourceLocationResolver {
    resolve(req: ISourceLocationRequest): Promise<SourceLocationResponse>
}
