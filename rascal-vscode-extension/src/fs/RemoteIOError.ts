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
import * as rpc from 'vscode-jsonrpc/node';
import { ResponseError } from "vscode-languageclient";

/**
 * This class mirrors the `RemoteIOError` enum in Rascal.
 * The values must be kept in sync.
 */
export class RemoteIOError {
    static readonly fileExists = -1;
    static readonly fileNotFound = -2;
    static readonly isADirectory = -3;
    static readonly isNotADirectory = -4;
    static readonly directoryIsNotEmpty = -5;
    static readonly permissionDenied = -6;
    static readonly unsupportedScheme = -7;
    static readonly illegalSyntax = -8;

    static readonly watchAlreadyDefined = -10;
    static readonly watchNotDefined = -11;

    static readonly fileSystemError = -20;

    static readonly isRascalNative = -30;

    static readonly jsonRpcError = -40;

    static readonly unknown = -100;

    static readonly notSupported = 101;

    static translateResponseError(r: ResponseError | undefined, uri: vscode.Uri | string, logger: vscode.LogOutputChannel) : vscode.FileSystemError | undefined {
        if (r !== undefined) {
            logger.debug("Received error from Rascal file system", r);
            switch (r.code) {
                case RemoteIOError.fileExists: return vscode.FileSystemError.FileExists(uri);
                case RemoteIOError.isADirectory: return vscode.FileSystemError.FileIsADirectory(uri);
                case RemoteIOError.isNotADirectory: return vscode.FileSystemError.FileNotADirectory(uri);
                case RemoteIOError.fileNotFound: return vscode.FileSystemError.FileNotFound(uri);
                case RemoteIOError.permissionDenied: return vscode.FileSystemError.NoPermissions(uri);
                default: return new vscode.FileSystemError(uri);
            }
        }
        return r;
    }

    static translateFileSystemError(e: vscode.FileSystemError | ResponseError | unknown, logger: vscode.LogOutputChannel) : ResponseError {
        logger.debug("Received error from VS Code file system", e);
        if (e instanceof vscode.FileSystemError) {
            switch (e.code) {
                case "FileExists":
                case "EntryExists":
                    return new rpc.ResponseError(RemoteIOError.fileExists, e.message);
                case "FileNotFound":
                case "EntryNotFound":
                    return new rpc.ResponseError(RemoteIOError.fileNotFound, e.message);
                case "FileNotADirectory":
                case "EntryNotADirectory":
                    return new rpc.ResponseError(RemoteIOError.isADirectory, e.message);
                case "FileIsADirectory":
                case "EntryIsADirectory":
                    return new rpc.ResponseError(RemoteIOError.isNotADirectory, e.message);
                case "NoPermissions":
                    return new rpc.ResponseError(RemoteIOError.permissionDenied, e.message);
            }
            return new rpc.ResponseError(RemoteIOError.fileSystemError, e.message);
        }
        if (e instanceof rpc.ResponseError) {
            return e;
        }
        return new rpc.ResponseError(RemoteIOError.unknown, "Unknown error occurred");
    }
}
