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

// import the webdriver and the high level browser wrapper
import { By, EditorView, TextEditor, VSBrowser, WebDriver, Workbench } from 'vscode-extension-tester';
import * as path from 'path';


describe('typechecker', function () {
    let browser: VSBrowser;
    let driver: WebDriver;
    let bench: Workbench;
    let editorView : EditorView;

    this.timeout(40_000);
    const workspacePrefix = path.join('test-workspace', 'test-project');
    const mainFile = path.join(workspacePrefix, 'src', 'main', 'rascal', 'Main.rsc');

    before(async () => {
        browser = VSBrowser.instance;
        driver = browser.driver;
        bench = new Workbench();
        await browser.waitForWorkbench();
        editorView = bench.getEditorView();
        await browser.openResources(workspacePrefix);
    });

    beforeEach(async () => {
    });

    afterEach(async () => {
        await editorView.closeAllEditors();
    });

    after(async () => {
        // let's try and undo changes to the last line of certain files
        await clearLastLine(mainFile, 'Main.rsc');
    });

    async function clearLastLine(path: string, name: string) {
        await browser.openResources(path);
        const editor = await editorView.openEditor(name) as TextEditor;
        await editor.setTextAtLine(await editor.getNumberOfLines(), "");
        await editor.save();
        await editorView.closeAllEditors();
    }


    it("highlighting works", async function () {
        await browser.openResources(mainFile);
        const editor = await editorView.openEditor('Main.rsc') as TextEditor;
        await driver.wait(async () => editor.findElement(By.className('mtk18')), 10_000, "Syntax highlighting should be present");
    });

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

    async function triggerTypeChecker(editor: TextEditor) {
        const lastLine = await editor.getNumberOfLines();
        await editor.setTextAtLine(lastLine, await editor.getTextAtLine(lastLine) + " ");
        await editor.save();
        await driver.wait(async () => bench.getStatusBar().getItem("Rascal check"), 15_000, "Rascal check should be running after save");
    }

    async function openMainModule() {
        await browser.openResources(mainFile);
        return await editorView.openEditor('Main.rsc') as TextEditor;
    }

    it("save runs type checker", async function () {
        const editor = await openMainModule();
        await waitRascalCoreInit();
        await triggerTypeChecker(editor);
        await driver.wait(async () => (await bench.getStatusBar().getItem("Rascal check")) === undefined, 20_000, "Rascal check should finish processing Module");
    });

    it("go to definition works", async () => {
        const editor = await openMainModule();
        await waitRascalCoreInit();
        await triggerTypeChecker(editor);
        await editor.selectText("println");
        await bench.executeCommand("Go to Definition");
        await driver.wait(async () => (await (await editorView.getActiveTab())?.getTitle()) === "IO.rsc", 5_000, "IO.rsc should be opened for println");
    });


});

