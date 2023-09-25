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

import { assert, expect,  } from 'chai';
// import the webdriver and the high level browser wrapper
import { BottomBarPanel, By, TerminalView, VSBrowser, WebDriver, Workbench } from 'vscode-extension-tester';
import { integer } from 'vscode-languageclient';


// Create a Mocha suite
describe('Rascal VS Code extension', function () {
    let browser: VSBrowser;
    let driver: WebDriver;
    let bench: Workbench;

    this.timeout(20000);

    // initialize the browser and webdriver
    before(async () => {
        browser = VSBrowser.instance;
        driver = browser.driver;
        bench = new Workbench();
        await browser.waitForWorkbench();
    });

    describe('REPL', () => {
        let terminal: TerminalView;
        let panel: BottomBarPanel;
        const REPL_CREATE_TIMEOUT = 10_000;
        const REPL_READY_TIMEOUT = 10_000;

        before(async () => {
            /* make bottom panel in focus */
            panel = new BottomBarPanel();
            await panel.toggle(true);
            terminal = await panel.openTerminalView();
            await sleep(2000);
        });

        after(async () => {
            await terminal.killTerminal();
            await panel.toggle(false);
        });

        afterEach(async() => {
            await terminal.killTerminal();
        });

        async function isRascalReplActive() {
            for (let tries = 0; tries < 20; tries++) {
                if ((/rascal/i).test(await terminal.getCurrentChannel())) {
                    return true;
                }
                await sleep(REPL_CREATE_TIMEOUT / 20);
            }
            return false;
        }

        async function waitForReplReady() {
            for (let tries = 0; tries < 5; tries++) {
                await sleep(REPL_READY_TIMEOUT / 5);
                if (/rascal>\s*$/.test(await terminal.getText())) {
                    return true;
                }
            }
            return false;
        }

        async function createRascalTerminal() {
            await bench.executeCommand("rascalmpl.createTerminal");
            assert(await isRascalReplActive(), "Terminal should be opened");
            assert(await waitForReplReady(), "Repl prompt should print");
        }

        async function execute(command: string, waitForReady = true) {
            const inputs = await driver.findElements(By.className('xterm-helper-textarea'));
            //console.log(inputs);
            for (const i of inputs) {
                try {
                    await i.clear();
                    try {
                        await i.sendKeys(command +'\n');
                    } catch (_ignore) { /* ignore, might other terminal */ }
                } catch (_ignore) { /* ignore, might be toher terminal */ }
            }
            if (waitForReady) {
                assert(await waitForReplReady());
            }

        }

        it ("should open without a project", async () => {
            await createRascalTerminal();
        }).timeout(REPL_CREATE_TIMEOUT + REPL_READY_TIMEOUT);

        it ("run basic rascal commands", async () => {
            await createRascalTerminal();
            await execute("40 + 2");
            assert.match(await terminal.getText(), /int.*42/, "Result of expression should be 42");
            await execute("import IO;");
            await execute('println("Printing works: <1 + 3>");');
            assert.match(await terminal.getText(), /Printing works: 4/, "println works as expected");
        }).timeout(REPL_CREATE_TIMEOUT + REPL_READY_TIMEOUT);

    });
});


async function sleep(ms: number) {
    return new Promise(r => setTimeout(r, ms));
}
