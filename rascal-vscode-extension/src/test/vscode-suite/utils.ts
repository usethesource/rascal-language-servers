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

import { assert } from "chai";
import { stat, unlink } from "fs/promises";
import path = require("path");
import { By, CodeLens, EditorView, Locator, TerminalView, TextEditor, VSBrowser, WebDriver, WebElement, Workbench, until } from "vscode-extension-tester";

export async function sleep(ms: number) {
    return new Promise(r => setTimeout(r, ms));
}

export class TestWorkspace {
    private static get workspacePrefix() { return 'test-workspace'; }
    public static get workspaceFile() { return path.join(this.workspacePrefix, 'test.code-workspace'); }
    public static get testProject() { return path.join(this.workspacePrefix, 'test-project'); }
    public static get libProject() { return path.join(this.workspacePrefix, 'test-lib'); }
    public static get mainFile() { return path.join(this.testProject, 'src', 'main', 'rascal', 'Main.rsc'); }
    public static get mainFileTpl() { return path.join(this.testProject, 'target', 'classes', 'rascal','Main.tpl'); }
    public static get libCallFile() { return path.join(this.testProject, 'src', 'main', 'rascal', 'LibCall.rsc'); }
    public static get libCallFileTpl() { return path.join(this.testProject, 'target', 'classes', 'rascal','LibCall.tpl'); }
    public static get libFile() { return path.join(this.libProject, 'src', 'main', 'rascal', 'Lib.rsc'); }
    public static get libFileTpl() { return path.join(this.libProject, 'target', 'classes', 'rascal','Lib.tpl'); }

    public static get picoFile() { return path.join(this.testProject, 'src', 'main', 'pico', 'testing.pico'); }
}



export const REPL_CREATE_TIMEOUT = 20_000;
export const REPL_READY_TIMEOUT = 20_000;
export class RascalREPL {
    private lastReplOutput = '';
    private terminal: TerminalView;


    constructor(private bench : Workbench, private driver: WebDriver) {
        this.terminal = new TerminalView();
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

    private async tryConnect(): Promise<TerminalView | undefined> {
        try {
            return await new TerminalView().wait(100);
        }
        catch ( _ignored) { return undefined; }
    }

    async connect() {
        this.terminal = (await this.driver.wait(this.tryConnect, 20_000, "Waiting to find terminal view"))!;
        await this.driver.wait(async () => (await this.terminal.getCurrentChannel()).includes("Rascal"),
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

    async waitForLastOutput(): Promise<string> {
        assert(await this.waitForReplReady());
        return this.lastReplOutput;
    }

    async terminate() {
        await this.execute(":quit", false);
        await this.bench.executeCommand("workbench.action.terminal.killAll");
    }
}

export class IDEOperations {
    private editorView : EditorView;
    private driver: WebDriver;
    constructor(
        private browser: VSBrowser,
        private bench: Workbench,
    ) {
        this.editorView = bench.getEditorView();
        this.driver = browser.driver;
    }

    async load() {
        await this.browser.openResources(TestWorkspace.workspaceFile);
    }

    async cleanup() {
        await this.revertOpenChanges();
        await this.editorView.closeAllEditors();
    }

    hasElement(editor: TextEditor, selector: Locator, timeout: number, message: string): Promise<WebElement> {
        return this.driver.wait(() => editor.findElement(selector), timeout, message );
    }

    hasErrorSquiggly(editor: TextEditor, timeout = 5_000, message = "Missing error squiggly"): Promise<WebElement> {
        return this.driver.wait(until.elementLocated(By.className("squiggly-error")), timeout, message);
    }

    hasSyntaxHighlighting(editor: TextEditor, timeout = 5_000, message = "Syntax highlighting should be present"): Promise<WebElement> {
        return this.hasElement(editor, By.className("mtk18"), timeout, message);
    }

    hasInlayHint(editor: TextEditor, timeout = 5_000, message = "Missing inlay hint") {
        return this.hasElement(editor, By.css('[class*="dyn-rule"'), timeout, message);
    }

    revertOpenChanges(): Promise<void> {
        return this.bench.executeCommand("workbench.action.revertAndCloseActiveEditor");
    }

    async openModule(file: string): Promise<TextEditor> {
        await this.browser.openResources(file);
        return await this.editorView.openEditor(path.basename(file)) as TextEditor;
    }

    async triggerTypeChecker(editor: TextEditor, { checkName = "Rascal check", waitForFinish = false, timeout = 20_000, tplFile = "" } = {}) {
        const lastLine = await editor.getNumberOfLines();
        if (tplFile) {
            await safeDelete(tplFile);
        }
        await editor.setTextAtLine(lastLine, await editor.getTextAtLine(lastLine) + " ");
        await sleep(50);
        await editor.save();
        await sleep(50);
        if (waitForFinish) {
            let doneChecking = async () => (await this.bench.getStatusBar().getItem(checkName)) === undefined;
            if (tplFile) {
                const oldDone = doneChecking;
                doneChecking = async () => await oldDone() || await fileExists(tplFile);
            }

            await this.driver.wait(doneChecking, timeout, `${checkName} should be finished processing the module`);
        }
    }

    findCodeLens(editor: TextEditor, name: string, timeout = 10_000, message = `Cannot find code lens: ${name}`): Promise<CodeLens | undefined> {
        return this.driver.wait(() => editor.getCodeLens(name), timeout, message);
    }

    statusContains(needle: string): () => Promise<boolean> {
        return async () => {
            for (const st of await this.bench.getStatusBar().getItems()) {
                try {
                    if ((await st.getText()).includes(needle)) {
                        return true;
                    }
                } catch (_ignored) { /* sometimes status items get dropped before we can check them */ }
            }
            return false;
        };
    }
}

async function safeDelete(file: string) {
    try {
        await unlink(file);
    } catch (_ignored) { /* ignore deletion errors */ }
}

async function fileExists(file: string): Promise<boolean> {
    try {
        return await stat(file) !== undefined;
    } catch (_ignored) {
        return false;
    }
}
