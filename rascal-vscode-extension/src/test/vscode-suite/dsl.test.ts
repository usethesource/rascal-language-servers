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

import { VSBrowser, WebDriver, Workbench } from 'vscode-extension-tester';
import { IDEOperations, RascalREPL, TestWorkspace } from './utils';
import { expect } from 'chai';
import * as fs from 'fs/promises';


describe('DSL', function () {
    let browser: VSBrowser;
    let driver: WebDriver;
    let bench: Workbench;
    let ide : IDEOperations;
    let picoFileBackup: Buffer;

    this.timeout(120_000);


    async function loadPico() {
        const terminal = await bench.getBottomBar().openTerminalView();
        const repl = new RascalREPL(terminal, driver);
        await repl.start();
        await repl.execute("import demo::lang::pico::LanguageServer;");
        await repl.execute("main();");
        expect(repl.lastOutput).is.equal("ok");
        const statusBarCheck = driver.wait(ide.statusContains("Pico"), 20_000, "Pico DSL should start loading");
        await repl.terminate();
        await terminal.killTerminal(); // also kill the powershell one
        await statusBarCheck; // now we wait for pico to finish loading
    }


    before(async () => {
        browser = VSBrowser.instance;
        driver = browser.driver;
        bench = new Workbench();
        await browser.waitForWorkbench();
        ide = new IDEOperations(browser, bench);
        await ide.load();
        await loadPico();
        picoFileBackup = await fs.readFile(TestWorkspace.picoFile);
    });

    afterEach(async () => {
        await ide.cleanup();
        await fs.writeFile(TestWorkspace.picoFile, picoFileBackup);
    });

    after(async () => {
        // let's try and undo changes to the last line of certain files
        await ide.cleanupTypeCheckerChanges(TestWorkspace.picoFile);
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

    it("have inlay hints", async function () {
        const editor = await ide.openModule(TestWorkspace.picoFile);
        await ide.hasSyntaxHighlighting(editor);
        await ide.hasInlayHint(editor);
    });

    it("save runs type checker", async function () {
        const editor = await ide.openModule(TestWorkspace.picoFile);
        await ide.triggerTypeChecker(editor, {checkName: "Pico check", waitForFinish: true, timeout: 5_000});
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
});

