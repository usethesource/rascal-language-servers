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

import { VSBrowser, WebDriver, Workbench } from 'vscode-extension-tester';
import { Delays, IDEOperations, RascalREPL, TestWorkspace, ignoreFails, printRascalOutputOnFailure } from './utils';

import * as fs from 'fs/promises';
import { Suite } from 'mocha';

function parameterizedDescribe(body: (this: Suite, errorRecovery: boolean) => void) {
    describe('DSL', function() { body.apply(this, [false]); });
    describe('DSL+recovery', function() { body.apply(this, [true]); });
}



parameterizedDescribe(function (errorRecovery: boolean) {
    let browser: VSBrowser;
    let driver: WebDriver;
    let bench: Workbench;
    let ide : IDEOperations;
    let picoFileBackup: Buffer;

    this.timeout(Delays.extremelySlow * 2);

    printRascalOutputOnFailure('Language Parametric Rascal');

    async function loadPico() {
        const repl = new RascalREPL(bench, driver);
        await repl.start();
        await repl.execute("import demo::lang::pico::OldStyleLanguageServer;");
        const replExecuteMain = repl.execute(`main(errorRecovery=${errorRecovery});`); // we don't wait yet, because we might miss pico loading window
        const ide = new IDEOperations(browser);
        const isPicoLoading = ide.statusContains("Pico");
        await driver.wait(isPicoLoading, Delays.slow, "Pico DSL should start loading");
        // now wait for the Pico loader to disappear
        await driver.wait(async () => !(await isPicoLoading()), Delays.extremelySlow, "Pico DSL should be finished starting", 100);
        await replExecuteMain;
        await repl.terminate();
    }


    before(async () => {
        browser = VSBrowser.instance;
        driver = browser.driver;
        bench = new Workbench();
        await ignoreFails(browser.waitForWorkbench());
        ide = new IDEOperations(browser);
        await ide.load();
        await loadPico();
        picoFileBackup = await fs.readFile(TestWorkspace.picoFile);
        ide = new IDEOperations(browser);
        await ide.load();
    });

    beforeEach(async function () {
        if (this.test?.title) {
            await ide.screenshot(`DSL-${errorRecovery}-` + this.test?.title);
        }
    });

    afterEach(async function () {
        if (this.test?.title) {
            await ide.screenshot(`DSL-${errorRecovery}-`+ this.test?.title);
        }
        await ide.cleanup();
        await fs.writeFile(TestWorkspace.picoFile, picoFileBackup);
    });

    it("have highlighting and parse errors", async function () {
        const editor = await ide.openModule(TestWorkspace.picoFile);
        await ide.hasSyntaxHighlighting(editor);
        try {
            await editor.setTextAtLine(10, "b := ;");
            await ide.hasErrorSquiggly(editor, Delays.slow);
        } catch (e) {
            console.log(`Failed to trigger parse error: ${e}`);
            if (e instanceof Error) {
                console.log(e.stack);
            }
        } finally {
            await ide.revertOpenChanges();
        }
    });

    it("have highlighting and parse errors for second extension", async function () {
        const editor = await ide.openModule(TestWorkspace.picoNewFile);
        await ide.hasSyntaxHighlighting(editor);
        try {
            await editor.setTextAtLine(1, "");
            await ide.hasErrorSquiggly(editor, Delays.slow);
        } finally {
            await ide.revertOpenChanges();
        }
    });

    it("error recovery works", async function () {
        if (!errorRecovery) { this.skip(); }
        const editor = await ide.openModule(TestWorkspace.picoNewFile);
        await ide.hasSyntaxHighlighting(editor);
        try {
            // Introduce two parse errors
            await editor.setTextAtLine(4, "n : x natural");
            await editor.setTextAtLine(9, "     a := x 2;");
            await ide.hasRecoveredErrors(editor, 2, Delays.slow);
            await ide.hasSyntaxHighlighting(editor);
        } finally {
            await ide.revertOpenChanges();
        }
    });

    it.only("have inlay hints", async function () {
        if (errorRecovery) { this.skip(); }
        const editor = await ide.openModule(TestWorkspace.picoFile);
        await ide.hasSyntaxHighlighting(editor);
        await ide.hasInlayHint(editor);
    });

    it("change runs analyzer", async function () {
        if (errorRecovery) { this.skip(); }
        const editor = await ide.openModule(TestWorkspace.picoFile);
        try {
            await editor.setTextAtLine(10, "bzzz := 3;");
            await ide.hasErrorSquiggly(editor, Delays.slow);
        } finally {
            await ide.revertOpenChanges();
        }
    });

    it("save runs builder", async function () {
        if (errorRecovery) { this.skip(); }
        const editor = await ide.openModule(TestWorkspace.picoFile);
        const line10 = await editor.getTextAtLine(10);
        try {
            await editor.setTextAtLine(10, "bzzz := 3;");
            await editor.save();
            await ide.hasWarningSquiggly(editor, Delays.slow);
            await ide.hasErrorSquiggly(editor, Delays.slow);
        } finally {
            await editor.setTextAtLine(10, line10);
            await editor.save();
        }
    });

    it("go to definition works", async function() {
        if (errorRecovery) { this.skip(); }
        const editor = await ide.openModule(TestWorkspace.picoFile);
        await ide.triggerTypeChecker(editor, {checkName: "Pico check"});
        await editor.selectText("x", 2);
        await bench.executeCommand("Go to Definition");
        await driver.wait(async ()=> (await editor.getCoordinates())[0] === 3, Delays.slow, "Cursor should have moved to line 3");
    });

    it("code lens works", async function() {
        if (errorRecovery) { this.skip(); }
        const editor = await ide.openModule(TestWorkspace.picoFile);
        const lens = await driver.wait(() => editor.getCodeLens("Rename variables a to b."), Delays.verySlow, "Rename lens should be available");
        await lens!.click();
        await ide.assertLineBecomes(editor, 9, "b := 2;", "a variable should be changed to b");
    });

    it("quick fix works", async function() {
        if (errorRecovery) { this.skip(); }
        const editor = await ide.openModule(TestWorkspace.picoFile);
        await editor.setTextAtLine(9, "  az := 2;");
        await editor.moveCursor(9,3);                   // it's where the undeclared variable `az` is
        await ide.hasErrorSquiggly(editor, Delays.verySlow);   // just make sure there is indeed something to fix

        try {
            await ide.triggerFirstCodeAction(editor, 'Change to a');
            await ide.assertLineBecomes(editor, 9, "a := 2;", "a variable should be changed back to a", Delays.extremelySlow);
        }
        finally {
            await ide.revertOpenChanges();
        }
    });

});
