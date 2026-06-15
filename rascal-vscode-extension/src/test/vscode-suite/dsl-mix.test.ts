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
import * as fs from 'fs/promises';
import { TextEditor, VSBrowser, WebDriver, Workbench } from 'vscode-extension-tester';
import { Delays, IDEOperations, ignoreFails, isLanguageLoading, printRascalOutputOnFailure, RascalREPL, sleep, src, TestWorkspace } from './utils';

import path from 'path';

describe('DSL [multi-language]', function () {
    let browser: VSBrowser;
    let driver: WebDriver;
    let bench: Workbench;
    let ide : IDEOperations;

    const languages = ["Pico", "JSON2"];

    this.timeout(Delays.extremelySlow * 2);

    printRascalOutputOnFailure('Language Parametric Rascal');

    async function loadLanguages() {
        const repl = new RascalREPL(bench, driver);
        await repl.start();

        for (const lang of languages) {
            await repl.execute(`import testing::lang::${lang.toLowerCase()}::LanguageServer;`, false, Delays.extremelySlow);
            const replExecuteMain = await repl.execute(`testing::lang::${lang.toLowerCase()}::LanguageServer::register();`, false); // we don't wait yet, because we might miss language loading window
            const isLoading = isLanguageLoading(bench, lang);
            await driver.wait(isLoading, Delays.extremelySlow, `${lang} should start loading`);
            // now wait for the loader to disappear
            await driver.wait(async () => !(await isLoading()), Delays.extremelySlow, `${lang} should be finished starting`, 100);
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
        await loadLanguages();
        ide = new IDEOperations(browser);
        await ide.load();
    });

    after(async () => {
        const repl = new RascalREPL(bench, driver);
        await repl.start();
        await repl.execute("import util::LanguageServer;");
        // Until issue #630 is fixed (race between `unregister` and `register`), the
        // unregistration can't reliably be done as part of `main` (tried in
        // commit `a955a05`). Instead, it's done here and followed by a suitably
        // long sleep.
        for (const lang of languages) {
            await repl.execute(`unregisterLanguage("${lang}", {"${lang.toLowerCase()}"});`);
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
        await ide.revertOpenChanges();

        if (this.test?.title) {
            await ide.screenshot(`DSL-mix-${this.test?.title}`);
        }
        await ide.cleanup();
    });

    it("reads unsaved editor contents across languages", async function() {
        const exampleDir = src(TestWorkspace.testProject, 'json2');
        const targetFile = path.join(exampleDir, "example-copy.json2");

        const editor1 = await ide.openModule(path.join(exampleDir, 'example.json2'));
        try {
            await editor1.setTextAtLine(6, '}, "key5": "unsaved"');
            await fs.writeFile(targetFile, "{}");
            const editor2 = await ide.openModule(TestWorkspace.picoFile);
            await ide.clickCodeLens(editor2, "Copy contents of example.json2");
            await driver.wait(async() => {
                const editorView = bench.getEditorView();
                const editor = await ignoreFails(editorView.openEditor("example-copy.json2")) as TextEditor | undefined;
                return (await editor?.getText())?.includes("unsaved");
            }, Delays.normal, "Unsaved editor contents should be available across languages");
        } finally {
            await ide.revertOpenChanges();
            await fs.unlink(targetFile);
        }
    });
});
