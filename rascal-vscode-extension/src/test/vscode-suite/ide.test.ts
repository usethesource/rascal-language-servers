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

import { expect } from 'chai';
import * as fs from 'fs/promises';
import * as path from 'path';
import { By, Key, TextEditor, ViewSection, VSBrowser, WebDriver, Workbench } from 'vscode-extension-tester';
import { Delays, IDEOperations, ignoreFails, printRascalOutputOnFailure, sleep, TestWorkspace } from './utils';
import * as os from 'os';


const protectFiles = [TestWorkspace.mainFile, TestWorkspace.libFile, TestWorkspace.libCallFile];

describe('IDE', function () {
    let browser: VSBrowser;
    let driver: WebDriver;
    let bench: Workbench;
    let ide: IDEOperations;
    const originalFiles = new Map<string, Buffer>();

    this.timeout(Delays.extremelySlow * 2);

    printRascalOutputOnFailure('Rascal MPL');

    before(async () => {
        browser = VSBrowser.instance;
        driver = browser.driver;
        bench = new Workbench();
        await browser.waitForWorkbench();
        ide = new IDEOperations(browser);
        await ide.load();
        // trigger rascal type checker to be sure
        for (const f of protectFiles) {
            originalFiles.set(f, await fs.readFile(f));
        }
        await makeSureRascalModulesAreLoaded();
    });

    beforeEach(async () => {
    });

    afterEach(async function () {
        if (this.test?.title) {
            await ide.screenshot("IDE-" + this.test?.title);
        }
        await ide.cleanup();
        for (const [f, b] of originalFiles) {
            await fs.writeFile(f, b);
        }
    });


    async function makeSureRascalModulesAreLoaded(delay = Delays.verySlow) {
        try {
            await ide.openModule(TestWorkspace.mainFile);
            let statusBarSeen = false;
            const checkRascalStatus = ide.statusContains("Loading Rascal");

            for (let tries = 0; tries < 10 && !statusBarSeen; tries++) {
                if (await checkRascalStatus()) {
                    statusBarSeen = true;
                    break;
                }
                await sleep(delay / 80);
            }

            if (statusBarSeen) {
                console.log("Waiting for startup of rascal core");
                for (let tries = 0; tries < 70; tries++) {
                    if (!await checkRascalStatus()) {
                        return;
                    }
                    await sleep(delay / 80);
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
            (await (await bench.getEditorView().getActiveTab())?.getTitle()) === title, timeout, message);
    }

    it("has syntax highlighting and parsing errors", async function () {
        const editor = await ide.openModule(TestWorkspace.mainFile);
        await ide.hasSyntaxHighlighting(editor);
        await editor.setTextAtLine(1, "this should not parse");
        await ide.hasErrorSquiggly(editor);
    });

    it("error recovery works", async function() {
        const editor = await ide.openModule(TestWorkspace.mainFile);
        await ide.hasSyntaxHighlighting(editor);
        // Introduce two parse errors
        await editor.setTextAtLine(2, "1 2 3");
        await editor.setTextAtLine(4, "1 2 3");
        await ide.hasRecoveredErrors(editor, 2, Delays.slow);
        await ide.hasSyntaxHighlighting(editor);
    });

    function triggerTypeChecker(editor: TextEditor, tplFile : string, waitForFinish = false) {
        return ide.triggerTypeChecker(editor, {tplFile : tplFile, waitForFinish: waitForFinish });
    }

    it("save runs type checker", async function () {
        const editor = await ide.openModule(TestWorkspace.mainFile);
        await triggerTypeChecker(editor, TestWorkspace.mainFileTpl, true);
    });

    it("type checker runs on dependencies", async() => {
        const editor = await ide.openModule(TestWorkspace.libCallFile);
        await triggerTypeChecker(editor, TestWorkspace.libFileTpl, true);
    });

    it("go to definition works", async () => {
        const editor = await ide.openModule(TestWorkspace.mainFile);
        await triggerTypeChecker(editor, TestWorkspace.mainFileTpl, true);
        await editor.selectText("println");
        await bench.executeCommand("Go to Definition");
        await waitForActiveEditor("IO.rsc", Delays.extremelySlow, "IO.rsc should be opened for println");

        await editor.selectText("&T", 0);
        const defLoc = await editor.getCoordinates();

        await editor.selectText("&T", 1);
        await bench.executeCommand("Go to Definition");
        const jumpLoc = await editor.getCoordinates();

        expect(jumpLoc).to.deep.equal(defLoc);
    });

    it("go to definition works across projects", async () => {
        // due to a current bug, we have to make sure that the lib in the other project is correctly resolved
        const libEditor = await ide.openModule(TestWorkspace.libFile);
        await triggerTypeChecker(libEditor, TestWorkspace.libFileTpl, true);
        await bench.getEditorView().closeAllEditors();

        const editor = await ide.openModule(TestWorkspace.libCallFile);
        await triggerTypeChecker(editor, TestWorkspace.libCallFileTpl, true);
        await editor.selectText("fib");
        await bench.executeCommand("Go to Definition");
        await waitForActiveEditor(path.basename(TestWorkspace.libFile), Delays.slow, "Lib.rsc should be opened for fib");
    });

    it("outline works", async () => {
        const editor = await ide.openModule(TestWorkspace.mainFile);
        await editor.moveCursor(1,1);
        const explorer = await (await bench.getActivityBar().getViewControl("Explorer"))!.openView();
        const outline = await driver.wait(() => explorer.getContent().getSection("Outline"), Delays.normal) as ViewSection;
        await outline.expand();
        const mainItem = await driver.wait(async() => ignoreFails(outline.findItem("main()", 0)), Delays.slow, "Main function should show in the outline");
        await driver.wait(async () => {
            await driver.actions().doubleClick(mainItem!).perform();
            return (await editor.getCoordinates())[0] === 5;
        }, Delays.normal, "Cursor should have moved to line that contains the println function");
    });

    it ("rename works", async() => {
        const editor = await ide.openModule(TestWorkspace.libFile);
        await editor.moveCursor(7, 15);

        // Before moving, check that Rascal is really loaded
        const checkRascalStatus = ide.statusContains("Loading Rascal");
        await driver.wait(async () => !(await checkRascalStatus()), Delays.extremelySlow, "Rascal evaluators have not finished loading");

        let renameSuccess = false;
        let tries = 0;
        while (!renameSuccess && tries < 5) {
            try {
                await bench.executeCommand("Rename Symbol");
                const renameBox = await ide.hasElement(editor, By.className("rename-input"), Delays.normal, "Rename box should appear");
                await renameBox.sendKeys(Key.BACK_SPACE, Key.BACK_SPACE, Key.BACK_SPACE, "i", Key.ENTER);
                renameSuccess = true;
            }
            catch (e) {
                console.log("Rename failed to succeed, lets try again");
                await ide.screenshot(`IDE-failed-rename-round-${tries}`);
                tries++;
            }
        }
        expect(renameSuccess, "We should have been able to trigger the rename box after 5 times");

        await driver.wait(() => (editor.isDirty()), Delays.extremelySlow, "Rename should have resulted in changes in the editor");

        const editorText = await editor.getText();
        expect(editorText).to.contain("int i");
        expect(editorText).to.contain("i < 2");
        expect(editorText).to.contain("i - 1");
        expect(editorText).to.contain("i -2");
    });

    it("renaming files works", async() => {
        const newDir = path.join(TestWorkspace.libProject, "src", "main", "rascal", "lib");
        await fs.mkdir(newDir, {recursive: true});

        const explorer = await (await bench.getActivityBar().getViewControl("Explorer"))!.openView();
        await bench.executeCommand("workbench.files.action.refreshFilesExplorer");
        const workspace = await explorer.getContent().getSection("test (Workspace)");
        await workspace.expand();

        // Open the lib file before moving it, so we have the editor ready to inspect afterwards
        const libFile = await ide.openModule(TestWorkspace.libFile);

        // Before moving, check that Rascal is really loaded
        const checkRascalStatus = ide.statusContains("Loading Rascal");
        await driver.wait(async () => !(await checkRascalStatus()), Delays.extremelySlow, "Rascal evaluators have not finished loading");

        // Move the file
        if (os.type() === "Darwin") {
            // Context menus are not supported for macOS:
            // https://github.com/redhat-developer/vscode-extension-tester/blob/main/KNOWN_ISSUES.md#macos-known-limitations-of-native-objects
            //
            // The following workaround triggers a move of `Lib.rsc` by cutting
            // and pasting that file using keyboard input. It works under the
            // assumption that `Lib.rsc` and `lib` are visible in the Explorer.
            // If this assumption breaks in the future, then see the
            // implementation of `DefaultTreeSection.findItem` for inspiration
            // on how to scroll the Explorer down:
            // https://github.com/redhat-developer/vscode-extension-tester/blob/1bd6c23b25673a76f4a9d139f4572c0ea6f55a7b/packages/page-objects/src/components/sidebar/tree/default/DefaultTreeSection.ts#L36-L59

            // Find the div that contains the whole visible tree in the Explorer
            const treeDiv = await workspace.findElement(By.className('monaco-list'));

            // Cut
            const libFileInTreeDiv = (await treeDiv.findElements(By.xpath(`.//div[@role='treeitem' and @aria-label='Lib.rsc']`)))[0];
            await libFileInTreeDiv?.click(); // Must click on this div instead of the object returned by `findItem`
            await treeDiv.sendKeys(Key.COMMAND, 'x', Key.COMMAND); // Only this div handles key events; not `libFileInTreeDiv`

            // Paste
            const libFolderInTreeDiv = (await treeDiv.findElements(By.xpath(`.//div[@role='treeitem' and @aria-label='lib']`)))[0];
            await libFolderInTreeDiv?.click(); // Must click on this div instead of the object returned by `findItem`
            await treeDiv.sendKeys(Key.COMMAND, 'v', Key.COMMAND); // Only this div handles key events; not `libFolderInTreeDiv`
        }

        else {
            // Context menus are supported for Windows and Linux
            const libFileInTree = await driver.wait(async() => workspace.findItem("Lib.rsc"), Delays.normal, "Cannot find Lib.rsc");
            const libFolderInTree = await driver.wait(async() => workspace.findItem("lib"), Delays.normal, "Cannot find lib folder");
            await (await libFileInTree!.openContextMenu()).select("Cut");
            await (await libFolderInTree!.openContextMenu()).select("Paste");
        }

        await driver.wait(async() => {
            const text = await libFile.getText();
            return text.indexOf("module lib::Lib") !== -1;
        }, Delays.extremelySlow, "Module name should have changed to `lib::Lib`", Delays.normal);

        const callFile = await ide.openModule(TestWorkspace.libCallFile);
        await driver.wait(async() => {
            const text = await callFile.getText();
            return text.indexOf("import lib::Lib") !== -1;
        }, Delays.extremelySlow, "Import should have changed to `lib::Lib`", Delays.normal);

        await fs.rm(newDir, {recursive: true, force: true});
    });

    it("code actions work", async() => {
        const editor = await ide.openModule(TestWorkspace.libCallFile);
        await editor.moveCursor(1,8); // in the module name

        try {
            await ide.triggerFirstCodeAction(editor, 'Add missing license header');
            await ide.assertLineBecomes(editor, 1, "@license{", "license header should have been added", Delays.extremelySlow);
        }
        finally {
            await ide.revertOpenChanges();
        }
    });

    it("editor contents used for open files", async() => {
        const importerEditor = await ide.openModule(TestWorkspace.importerFile);
        const importeeEditor = await ide.openModule(TestWorkspace.importeeFile);

        await importeeEditor.typeTextAt(3, 1, "public str foo;");
        await ide.openModule(TestWorkspace.importerFile);

        await ide.triggerTypeChecker(importerEditor, {waitForFinish : true});
        await ide.hasErrorSquiggly(importerEditor);
    });
});
