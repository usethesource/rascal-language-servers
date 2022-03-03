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
import { LanguageClient, Range, TextDocumentIdentifier } from 'vscode-languageclient/node';


/*
This module adds inlayHint support to rascal until it is supported in mainline vscode.

The code inspired by:
    - vscode-extension-samples (the decorator example) (MIT license)
    - vscode: vscode.proposed.inlayHints.d.ts (MIT license)
    - vscode: inlayHintsController.ts (MIT license)
    - rust-analyzer: Apache License
    - discussion in https://github.com/microsoft/language-server-protocol/issues/956
    - discussion in https://github.com/microsoft/language-server-protocol/pull/1249
*/

function buildThemeBlock(kind: string): vscode.ThemableDecorationAttachmentRenderOptions {
    return {
        color: new vscode.ThemeColor(`editorInlayHint.${kind}${kind ==="" ? "f": "F"}oreground`),
        backgroundColor: new vscode.ThemeColor(`editorInlayHint.${kind}${kind ==="" ? "b": "B"}ackground`),
        fontStyle: "normal",
        fontWeight: "normal",
    };

}

const inlayHintDecoratorStyle = {
    "type": vscode.window.createTextEditorDecorationType({
        before: buildThemeBlock("type"),
        after: buildThemeBlock("type"),
    }),
    "parameter": vscode.window.createTextEditorDecorationType({
        before: buildThemeBlock("parameter"),
        after: buildThemeBlock("parameter"),
    }),
    "other": vscode.window.createTextEditorDecorationType({
        before: buildThemeBlock(""),
        after: buildThemeBlock(""),
    })
}
;

export function addHintApi(client: LanguageClient, context: vscode.ExtensionContext, languageId : string) {
    // keep track of the current editor
    // to avoid calculating hints to editors that are not visible
    let activeEditor = vscode.window.activeTextEditor;
    vscode.window.onDidChangeActiveTextEditor(e => {
        activeEditor = e;
        setTimeout(trigger, 500);
    }, null, context.subscriptions);

    function trigger() {
        if (activeEditor && activeEditor.document.languageId === languageId) {
            triggerDecorations(activeEditor, client);
        }
    }

    let updater = setTimeout(trigger, 500);
    vscode.workspace.onDidChangeTextDocument(e => {
        // only trigger if the document is also the current visible one
        if (activeEditor && e.document === activeEditor.document) {
            // now we de-jitter it, so that only after 500ms of no changes do we
            // request for new decorations
            clearTimeout(updater);
            updater = setTimeout(trigger, 500);
        }
    }, null, context.subscriptions);
}

async function triggerDecorations(editor: vscode.TextEditor, client: LanguageClient) {
    await client.onReady();
    client.sendRequest<InlayHint[]>("rascal/provideInlayHints", <ProvideInlayHintParameter>{
        textDocument: client.code2ProtocolConverter.asTextDocumentIdentifier(editor.document),
    }).then(hints => {
        let params: vscode.DecorationOptions[] = [];
        let types: vscode.DecorationOptions[] = [];
        let others: vscode.DecorationOptions[] = [];
        if (hints) {
            hints.forEach(h => {
                const marginBefore = h.label.startsWith(" ") ? "0.25ex" : "0px";
                const marginAfter = h.label.endsWith(" ") ? "0.25ex" : "0px";
                const label = h.label.trim();
                const decl = <vscode.DecorationOptions> {
                    range: client.protocol2CodeConverter.asRange(h.range),
                    renderOptions: {
                        [h.before ? "before" : "after"]: {
                            // add non-joiner to prevent ligatures
                            contentText: h.before ? (label + "\u{200c}") : ("\u{200c}" + label),
                            margin: `0px ${marginAfter} 0px ${marginBefore}`
                        }
                    }
                };
                switch (h.category) {
                    case "parameter":
                        params.push(decl);
                        break;
                    case "type":
                        types.push(decl);
                        break;
                    default:
                        others.push(decl);
                        break;
                }
            });
        }
        editor.setDecorations(inlayHintDecoratorStyle.parameter, params);
        editor.setDecorations(inlayHintDecoratorStyle.type, types);
        editor.setDecorations(inlayHintDecoratorStyle.other, others);
    });
}


interface ProvideInlayHintParameter {
    textDocument: TextDocumentIdentifier;
    range: Range | undefined;
}

interface InlayHint {
    label: string,
    range: Range,
    category?: string;
    before: boolean;
}
