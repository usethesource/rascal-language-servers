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
import { BottomBarPanel, MarkerType, VSBrowser, WebDriver, Workbench } from 'vscode-extension-tester';
import { Delays, IDEOperations, RascalREPL, TestWorkspace, ignoreFails, printRascalOutputOnFailure } from './utils';

describe('DSL unregister/register race', function () {
    let browser: VSBrowser;
    let driver: WebDriver;
    let bench: Workbench;
    let ide : IDEOperations;

    let failed: boolean = false;

    this.timeout(Delays.extremelySlow * 2);

    printRascalOutputOnFailure('Language Parametric Rascal', () => ide);

    async function loadPico() {
        const repl = new RascalREPL(bench, driver);
        await repl.start();
        await repl.execute("import demo::lang::pico::LanguageServer;");
        const replExecuteMain = repl.execute("registrationSandwich();"); // we don't wait yet, because we might miss pico loading window
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
    });

    beforeEach(async function () {
    });

    afterEach(async function () {
        if (this.currentTest && this.currentTest.state === 'failed') {
            failed = true;
        }
        await ide.cleanup();
    });

    after(async function() {
    });

    for (let i = 0; i < 100; i++) {
        it.only("Try trigger race", async function () {
            if (failed) {
                this.skip();
            }

            await loadPico();

            await ide.openModule(TestWorkspace.picoFile);
            const labels = await driver.wait(async () => {
                const bbp = new BottomBarPanel();
                const problems = await bbp.openProblemsView();
                await ide.screenshot("problems");
                const errors = await problems.getAllVisibleMarkers(MarkerType.Error);
                if (errors.length === 0) {
                    return false;
                }
                return await Promise.all(errors.map(async e => await e.getLabel()));

            }, Delays.verySlow, "Cannot get problem markers.");

            if (!labels) {
                fail("No labels");
            }

            assert(!labels.includes("Registered A"), "A should be unregistered");
            assert(labels.includes("Registered B"), "B should be registered");
        });
    }
});

