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
import { Delays, IDEOperations, ignoreFails, printRascalOutputOnFailure, RascalREPL, sleep, TestWorkspace } from './utils';

import * as fs from 'fs/promises';

describe('DSL [multi-language]', function () {
    let browser: VSBrowser;
    let driver: WebDriver;
    let bench: Workbench;
    let ide : IDEOperations;
    let picoFileBackup: Buffer;

    this.timeout(Delays.extremelySlow * 2);

    printRascalOutputOnFailure('Language Parametric Rascal');

    const picoSuffixes = ["1", "2"];

    async function loadPicos() {
        const repl = new RascalREPL(bench, driver);
        await repl.start();
        await repl.execute("import testing::lang::pico::LanguageServer;", false, Delays.extremelySlow);

        await sleep(Delays.normal);

        for (const suffix of picoSuffixes) {
            const replExecuteMain = repl.execute(`register(suffix = "${suffix}");`); // we don't wait yet, because we might miss pico loading window
            const ide = new IDEOperations(browser);
            const isPicoLoading = ide.statusContains("Pico" + suffix);
            await driver.wait(isPicoLoading, Delays.slow, "Pico DSL should start loading");
            // now wait for the Pico loader to disappear
            await driver.wait(async () => !(await isPicoLoading()), Delays.extremelySlow, "Pico DSL should be finished starting", 100);
            await replExecuteMain;
        }
        await repl.terminate();
    }

    before(async () => {
        browser = VSBrowser.instance;
        driver = browser.driver;
        bench = new Workbench();
        await ignoreFails(browser.waitForWorkbench());
        ide = new IDEOperations(browser);
        await ide.load();
        await loadPicos();
        picoFileBackup = await fs.readFile(TestWorkspace.picoFile);
        ide = new IDEOperations(browser);
        await ide.load();
    });

    after(async () => {
        const repl = new RascalREPL(bench, driver);
        await repl.start();
        await repl.execute("import util::LanguageServer;");
        // If Pico variants were registered before as part of another series of tests,
        // then it needs to be unregistered first. Until issue #630
        // is fixed (race between `unregister` and `register`), the
        // unregistration can't reliably be done as part of `main` (tried in
        // commit `a955a05`). Instead, it's done here and followed by a suitably
        // long sleep.
        for (const suffix of picoSuffixes) {
            await repl.execute(`unregisterLanguage("Pico${suffix}", {"pico${suffix}", "pico-new${suffix}"});`);
        }
        await sleep(Delays.normal);
        await repl.terminate();
    });

    beforeEach(async function () {
        if (this.test?.title) {
            await ide.screenshot(`DSL-mix-${this.test?.title}`);
        }
    });

    afterEach(async function () {
        if (this.test?.title) {
            await ide.screenshot(`DSL-mix-${this.test?.title}`);
        }
        await ide.cleanup();
        await fs.writeFile(TestWorkspace.picoFile, picoFileBackup);
    });

    it("reads unsaved editor contents across languages", async function() {
        const file1 = TestWorkspace.picoFile + "1";
        const file2 = TestWorkspace.picoFile + "2";

        await fs.copyFile(TestWorkspace.picoFile, file1);
        await fs.copyFile(TestWorkspace.picoFile, file2);

        const editor1 = await ide.openModule(file1);
        await editor1.setTextAtLine(1, "UNSAVED CHANGES");
        const editor2 = await ide.openModule(file2);
        await ide.clickCodeLens(editor2, "Copy contents of testing.pico1");
        await driver.wait(async() => (await editor2.getText()).includes("UNSAVED CHANGES"), Delays.normal, "Unsaved editor contents should be available across languages");
    });
});
