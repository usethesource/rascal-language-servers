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

import { expect } from 'chai';
import { VSBrowser, WebDriver, Workbench } from 'vscode-extension-tester';
import { TestWorkspace, RascalREPL, Delays, IDEOperations, printRascalOutputOnFailure } from './utils';

describe('REPL', function () {
    let browser: VSBrowser;
    let driver: WebDriver;
    let bench: Workbench;
    let ide: IDEOperations;

    this.timeout(2 * Delays.extremelySlow);

    printRascalOutputOnFailure('Rascal MPL');

    before(async () => {
        browser = VSBrowser.instance;
        driver = browser.driver;
        bench = new Workbench();
        ide = new IDEOperations(browser);
        await ide.load();
        await ide.cleanup();
        await browser.waitForWorkbench();
    });

    afterEach(async function () {
        if (this.test?.title) {
            await ide.screenshot("REPL-"+this.test?.title);
        }
        await bench.executeCommand("workbench.action.terminal.killAll");
        await ide.cleanup();
    });

    it("should open without a project", async () => {
        await new RascalREPL(bench, driver).start();
    });

    it("run basic rascal commands", async () => {
        const repl = new RascalREPL(bench, driver);
        await repl.start();
        await repl.execute("40 + 2");
        expect(repl.lastOutput).matches(/int.*42/, "Result of expression should be 42");
        await repl.execute("import IO;");
        await repl.execute('println("Printing works: <1 + 3>");');
        expect(repl.lastOutput).is.equal("Printing works: 4\nok", "println works as expected");
    });

    it("import module and run in terminal", async () => {
        const editor = await ide.openModule(TestWorkspace.libCallFile);
        const lens = await ide.findCodeLens(editor, "Run in new Rascal terminal");
        await lens!.click();
        const repl = new RascalREPL(bench, driver);
        await repl.connect();
        expect(repl.lastOutput).is.equal("5\nint: 0");
    }).timeout(Delays.extremelySlow * 3);

    it("edit call module via repl", async() => {
        const repl = new RascalREPL(bench, driver);
        await repl.start();
        await repl.execute(":edit demo::lang::pico::LanguageServer");

        await driver.wait(async () => await (await bench.getEditorView().getActiveTab())?.getTitle() === "LanguageServer.rsc", Delays.normal, "LanguageServer should be opened");
    });
});
