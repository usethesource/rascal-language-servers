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

import { BaseLanguageClient, ResponseError } from 'vscode-languageclient';
import { VSCodeFileSystemInRascal } from '../fs/VSCodeFileSystemInRascal';
import { activateLanguageClient } from './RascalLSPConnection';
import { ParameterizedLanguageServer } from './ParameterizedLanguageServer';
import { RascalDebugClient } from '../dap/RascalDebugClient';
import { RASCAL_LANGUAGE_ID } from '../Identifiers';
import { LanguageRegistry } from './LanguageRegistry';
import { SourceLocationResponse } from '../fs/JsonRpcMessages';
import { RemoteIOError } from '../fs/RemoteIOError';

export class RascalLanguageServer implements vscode.Disposable {
    public readonly rascalClient: Promise<BaseLanguageClient>;
    public readonly rascalDebugClient: RascalDebugClient;
    public readonly languageRegistry: LanguageRegistry;

    constructor(
        _context: vscode.ExtensionContext,
        vfsServer: VSCodeFileSystemInRascal,
        absoluteJarPath: string,
        dslLSP: ParameterizedLanguageServer,
        private readonly logger: vscode.LogOutputChannel,
        deployMode = true) {
        this.rascalClient = activateLanguageClient({
            deployMode: deployMode,
            devPort: 8888,
            isParametricServer: false,
            jarPath: absoluteJarPath,
            language: RASCAL_LANGUAGE_ID,
            title: 'Rascal MPL Language Server',
            vfsServer: vfsServer,
            dedicated: false,
            lspArg: undefined
        });

        this.rascalDebugClient = new RascalDebugClient();

        this.languageRegistry = new LanguageRegistry(dslLSP, logger);

        void this.rascalClient.then(client => {
            client.onNotification("rascal/startDebuggingSession", (serverPort:number) => {
                void this.rascalDebugClient.startDebuggingSession(serverPort);
            });
            client.onNotification("rascal/registerDebugServerPort", (processID:number, serverPort:number) => {
                this.rascalDebugClient.registerDebugServerPort(processID, serverPort);
            });
        });
    }
    dispose() {
        void this.rascalClient.then(c => c.dispose());
        this.languageRegistry.dispose();
    }

    private async requestResolve(uri: vscode.Uri, coordinates?: IRascalCoordinates): Promise<SourceLocationResponse> {
        try {
            return (await this.rascalClient).sendRequest("rascal/vfs/logical/resolve", {
                loc: addCoordinates(toRascalUri(uri), coordinates)
            });
        } catch (error) {
            throw RemoteIOError.translateResponseError(error as ResponseError, uri, this.logger);
        }
    }

    async resolve(uri: vscode.Uri, coordinates?: IRascalCoordinates) : Promise<[vscode.Uri, IRascalCoordinates | undefined]> {
        this.logger.debug("[RascalLanguageServer] resolve: ", [uri, coordinates]);
        const result = await this.requestResolve(uri, coordinates);

        if (!result.loc) {
            this.logger.trace("[RascalFileSystemInVSCode] resolveClient return empty result for: ", uri);
            return [uri, coordinates];
        }
        if (!result.loc.startsWith('|')) {
            return [vscode.Uri.parse(result.loc), undefined];
        }
        return parseFullSourceLocation(result.loc);
    }
}

function addCoordinates(uri: string, coordinates: IRascalCoordinates | undefined): string {
    if (!coordinates) {
        return uri;
    }
    let result = `|${uri}|(${coordinates.offsetLength[0]},${coordinates.offsetLength[1]}`;
    if (coordinates.beginLineColumn && coordinates.endLineColumn) {
        result += `,<${coordinates.beginLineColumn[0]},${coordinates.beginLineColumn[1]}>,<${coordinates.endLineColumn[0]},${coordinates.endLineColumn[1]}>`;
    }
    return result + ")";
}

// VS Code omits the leading two slashes from URIs if the autority is empty *and* the scheme is not equal to "file"
// Rascal does not support this style of URIs, so we add the slashes before sending the URI over
function toRascalUri(uri: vscode.Uri): string {
    const uriString = uri.toString();
    if (uri.authority === "" && uri.scheme !== "file") {
        const colon = uri.scheme.length + 1;
        return `${uriString.slice(0, colon)}//${uriString.slice(colon)}`;
    }
    return uriString;
}

function asNumberPair(ar: number[]): [number, number] {
    if (ar.length !==  2) {
        throw new Error(`Cannot convert ${ar} to tuple pair`);
    }
    return [ar[0]!, ar[1]!];
}

function parseFullSourceLocation(loc: string): [vscode.Uri, IRascalCoordinates | undefined] {
    const [uriPart, coordinates] = loc.substring(1).split('|');
    if (!uriPart) {
        throw new vscode.FileSystemError(`Can not parse ${loc} as ISourceLocation`);
    }
    const base = vscode.Uri.parse(uriPart);
    if (!coordinates) {
        return [base, undefined];
    }
    if (!coordinates.startsWith('(') || !coordinates.endsWith(')')) {
        throw new vscode.FileSystemError(`Can not parse ${loc} as ISourceLocation due to incorrect coordinates`);
    }
    const coords = coordinates.substring(1, coordinates.length - 1).match(/[0-9]+/g)?.map(c => parseInt(c));
    if (!coords || coords.length < 2) {
        throw new vscode.FileSystemError(`Can not parse ${loc} as ISourceLocation due to incorrect coordinates`);
    }
    const coord = <IRascalCoordinates>{
        offsetLength: asNumberPair(coords.slice(0,2))
    };
    if (coords.length === 6) {
        coord.beginLineColumn = asNumberPair(coords.slice(2, 4));
        coord.endLineColumn = asNumberPair(coords.slice(4, 6));
    }
    else if (coords.length !== 2) {
        throw new vscode.FileSystemError(`Can not parse ${loc} as ISourceLocation due to incorrect coordinates (${coords})`);
    }
    return [base, coord];
}


/**
 * Rascal coordinates in unicode codepoints (so utf32/24)
 */
export interface IRascalCoordinates {
    offsetLength: [offset: number, length: number];
    beginLineColumn?: [line: number, column: number];
    endLineColumn?: [line: number, column: number];
}

