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
import * as assert from 'assert';

import * as path from 'path';
import * as vscode from 'vscode';
import * as rascalExtension from '../../extension';

const testFolderLocation = '/../../../src/test/test-workspace';

suite('Extension Test Suite', () => {
    vscode.window.showInformationMessage('Start all tests.');

    test('Never commit debug mode', () => {
        assert.strictEqual(rascalExtension.getRascalExtensionDeploymode(), true);
    });

    test('Open Rascal file', async () => {
      //This test has the side effect of activating the Rascal extension
      const file = vscode.Uri.file(path.join(__dirname + testFolderLocation + '/A.rsc'));
      const document = await vscode.workspace.openTextDocument(file);
      const editor = await vscode.window.showTextDocument(document);

      vscode.commands.executeCommand('workbench.action.closeActiveEditor');
    });

    test('Test RegisterLanguage', async () => {
        vscode.window.showInformationMessage('Starting Pico tests.');
        const pico = {pathConfig: 'pathConfig()', name: 'Pico', extension: 'pico', mainModule: 'demo::lang::pico::LanguageServer', mainFunction: 'picoLanguageContributor'};
        rascalExtension.registerLanguage(pico);

        const p1 = vscode.Uri.file(path.join(__dirname + testFolderLocation + '/P1.pico'));
        const document = await vscode.workspace.openTextDocument(p1);
        const editor = await vscode.window.showTextDocument(document);

        vscode.commands.executeCommand('workbench.action.closeActiveEditor');
    });
});
