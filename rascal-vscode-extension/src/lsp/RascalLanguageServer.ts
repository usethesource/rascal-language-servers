/*
 * Copyright (c) 2018-2021, NWO-I CWI and Swat.engineering
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

import { LanguageClient } from 'vscode-languageclient/node';
import { VSCodeUriResolverServer } from '../fs/VSCodeURIResolver';
import { activateLanguageClient } from './RascalLSPConnection';
import { LanguageParameter, ParameterizedLanguageServer } from './ParameterizedLanguageServer';


export class RascalLanguageServer implements vscode.Disposable {
    public readonly rascalClient: Promise<LanguageClient>;

    constructor(
        context: vscode.ExtensionContext,
        vfsServer: VSCodeUriResolverServer,
        absoluteJarPath: string,
        dslLSP: ParameterizedLanguageServer,
        deployMode = true) {
        this.rascalClient = activateLanguageClient({
            deployMode: deployMode,
            devPort: 8888,
            isParametricServer: false,
            jarPath: absoluteJarPath,
            language: "rascalmpl",
            title: 'Rascal MPL Language Server',
            vfsServer: vfsServer,
            dedicated: false
        });

        this.rascalClient.then(client => {
            client.onNotification("rascal/receiveRegisterLanguage", (lang:LanguageParameter) => {
                dslLSP.registerLanguage(lang);
            });
            client.onNotification("rascal/receiveUnregisterLanguage", (lang:LanguageParameter) => {
                dslLSP.unregisterLanguage(lang);
            });
        });
    }
    dispose() {
        this.rascalClient.then(c => c.dispose());
    }

}

