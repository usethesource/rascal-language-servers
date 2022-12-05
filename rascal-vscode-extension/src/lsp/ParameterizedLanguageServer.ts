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
import * as path from 'path';
import * as vscode from 'vscode';

import { LanguageClient } from 'vscode-languageclient/node';
import { activateLanguageClient } from './RascalLSPConnection';
import { VSCodeUriResolverServer } from '../fs/VSCodeURIResolver';

export class ParameterizedLanguageServer implements vscode.Disposable {
    private readonly registeredFileExtensions:Map<string, Set<string>> = new Map();
    private parametricClient: Promise<LanguageClient> | undefined = undefined;

    constructor(
        context: vscode.ExtensionContext,
        private readonly vfsServer: VSCodeUriResolverServer,
        private readonly absoluteJarPath: string,
        private readonly deployMode = true,
        private readonly languageId = 'parametric-rascalmpl',
        private readonly title = 'Language Parametric Rascal Language Server',
        private readonly dedicatedLanguage: LanguageParameter | undefined = undefined
        ) {
        if (dedicatedLanguage === undefined) {
            // if we are not a dedicated instance, we have to monitor files being opened up and assign our own language ID
            context.subscriptions.push(vscode.workspace.onDidOpenTextDocument(e => {
                const ext = path.extname(e.fileName);
                if (ext !== "" && e.languageId !== this.languageId && !e.isClosed && this.registeredFileExtensions.has(ext.substring(1))) {
                    // we delay setting the language, as sometimes VSCode quickly opens and closes the document
                    // also it looks like there is a bug where calling `setTextDocumentLanguage` closes and opens it, but at opening
                    // it's not applied yet.
                    // so we wait 1ms just to be sure we're not triggering an endless loop.
                    setTimeout(() => {
                        if (!e.isClosed && e.languageId !== this.languageId) {
                            vscode.languages.setTextDocumentLanguage(e, this.languageId);
                        }
                    }, 1);
                }
            }));
        }
        else {
            // trigger creating of dedicated instance
            this.getLanguageClient();
        }
    }
    dispose() {
        this.parametricClient?.then(c => c.dispose());
    }

    private activateParametricLanguageClient(): Promise<LanguageClient> {
        return activateLanguageClient({
            vfsServer: this.vfsServer,
            language: this.languageId,
            title: this.title,
            isParametricServer: true,
            jarPath: this.absoluteJarPath,
            deployMode: this.deployMode,
            devPort: 9999,
            dedicated: this.dedicatedLanguage !== undefined,
            lspArg: JSON.stringify(this.dedicatedLanguage)
        });
    }


    getLanguageClient(): Promise<LanguageClient> {
        if (!this.parametricClient) {
            this.parametricClient = this.activateParametricLanguageClient();
        }
        return this.parametricClient;
    }


    async registerLanguage(lang:LanguageParameter) {
        const client = this.getLanguageClient();
        // first we load the new language into the parametric server
        await (await client).sendRequest("rascal/sendRegisterLanguage", lang);

        if (this.dedicatedLanguage === undefined) {
            for (const editor of vscode.window.visibleTextEditors) {
                const ext = path.extname(editor.document.uri.path);
                if (ext !== "" && lang.extension === ext.substring(1)) {
                    vscode.languages.setTextDocumentLanguage(editor.document, this.languageId);
                }
            }

            if (lang.extension && lang.extension !== "") {
                let registries = this.registeredFileExtensions.get(lang.extension);
                if (!registries) {
                    registries = new Set();
                    this.registeredFileExtensions.set(lang.extension, registries);
                }
                registries.add(languageKey(lang));
            }
        }
    }

    async unregisterLanguage(lang: LanguageParameter) {
        const client = this.getLanguageClient();
        (await client).sendRequest("rascal/sendUnregisterLanguage", lang);

        if (this.dedicatedLanguage === undefined) {
            if (lang.extension && lang.extension !== "") {
                if (lang.mainModule && lang.mainFunction) {
                    // partial clear
                    const registries = this.registeredFileExtensions.get(lang.extension);
                    if (registries) {
                        registries.delete(languageKey(lang));
                        if (registries.size === 0) {
                            this.registeredFileExtensions.delete(lang.extension);
                        }
                    }
                }
                else {
                    // complete clear
                    this.registeredFileExtensions.delete(lang.extension);
                }
            }
        }
    }


}


export interface LanguageParameter {
    pathConfig: string 		// rascal pathConfig constructor as a string
    name: string; 			// name of the language
    extension:string; 		// extension for files in this language
    mainModule: string; 	// main module to locate mainFunction in
    mainFunction: string; 	// main function which contributes the language implementation
}

function languageKey(lang: LanguageParameter) {
    return `${lang.mainModule}::${lang.mainFunction}`;
}

