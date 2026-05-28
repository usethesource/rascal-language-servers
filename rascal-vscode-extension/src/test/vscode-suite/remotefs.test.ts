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
        await repl.execute("writeFile(l, \"Hello world!\")");
        await repl.execute("exists(l)");
        expect(repl.lastOutput).is.equal("bool: true", "Exists on VS Code fs works");
        await repl.execute("readFile(l)");
        expect(repl.lastOutput).is.equal(`str: "Hello world!"
───
Hello world!
───`, "Writing + reading VS Code fs works");
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
            await repl.execute("i");
            return repl.lastOutput === "int: 1";
        }, Delays.slow, "Callback counter not updated after write", Delays.fast);
        await repl.execute("unwatch(l, false, inc)");
        await repl.execute("writeFile(l, \"three\")");
        await repl.execute("i");
        expect(repl.lastOutput).is.equal("int: 1");
    });

    // Rascal file system in VS Code

    it("Watch operations in VS Code on Rascal file system", async () => {
        const repl = new RascalREPL(bench, driver);
        await repl.start();
        await repl.execute("import IO;");
        await repl.execute("l = |tmp:///rascal-remotefs-test/rascalfs-watch-test|;");
        await repl.execute("testRoot = |rascal-vscode-test:///remotefs-api-test/|;");
        await repl.execute('counterFile = testRoot + "test-rascalfs-counter";');
        await repl.execute('writeFile(l, "")');
        await repl.execute('readFile(testRoot + "test-rascalfs-initiate-watch")');
        await repl.execute('readFile(counterFile) == "0"');
        expect(repl.lastOutput).is.equal("bool: true", "Callback counter not at 0");

        await repl.execute('writeFile(l, "aa")');
        await driver.wait(async () => {
            await repl.execute('readFile(counterFile) == "1"');
            return repl.lastOutput === "bool: true";
        }, Delays.slow, "Callback counter not at 1", Delays.fast);

        await repl.execute('writeFile(l, "bb")');
        await driver.wait(async () => {
            await repl.execute('readFile(counterFile) == "2"');
            return repl.lastOutput === "bool: true";
        }, Delays.slow, "Callback counter not at 2", Delays.fast);

        await repl.execute('readFile(counterFile)');
        let previousOutput = repl.lastOutput;
        await repl.execute('readFile(testRoot + "test-rascalfs-end-watch")');
        await driver.wait(async () => {
            await repl.execute('writeFile(l, "cc")');
            await repl.execute('readFile(counterFile)');
            if (repl.lastOutput !== previousOutput) {
                previousOutput = repl.lastOutput;
                return false;
            }
            return true;
        }, Delays.slow, "Callback counter changed after watch ended", Delays.fast);
    });

    it("IO operations on Rascal locations from VS Code", async () => {
        const repl = new RascalREPL(bench, driver);
        await repl.start();
        await repl.execute("import IO;");
        await repl.execute("l = |tmp:///rascal-remotefs-test/rascal-test-file|;");
        await repl.execute('writeFile(l, "")');
        await repl.execute("readFile(|rascal-vscode-test:///remotefs-api-test/test-rascalfs-write|)");
        await repl.execute('readFile(l) == "hi"');
        expect(repl.lastOutput).is.equal("bool: true", "Writing Rascal Code fs works");
    });

    it("resolveLocation from Rascal file system", async () => {
        const repl = new RascalREPL(bench, driver);
        await repl.start();
        await repl.execute(":edit IO", true, Delays.extremelySlow);

        await driver.wait(async () => await (await bench.getEditorView().getActiveTab())?.getTitle() === "IO.rsc", Delays.slow, "IO should be opened");
    });
});

