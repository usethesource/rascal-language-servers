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

import { EditorView, VSBrowser, WebDriver, Workbench } from 'vscode-extension-tester';
import { Delays, IDEOperations, RascalREPL, TestWorkspace, ignoreFails, printRascalOutputOnFailure } from './utils';

import * as fs from 'fs/promises';


describe('DSL', function () {
    let browser: VSBrowser;
    let driver: WebDriver;
    let bench: Workbench;
    let ide : IDEOperations;
    let picoFileBackup: Buffer;

    this.timeout(Delays.extremelySlow * 2);


    printRascalOutputOnFailure('Language Parametric Rascal Language Server');

    async function loadPico() {
        const repl = new RascalREPL(bench, driver);
        await repl.start();
        await repl.execute("import demo::lang::pico::LanguageServer;");
        repl.execute("main();"); // we don't wait, be cause we might miss pico loading window
        const ide = new IDEOperations(browser, bench);
        const isPicoLoading = ide.statusContains("Pico");
        await driver.wait(isPicoLoading, Delays.slow, "Pico DSL should start loading");
        await repl.terminate();
        // now wait for the Pico loader to dissapear
        await driver.wait(async () => !(await isPicoLoading()), Delays.extremelySlow, "Pico DSL should be finished starting");
    }


    before(async () => {
        browser = VSBrowser.instance;
        driver = browser.driver;
        bench = new Workbench();
        await ignoreFails(browser.waitForWorkbench());
        ide = new IDEOperations(browser, bench);
        await ide.load();
        await loadPico();
        picoFileBackup = await fs.readFile(TestWorkspace.picoFile);
        ide = new IDEOperations(browser, bench);
        await ide.load();
    });

    afterEach(async function () {
        if (this.test?.title) {
            await ide.screenshot("DSL-" + this.test?.title);
        }
        await ide.cleanup();
        await fs.writeFile(TestWorkspace.picoFile, picoFileBackup);
    });

    after(async function() {
    });

    it("have highlighting and parse errors", async function () {
        const editor = await ide.openModule(TestWorkspace.picoFile);
        await ide.hasSyntaxHighlighting(editor);
        try {
            await editor.setTextAtLine(10, "b := ;");
            await ide.hasErrorSquiggly(editor, 15_000);
        } finally {
            await ide.revertOpenChanges();
        }
    });

    it("have highlighting and parse errors for second extension", async function () {
        const editor = await ide.openModule(TestWorkspace.picoNewFile);
        await ide.hasSyntaxHighlighting(editor);
        try {
            await editor.setTextAtLine(10, "b := ;");
            await ide.hasErrorSquiggly(editor, 15_000);
        } finally {
            await ide.revertOpenChanges();
        }
    });

    it("have inlay hints", async function () {
        const editor = await ide.openModule(TestWorkspace.picoFile);
        await ide.hasSyntaxHighlighting(editor);
        await ide.hasInlayHint(editor);
    });

    it("change runs analyzer", async function () {
        const editor = await ide.openModule(TestWorkspace.picoFile);
        try {
            await editor.setTextAtLine(10, "bzzz := 3;");
            await ide.hasErrorSquiggly(editor, 15_000);
        } finally {
            await ide.revertOpenChanges();
        }
    });

    it("save runs builder", async function () {
        const editor = await ide.openModule(TestWorkspace.picoFile);
        const line10 = await editor.getTextAtLine(10);
        try {
            await editor.setTextAtLine(10, "bzzz := 3;");
            await editor.save();
            await ide.hasWarningSquiggly(editor, 15_000);
            await ide.hasErrorSquiggly(editor, 15_000);
        } finally {
            await editor.setTextAtLine(10, line10);
            await editor.save();
        }
    });

    it("go to definition works", async () => {
        const editor = await ide.openModule(TestWorkspace.picoFile);
        await ide.triggerTypeChecker(editor, {checkName: "Pico check"});
        await editor.selectText("x", 2);
        await bench.executeCommand("Go to Definition");
        await driver.wait(async ()=> (await editor.getCoordinates())[0] === 3, 15_000, "Cursor should have moved to line 3");
    });

    it("code lens works", async () => {
        const editor = await ide.openModule(TestWorkspace.picoFile);
        const lens = await driver.wait(async () => editor.getCodeLens("Rename variables a to b."), 10_000, "Rename lens should be available");
        await lens!.click();
        await driver.wait(async () => (await editor.getTextAtLine(9)).trim() === "b := 2;", 20_000, "a variable should be changed to b");
    });

    it("quick fix works", async() => {
        const editor = await ide.openModule(TestWorkspace.picoFile);
        await editor.setTextAtLine(9, "az := 2;");
        await editor.moveCursor(9,3);                   // it's where the undeclared variable `az` is
        await ide.hasErrorSquiggly(editor, Delays.verySlow);   // just make sure there is indeed something to fix
        const editorView = new EditorView();

        // find an editor action button by title
        const button = await editorView.getAction("Change to a");

        if (button) {
            await button.click();
            await driver.wait(async () => (await editor.getTextAtLine(9)).trim() === "a := 2;", Delays.extremelySlow, "a variable should be changed back to a");

            return true;
        }
        
        return false;
    });
    
});

