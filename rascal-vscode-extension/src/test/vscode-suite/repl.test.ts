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

import { assert, expect } from 'chai';
import { BottomBarPanel, By, EditorView, TerminalView, TextEditor, VSBrowser, WebDriver, Workbench } from 'vscode-extension-tester';
import { TestWorkspace, sleep } from './utils';
import path = require('path');

const REPL_CREATE_TIMEOUT = 20_000;
const REPL_READY_TIMEOUT = 20_000;

// Create a Mocha suite
describe('REPL', function () {
    let browser: VSBrowser;
    let driver: WebDriver;
    let terminal: TerminalView;
    let panel: BottomBarPanel;

    this.timeout(20_000);

    before(async () => {
        browser = VSBrowser.instance;
        driver = browser.driver;
        await browser.openResources(TestWorkspace.workspaceFile);
        await browser.waitForWorkbench();
        panel = new BottomBarPanel();
        await panel.toggle(true);
        // we start an initial terminal and keep that one open
        terminal = await panel.openTerminalView();
        await sleep(2000);
    });


    after(async () => {
        await terminal.killTerminal();
        await panel.toggle(false);
    });

    afterEach(async () => {
        await terminal.killTerminal();
    });

    it("should open without a project", async () => {
        await new RascalREPL(terminal, driver).start();
    }).timeout(REPL_CREATE_TIMEOUT + REPL_READY_TIMEOUT);

    it("run basic rascal commands", async () => {
        const repl = new RascalREPL(terminal, driver);
        await repl.start();
        await repl.execute("40 + 2");
        expect(repl.lastOutput).matches(/int.*42/, "Result of expression should be 42");
        await repl.execute("import IO;");
        await repl.execute('println("Printing works: <1 + 3>");');
        expect(repl.lastOutput).is.equal("Printing works: 4\nok", "println works as expected");
    }).timeout(REPL_CREATE_TIMEOUT + REPL_READY_TIMEOUT);

    it("import module and run in terminal", async () => {
        await browser.openResources(TestWorkspace.libCallFile);
        const editor = await new EditorView().openEditor(path.basename(TestWorkspace.libCallFile)) as TextEditor;
        const lens = await driver.wait(async () => editor.getCodeLens("Run in new Rascal terminal"), 10_000, "Run in new terminal should show");
        await lens?.click();
        const repl = new RascalREPL(terminal, driver);
        await repl.connect();
        expect(repl.lastOutput).is.equal("5\nint:0");
    }).timeout(REPL_CREATE_TIMEOUT * 10);
});



class RascalREPL {
    private lastReplOutput = '';


    constructor(private terminal: TerminalView, private driver: WebDriver) {
    }

    async waitForReplReady() {
        let output = "";
        try {
            for (let tries = 0; tries < 5; tries++) {
                await sleep(REPL_READY_TIMEOUT / 10);
                output = await this.terminal.getText();
                if (/rascal>\s*$/.test(output)) {
                    return true;
                }
                await sleep(REPL_READY_TIMEOUT / 10);
            }
            return false;
        }
        finally {
            const lines = output.split('\n').map(l => l.trimEnd());
            const lastPrompt = lines.lastIndexOf("rascal>");
            let secondToLastPrompt = -1;
            for (let l = 0; l < lastPrompt; l++) {
                if (lines[l].startsWith("rascal>")) {
                    secondToLastPrompt = l;
                }
            }
            if (secondToLastPrompt >= 0 && lastPrompt > 0) {
                this.lastReplOutput = lines.slice(secondToLastPrompt + 1, lastPrompt).join('\n').trimEnd();
                console.log(this.lastReplOutput);
            }
        }
    }

    async start() {
        await new Workbench().executeCommand("rascalmpl.createTerminal");
        return this.connect();
    }
    async connect() {
        await this.driver.wait(async () =>
            (await this.terminal.getCurrentChannel()).includes("Rascal"),
            REPL_CREATE_TIMEOUT, "Rascal REPL should be opened");
        assert(await this.waitForReplReady(), "Repl prompt should print");
    }

    async execute(command: string, waitForReady = true) {
        const inputs = await this.driver.findElements(By.className('xterm-helper-textarea'));
        for (const i of inputs) {
            // there can be multiple terminals, so we iterate over all of the to find the one that doesn't throw an exception
            try {
                await i.clear();
                try {
                    await i.sendKeys(command + '\n');
                } catch (_ignore) { /* ignore, might other terminal */ }
            } catch (_ignore) { /* ignore, might be other terminal */ }
        }
        if (waitForReady) {
            assert(await this.waitForReplReady());
        }
    }

    get lastOutput() { return this.lastReplOutput; }

    async waitForLastOutput() {
        assert(await this.waitForReplReady());
        return this.lastReplOutput;
    }
}
