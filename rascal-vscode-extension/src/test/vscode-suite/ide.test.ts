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

import { By, DefaultTreeSection, EditorView, SideBarView, TextEditor, VSBrowser, ViewSection, WebDriver, Workbench, until } from 'vscode-extension-tester';
import * as path from 'path';
import * as fs from 'fs/promises';
import { IDEOperations, TestWorkspace, sleep } from './utils';


const protectFiles = [TestWorkspace.mainFile, TestWorkspace.libFile, TestWorkspace.libCallFile];

describe('IDE', function () {
    let browser: VSBrowser;
    let driver: WebDriver;
    let bench: Workbench;
    let editorView : EditorView;
    let ide: IDEOperations;
    const originalFiles = new Map<string, Buffer>();

    this.timeout(100_000);

    before(async () => {
        browser = VSBrowser.instance;
        driver = browser.driver;
        bench = new Workbench();
        await browser.waitForWorkbench();
        editorView = bench.getEditorView();
        ide = new IDEOperations(browser, bench);
        await ide.load();
        // trigger rascal type checker to be sure
        for (const f of protectFiles) {
            originalFiles.set(f, await fs.readFile(f));
        }
        await makeSureRascalModulesAreLoaded();
    });

    beforeEach(async () => {
    });

    afterEach(async () => {
        await ide.cleanup();
        for (const [f, b] of originalFiles) {
            await fs.writeFile(f, b);
        }
    });

    async function makeSureRascalModulesAreLoaded(delay = 40_000) {
        try {
            await ide.openModule(TestWorkspace.mainFile);
            let statusBarSeen = false;
            const checkRascalStatus = ide.statusContains("Loading Rascal");

            for (let tries = 0; tries < 10 && !statusBarSeen; tries++) {
                await sleep(delay / 80);
                if (await checkRascalStatus()) {
                    statusBarSeen = true;
                    break;
                }
            }

            if (statusBarSeen) {
                console.log("Waiting for startup of rascal core");
                for (let tries = 0; tries < 70; tries++) {
                    await sleep(delay / 80);
                    if (!await checkRascalStatus()) {
                        return;
                    }
                }
                console.log("*** warning, loading rascal-core is still running, but we will continue anyway");
            }
        }
        finally {
            await ide.cleanup();
        }
    }

    function waitForActiveEditor(title: string, timeout: number, message: string) {
        return driver.wait(async () =>
            (await (await editorView.getActiveTab())?.getTitle()) === title, timeout, message);
    }

    it("has syntax highlighting and parsing errors", async function () {
        const editor = await ide.openModule(TestWorkspace.mainFile);
        await ide.hasSyntaxHighlighting(editor);
        await editor.setTextAtLine(1, "this should not parse");
        await ide.hasErrorSquiggly(editor);
    });

    function triggerTypeChecker(editor: TextEditor, tplFile : string, waitForFinish = false) {
        return ide.triggerTypeChecker(editor, {tplFile : tplFile, waitForFinish: waitForFinish });
    }

    it("save runs type checker", async function () {
        const editor = await ide.openModule(TestWorkspace.mainFile);
        await triggerTypeChecker(editor, TestWorkspace.mainFileTpl, true);
    });

    it("go to definition works", async () => {
        const editor = await ide.openModule(TestWorkspace.mainFile);
        await triggerTypeChecker(editor, TestWorkspace.mainFileTpl, true);
        await editor.selectText("println");
        await bench.executeCommand("Go to Definition");
        await waitForActiveEditor("IO.rsc", 15_000, "IO.rsc should be opened for println");
    });

    it("go to definition works across projects", async () => {
        // due to a current bug, we have to make sure that the lib in the other project is correctly resolved
        const libEditor = await ide.openModule(TestWorkspace.libFile);
        await triggerTypeChecker(libEditor, TestWorkspace.libFileTpl, true);
        await editorView.closeAllEditors();

        const editor = await ide.openModule(TestWorkspace.libCallFile);
        await triggerTypeChecker(editor, TestWorkspace.libCallFileTpl, true);
        await editor.selectText("fib");
        await bench.executeCommand("Go to Definition");
        await waitForActiveEditor(path.basename(TestWorkspace.libFile), 5_000, "Lib.rsc should be opened for fib");
    });

    it("outline works", async () => {
        const editor = await ide.openModule(TestWorkspace.mainFile);
        await editor.moveCursor(1,1);
        const explorer = await (await (await bench.getActivityBar()).getViewControl("Explorer"))!.openView();
        await sleep(1000);
        const outline = await explorer.getContent().getSection("Outline") as ViewSection;
        await outline.expand();
        const mainItem = await driver.wait(async() => {
            try {
                return outline.findItem("main()", 0);
                //return await outline.findElement(By.partialLinkText("main()"));
            } catch (_ignored) { return undefined; }
        }, 10_000, "Main function should show in the outline");

        await driver.actions().doubleClick(mainItem!).perform();
        //await mainItem!.click();
        await driver.wait(async ()=> (await editor.getCoordinates())[0] === 5, 15_000, "Cursor should have moved to line 6 that contains the println function");
    });
});

