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
import { TextEditor, VSBrowser, WebDriver, WebView, Workbench } from 'vscode-extension-tester';
import { Delays, IDEOperations, ignoreFails, printRascalOutputOnFailure, RascalREPL, TestWorkspace } from './utils';

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
    }).retries(2);

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

        await ide.clickCodeLens(editor, "Run in new Rascal terminal");
        const repl = new RascalREPL(bench, driver);
        await repl.connect();
        expect(repl.lastOutput).is.equal("5\nint: 0");
    }).timeout(Delays.extremelySlow * 3);

    it("open module editor via repl", async() => {
        const repl = new RascalREPL(bench, driver);
        await repl.start();
        await repl.execute(":edit demo::lang::pico::LanguageServer", true, Delays.extremelySlow);

        await driver.wait(async () => await (await bench.getEditorView().getActiveTab())?.getTitle() === "LanguageServer.rsc", Delays.slow, "LanguageServer should be opened");
    });

    it("open stdlib module editor via repl", async() => {
        const repl = new RascalREPL(bench, driver);
        await repl.start();
        await repl.execute(":edit IO", true, Delays.extremelySlow);

        await driver.wait(async () => await (await bench.getEditorView().getActiveTab())?.getTitle() === "IO.rsc", Delays.slow, "IO should be opened");
    });

    it("VFS works", async() => {
        const repl = new RascalREPL(bench, driver);
        await repl.start();
        const baseLoc = '|rascal-vscode-test:///';
        await repl.execute('import IO;');
        await repl.execute(`writeFile(${baseLoc}test.txt|, "Hello World")`);
        expect(repl.lastOutput).contains('ok', 'Write file should succeed');
        await repl.execute(`${baseLoc}|.ls`);
        expect(repl.lastOutput).contains('test.txt', 'File entry should be there');
        await repl.execute(`readFile(${baseLoc}test.txt|)`);
        expect(repl.lastOutput).contains('Hello World', 'File contents should be there');
    });

    async function runIdeService(command: (file: string) => string): Promise<RascalREPL>;
    async function runIdeService(command: (file: string) => string, file: string): Promise<RascalREPL>;
    async function runIdeService(command: (file: string) => string, file?: string): Promise<RascalREPL> {
        const repl = new RascalREPL(bench, driver);
        await repl.start();
        await repl.execute("import util::IDEServices;");
        if (file) {
            await repl.execute(`loc f = |${file}|;`);
            await repl.execute(command("f"));
        } else {
            await repl.execute(command(""));
        }
        return repl;
    }

    it("browses interactively", async() => {
        await runIdeService(() => 'browse(|https://www.rascal-mpl.org|, title="Rascal MPL");');
        await driver.wait(async () => {
            const view = new WebView();
            return await ignoreFails(view.getTitle()) === "Rascal MPL";
        }, Delays.normal, "Browser for rascal-mpl.org should open");
    });

    it("opens editors", async() => {
        await runIdeService(f => `edit(${f});`, "project://test-project/src/main/pico/testing.pico");
        await driver.wait(async () => {
            const editor = new TextEditor();
            return "testing.pico" === await editor.getTitle();
        }, Delays.normal, "Another editor should open");
    });

    function checkMessageOutput(repl: RascalREPL, uri: string, message: string, severity: string) {
        expect(repl.lastOutput).to.equal(`[${severity.toUpperCase()}] |${uri}|: ${message}\nok`);
    }

    it("(un)registers diagnostics", async() => {
        const file = "project://test-project/src/main/pico/testing.pico";
        const repl = await runIdeService(f => `registerDiagnostics([info("TODO", ${f})]);`, file);
        checkMessageOutput(repl, file, "TODO", "info");

        await repl.execute(`unregisterDiagnostics([|${file}|]);`);
    });

    it("shows messages", async() => {
        const file = "project://test-project/src/main/pico/testing.pico";
        const repl = await runIdeService(f => `showMessage(warning("Test warning", ${f}));`, file);
        checkMessageOutput(repl, file, "Test warning", "warning");
    });

    it("logs messages", async() => {
        const file = "project://test-project/src/main/pico/testing.pico";
        const repl = await runIdeService(f => `logMessage(error("Test warning", ${f}));`, file);
        checkMessageOutput(repl, file, "Test warning", "error");
    });

    it("shows interactive content", async function() {
        await runIdeService(() => 'showInteractiveContent(plainText("Some text"));');
        await driver.wait(async () => {
            return "*static content*" === await (await bench.getEditorView().getActiveTab())?.getTitle();
        }, Delays.normal, "Static content should be shown");
    });

});
