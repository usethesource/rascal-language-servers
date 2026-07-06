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
import { VSBrowser, WebDriver, Workbench } from 'vscode-extension-tester';
import { Delays, IDEOperations, printRascalOutputOnFailure, RascalREPL } from './utils';

describe('RemoteFS', function () {
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
            await ide.screenshot("RemoteFS-"+this.test?.title);
        }
        await bench.executeCommand("workbench.action.terminal.killAll");
        await ide.cleanup();
    });

    // VS Code file system in Rascal

    it("IO operations in Rascal REPL on VS Code file system", async () => {
        const repl = new RascalREPL(bench, driver);
        await repl.start();
        await repl.execute("import IO;");
        await repl.execute("l = |rascal-vscode-test:///test|;");
        await repl.execute('writeFile(l, "Hello world!")');
        await repl.execute("exists(l)");
        expect(repl.lastOutput).is.equal("bool: true", "Exists on VS Code fs works");
        await repl.execute('readFile(l) == "Hello world!"');
        expect(repl.lastOutput).is.equal("bool: true", "Writing + reading VS Code fs works");
    });

    it("Watch operations in Rascal REPL on VS Code file system", async () => {
        const repl = new RascalREPL(bench, driver);
        await repl.start();
        await repl.execute("import IO;");
        await repl.execute("l = |rascal-vscode-test:///remotefs-api-test/test-vscode-watch|;");
        await repl.execute('writeFile(l, "")');
        await repl.execute("i = 0;");
        await repl.execute("void inc(FileSystemChange _) { i = i + 1; }");
        await repl.execute("watch(l, false, inc)");
        await repl.execute('writeFile(l, "two")');
        await repl.execute("readFile(l)");
        await driver.wait(async () => {
            await repl.execute("i > 0");
            return repl.lastOutput === "bool: true";
        }, Delays.slow, "Callback counter not updated after write", Delays.fast);
        await repl.execute("unwatch(l, false, inc)");
        await repl.execute("i");
        let previousOutput = repl.lastOutput;
        await driver.wait(async () => {
            await repl.execute('writeFile(l, "three")');
            await repl.execute('i');
            if (repl.lastOutput !== previousOutput) {
                previousOutput = repl.lastOutput;
                return false;
            }
            return true;
        }, Delays.slow, "Callback counter changed after watch ended", Delays.fast);
    });

    // Rascal file system in VS Code
    it("Read from Rascal locations from VS Code", async () => {
        const repl = new RascalREPL(bench, driver);
        await repl.start();
        await repl.execute("import IO;");
        await repl.execute("l = |compressed+tmp:///rascal-remotefs-test/rascal-test-file.gz|;");
        await repl.execute('writeFile(l, "hi")');
        await repl.execute("l2 = |rascal-vscode-test:///remotefs-api-test/test-rascalfs-read|;");
        await repl.execute('readFile(l2) == "hi"');
        expect(repl.lastOutput).is.equal("bool: true", "Reading from Rascal fs works");
    });

    it("resolve from Rascal file system", async () => {
        const repl = new RascalREPL(bench, driver);
        await repl.start();
        await repl.execute(":edit IO", true, Delays.extremelySlow);

        await driver.wait(async () => await (await bench.getEditorView().getActiveTab())?.getTitle() === "IO.rsc", Delays.slow, "IO should be opened");
    });
});

