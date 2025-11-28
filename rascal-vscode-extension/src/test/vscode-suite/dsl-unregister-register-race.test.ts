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

import assert, { fail } from 'assert';
import { BottomBarPanel, VSBrowser, WebDriver, Workbench } from 'vscode-extension-tester';
import { loadPico } from './dsl.test';
import { Delays, IDEOperations, getLogs, ignoreFails, printRascalOutputOnFailure } from './utils';

describe('DSL unregister/register race', function () {
    let browser: VSBrowser;
    let driver: WebDriver;
    let bench: Workbench;
    let ide : IDEOperations;

    let failed: boolean = false;

    this.timeout(Delays.extremelySlow * 2);

    printRascalOutputOnFailure(() => driver, () => ide);

    before(async () => {
        browser = VSBrowser.instance;
        driver = browser.driver;
        bench = new Workbench();
        await ignoreFails(browser.waitForWorkbench());
        ide = new IDEOperations(browser);
        await ide.load();
    });

    beforeEach(async function () {
    });

    afterEach(async function () {
        if (this.currentTest && this.currentTest.state === 'failed') {
            failed = true;
        }
        if (this.currentTest) {
            await ide.cleanup();
        }
    });

    after(async function() {
    });

    for (let i = 0; i < 100; i++) {
        it.only("Try trigger race", async function () {
            if (failed) {
                this.skip();
            }
            const bbp = new BottomBarPanel();
            await bbp.openOutputView();
            await bench.executeCommand("workbench.output.action.clearOutput");
            await loadPico(bench, driver, browser, false, true);
            const logs = await driver.wait(async() => await ignoreFails(getLogs(driver)));
            if (!logs) {
                fail("No logs");
            }
            assert(logs.length > 0);
            console.log(logs);
            const lastUnregister = logs.findLastIndex(l => l.match(/ParametricTextDocumentService unregisterLanguage/i));
            const allRegisters = logs.filter(l => l.match(/ParametricTextDocumentService registerLanguage/i)).map(l => logs.indexOf(l)).sort().slice(-4);
            assert(lastUnregister > 0, "No `unregisterLanguage` log found");
            assert(allRegisters.length === 4, "No `registerLanguage` log found");
            for (const r of allRegisters) {
                assert(lastUnregister < r, `Language unregistration was not finished before registration started:\n${logs[r]}\n${logs[lastUnregister]}`);
            }
        });
    }
});

