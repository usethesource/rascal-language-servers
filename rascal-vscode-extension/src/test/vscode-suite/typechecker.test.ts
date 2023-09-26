/*
 * Copyright (c) 2018-2023, NWO-I CWI and Swat.engineering
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

import { By, EditorView, TextEditor, VSBrowser, WebDriver, Workbench } from 'vscode-extension-tester';
import * as path from 'path';
import * as fs from 'fs/promises';


describe('typechecker', function () {
    let browser: VSBrowser;
    let driver: WebDriver;
    let bench: Workbench;
    let editorView : EditorView;

    this.timeout(40_000);
    const workspacePrefix = path.join('test-workspace');
    const mainFile = path.join(workspacePrefix, 'test-project', 'src', 'main', 'rascal', 'Main.rsc');
    const mainFileTpl = path.join(workspacePrefix, 'test-project', 'target', 'classes', 'rascal','Main.tpl');
    const libUse = path.join(workspacePrefix, 'test-project', 'src', 'main', 'rascal', 'LibCall.rsc');
    const libUseTpl = path.join(workspacePrefix, 'test-project', 'target', 'classes', 'rascal','LibCall.tpl');
    const libMain = path.join(workspacePrefix, 'test-lib', 'src', 'main', 'rascal', 'Lib.rsc');
    const libMainTpl = path.join(workspacePrefix, 'test-lib', 'target', 'classes', 'rascal','Lib.tpl');

    before(async () => {
        browser = VSBrowser.instance;
        driver = browser.driver;
        bench = new Workbench();
        await browser.waitForWorkbench();
        editorView = bench.getEditorView();
        await browser.openResources(path.join(workspacePrefix, "test.code-workspace"));
    });

    beforeEach(async () => {
    });

    afterEach(async () => {
        await editorView.closeAllEditors();
    });

    after(async () => {
        // let's try and undo changes to the last line of certain files
        await clearLastLine(mainFile);
        await clearLastLine(libUse);
        await clearLastLine(libMain);
    });

    async function clearLastLine(file: string) {
        await browser.openResources(file);
        const editor = await editorView.openEditor(path.basename(file)) as TextEditor;
        await editor.setTextAtLine(await editor.getNumberOfLines(), "");
        await editor.save();
        await editorView.closeAllEditors();
    }


    async function fileExists(file: string) {
        try {
            return await fs.stat(file) !== undefined;
        } catch(_ignored) {
            return false;
        }
    }

    function waitRascalCoreInit() {
        return driver.wait(async () => {
            for (const s  of await bench.getStatusBar().getItems()) {
                if ((await s.getText()).includes("Loading Rascal")) {
                    return false;
                }
            }
            return true;
        }, 20_000, "Rascal core loading took longer");
    }

    async function triggerTypeChecker(editor: TextEditor, outputFile: string, waitForFinish = false) {
        const lastLine = await editor.getNumberOfLines();
        try {
            await fs.unlink(outputFile);
        } catch (_e) { /* ignored */ }
        await editor.setTextAtLine(lastLine, await editor.getTextAtLine(lastLine) + " ");
        await editor.save();
        await driver.wait(async () => {
            return (await bench.getStatusBar().getItem("Rascal check")) ||
                (await fileExists(outputFile));
        }, 15_000, "Rascal check should be running after save");
        if (waitForFinish) {
            await driver.wait(async () => (await bench.getStatusBar().getItem("Rascal check")) === undefined && (await fileExists(outputFile)), 20_000, "Rascal check should finish processing Module");
        }
    }

    async function openModule(file: string) {
        await browser.openResources(file);
        return await editorView.openEditor(path.basename(file)) as TextEditor;
    }

    it("highlighting works", async function () {
        const editor = await openModule(mainFile);
        await driver.wait(async () => editor.findElement(By.className('mtk18')), 10_000, "Syntax highlighting should be present");
    });

    it("save runs type checker", async function () {
        const editor = await openModule(mainFile);
        await waitRascalCoreInit();
        await triggerTypeChecker(editor, mainFileTpl, true);
    });

    it("go to definition works", async () => {
        const editor = await openModule(mainFile);
        await waitRascalCoreInit();
        await triggerTypeChecker(editor, mainFileTpl);
        await editor.selectText("println");
        await bench.executeCommand("Go to Definition");
        await driver.wait(async () => (await (await editorView.getActiveTab())?.getTitle()) === "IO.rsc", 5_000, "IO.rsc should be opened for println");
    });

    it("go to definition works across projects", async () => {
        const libEditor = await openModule(libMain);
        await waitRascalCoreInit();
        await triggerTypeChecker(libEditor, libMainTpl, true);
        await editorView.closeAllEditors();

        const editor = await openModule(libUse);
        await waitRascalCoreInit();
        await triggerTypeChecker(editor, libUseTpl);
        await editor.selectText("fib");
        await bench.executeCommand("Go to Definition");
        await driver.wait(async () => (await (await editorView.getActiveTab())?.getTitle()) === "Lib.rsc", 5_000, "Lib.rsc should be opened for fib");
    });





});

