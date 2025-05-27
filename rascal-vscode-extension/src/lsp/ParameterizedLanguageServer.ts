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
import * as path from 'path';
import * as vscode from 'vscode';

import { BaseLanguageClient } from 'vscode-languageclient';
import { activateLanguageClient } from './RascalLSPConnection';
import { VSCodeUriResolverServer } from '../fs/VSCodeURIResolver';

export class ParameterizedLanguageServer implements vscode.Disposable {
    private readonly registeredFileExtensions:Map<string, Set<string>> = new Map();
    private parametricClient: Promise<BaseLanguageClient> | undefined = undefined;

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

    private activateParametricLanguageClient(): Promise<BaseLanguageClient> {
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


    getLanguageClient(): Promise<BaseLanguageClient> {
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
                if (ext !== "" && lang.extensions.includes(ext.substring(1))) {
                    vscode.languages.setTextDocumentLanguage(editor.document, this.languageId);
                }
            }

            for (const ext of lang.extensions) {
                let registries = this.registeredFileExtensions.get(ext);
                if (!registries) {
                    registries = new Set();
                    this.registeredFileExtensions.set(ext, registries);
                }
                registries.add(languageKey(lang));
            }
        }
    }

    async unregisterLanguage(lang: LanguageParameter) {
        const client = this.getLanguageClient();
        (await client).sendRequest("rascal/sendUnregisterLanguage", lang);

        if (this.dedicatedLanguage === undefined) {
            for (const ext of lang.extensions) {
                if (lang.mainModule && lang.mainFunction) {
                    // partial clear
                    const registries = this.registeredFileExtensions.get(ext);
                    if (registries) {
                        registries.delete(languageKey(lang));
                        if (registries.size === 0) {
                            this.registeredFileExtensions.delete(ext);
                        }
                    }
                }
                else {
                    // complete clear
                    this.registeredFileExtensions.delete(ext);
                }
            }
        }
    }


}

/**
 * Define a DSL that the DSL multiplexer has to load
 */
export interface LanguageParameter {
    /** rascal pathConfig constructor as a string */
    pathConfig: string
    /** name of the language */
    name: string;
    /** extensions for files in this language */
    extensions: string[]
    /** main module to locate mainFunction in */
    mainModule: string;
    /** main function which contributes the language implementation */
    mainFunction: string;
    /**
     * optionally configure a precompiled parser function
     *
     * warning: this feature is temporary, make sure to always define a regular parser
     * such that when we drop support for this feature, your DSLs will continue to work.
     * */
    precompiledParser?: ParserSpecification;
}

/**
 * Define precompiled parser to load before the modules have loaded.
 * This helps reduce the time an IDE is shown without syntax highlighting
 *
* warning: this feature is temporary, make sure to always define a regular parser
* such that when we drop support for this feature, your DSLs will continue to work.
 */
export interface ParserSpecification {
    /** a rascal source location (for example |jar+file:///....!/lang.parser|) pointing to the file that contains the precompiled parsers */
    parserLocation: string;
    /** non-terminal to use from the defined parsers */
    nonTerminalName: string;
    /** is the non-terminal a `start` non-terminal, default: true */
    nonTerminalIsStart?: boolean;
    /** allow ambiguities during parsing, default: false */
    allowAmbiguity?: boolean;
    /** limit the maximum nesting depth of ambiguities, ambiguities will be pruned below this level. Only of interest if allowAmbiguity or allowRecovery are set to true. default: 2 */
    maxAmbDepth?: number;
    /** Allow error recovery during parsing resulting in error trees. default: false */
    allowRecovery?: boolean;
    /** The maximum number of recovery attempts made during a single parse when error recovery is enabled. default: 50 */
    maxRecoveryAttempts?: number;
    /** The maximum number of tokens used in each recovery attempt. default: 3 */
    maxRecoveryTokens?: number;
    /** apply the special case for highlighting syntax-in-syntax, default: true
        Note: This is a temporary property. In a short-term future release, the
        default will become `false`. In a mid-term future release, the property
        will be removed (and the special case no longer exists). */
    specialCaseHighlighting?: boolean;
}

function languageKey(lang: LanguageParameter) {
    return `${lang.mainModule}::${lang.mainFunction}`;
}

