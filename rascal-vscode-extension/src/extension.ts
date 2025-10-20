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

import { RascalExtension } from './RascalExtension';
import { RascalMFValidator } from './ux/RascalMFValidator';
import { RascalProjectValidator } from './ux/RascalProjectValidator';

const testDeployMode = (process.env['RASCAL_LSP_DEV_DEPLOY'] || "false") === "true";
const deployMode = (process.env['RASCAL_LSP_DEV'] || "false") !== "true";


export function activate(context: vscode.ExtensionContext) {
    const jars = context.asAbsolutePath(path.join('.', 'assets', 'jars'));
    const icon = vscode.Uri.joinPath(context.extensionUri, "assets", "images", "rascal-logo-v2.1.svg");
    const extension = new RascalExtension(context, jars, icon, (deployMode || testDeployMode));
    context.subscriptions.push(extension);
    context.subscriptions.push(new RascalMFValidator());
    context.subscriptions.push(new RascalProjectValidator(extension.logger()));
    return extension.externalLanguageRegistry();
}


export function deactivate() {
    // no deactivation logic yet, since we push everything as a disposable
    // although maybe we should do something here with the closing of connections to the REPLs that are still running
}
