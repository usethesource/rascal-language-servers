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
import { env } from "process";
import { By, CodeLens, Locator, TerminalView, TextEditor, VSBrowser, WebDriver, WebElement, Workbench, until } from "vscode-extension-tester";

export async function sleep(ms: number) {
    return new Promise(r => setTimeout(r, ms));
}

function sec(n: number) { return n * 1000; }
export class Delays {
    private static readonly delayFactor = parseInt(env['DELAY_FACTOR'] ?? "1");
    public static readonly fast = sec(1) * this.delayFactor;
    public static readonly normal =sec(5) * this.delayFactor;
    public static readonly slow = sec(15) * this.delayFactor;
    public static readonly verySlow =sec(30) * this.delayFactor;
    public static readonly extremelySlow =sec(120) * this.delayFactor;
}

function src(project : string, language = 'rascal') { return path.join(project, 'src', 'main', language); }
function target(project : string) { return path.join(project, 'target', 'classes', 'rascal'); }
export class TestWorkspace {
    private static readonly workspacePrefix = 'test-workspace';
    public static readonly workspaceFile = path.join(this.workspacePrefix, 'test.code-workspace');
    public static readonly testProject = path.join(this.workspacePrefix, 'test-project');
    public static readonly libProject = path.join(this.workspacePrefix, 'test-lib');
    public static readonly mainFile = path.join(src(this.testProject), 'Main.rsc');
    public static readonly mainFileTpl = path.join(target(this.testProject),'Main.tpl');
    public static readonly libCallFile = path.join(src(this.testProject), 'LibCall.rsc');
    public static readonly libCallFileTpl = path.join(target(this.testProject),'LibCall.tpl');
    public static readonly libFile = path.join(src(this.libProject), 'Lib.rsc');
    public static readonly libFileTpl = path.join(target(this.libProject),'Lib.tpl');

    public static readonly picoFile = path.join(src(this.testProject, 'pico'), 'testing.pico');
    public static readonly picoNewFile = path.join(src(this.testProject, 'pico'), 'testing.pico-new');
}


const _DEBUG = false;

export async function ignoreFails<T>(fn : Promise<T> | undefined): Promise<T | undefined> {
    try {
        if (fn === undefined) {
            return undefined;
        }
        return await fn;
    } catch (exp) {
        if (_DEBUG) {
            console.debug("Promise failed, ignoring exception: ", exp);
        }
        return undefined;
    }
}

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
                await sleep(Delays.slow / 10);
                output = await this.terminal.getText();
                if (/rascal>\s*$/.test(output)) {
                    return true;
                }
                await sleep(Delays.slow / 10);
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
            }
        }
    }

    async start() {
        await new Workbench().executeCommand("rascalmpl.createTerminal");
        return this.connect();
    }

    async connect() {
        this.terminal = (await this.driver.wait(() => ignoreFails(new TerminalView().wait(100)), Delays.verySlow, "Waiting to find terminal view"))!;
        await this.driver.wait(async () => (await ignoreFails(this.terminal.getCurrentChannel()))?.includes("Rascal"),
            Delays.slow, "Rascal REPL should be opened");
        assert(await this.waitForReplReady(), "Repl prompt should print");
    }

    async execute(command: string, waitForReady = true) {
        const inputs = await this.terminal.findElements(By.className('xterm-helper-textarea'));
        for (const i of inputs) {
            // there can be multiple terminals, so we iterate over all of the to find the one that doesn't throw an exception
            await ignoreFails(i.clear());
            await ignoreFails(i.sendKeys(command + '\n'));
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
        await ignoreFails(this.execute(":quit", false));
        await ignoreFails(this.bench.executeCommand("workbench.action.terminal.killAll"));
    }
}

export class IDEOperations {
    private driver: WebDriver;
    constructor(
        private browser: VSBrowser,
        private bench: Workbench,
    ) {
        this.driver = browser.driver;
    }

    async load() {
        await ignoreFails(this.browser.waitForWorkbench(Delays.slow));
        for (let t = 0; t < 5; t++) {
            try {
                await this.browser.openResources(TestWorkspace.workspaceFile);
            } catch (ex) {
                console.debug("Error opening workspace, retrying.", ex);
            }
        }
        await ignoreFails(this.browser.waitForWorkbench(Delays.normal));
        await ignoreFails(this.browser.waitForWorkbench(Delays.normal));
        const center = await ignoreFails(this.bench.openNotificationsCenter());
        await ignoreFails(center?.clearAllNotifications());
        await ignoreFails(center?.close());
    }

    async cleanup() {
        await ignoreFails(this.revertOpenChanges());
        await ignoreFails(this.bench.getEditorView().closeAllEditors());
        const center = await ignoreFails(this.bench.openNotificationsCenter());
        await ignoreFails(center?.clearAllNotifications());
        await ignoreFails(center?.close());
    }

    hasElement(editor: TextEditor, selector: Locator, timeout: number, message: string): Promise<WebElement> {
        return this.driver.wait(() => editor.findElement(selector), timeout, message );
    }

    hasErrorSquiggly(editor: TextEditor, timeout = Delays.normal, message = "Missing error squiggly"): Promise<WebElement> {
        return this.driver.wait(until.elementLocated(By.className("squiggly-error")), timeout, message);
    }

    hasSyntaxHighlighting(editor: TextEditor, timeout = Delays.normal, message = "Syntax highlighting should be present"): Promise<WebElement> {
        return this.hasElement(editor, By.css('span[class^="mtk"]:not(.mtk1)'), timeout, message);
    }

    hasInlayHint(editor: TextEditor, timeout = Delays.normal, message = "Missing inlay hint") {
        return this.hasElement(editor, By.css('[class*="dyn-rule"'), timeout, message);
    }

    revertOpenChanges(): Promise<void> {
        return this.bench.executeCommand("workbench.action.revertAndCloseActiveEditor");
    }

    async openModule(file: string): Promise<TextEditor> {
        for (let tries = 0; tries < 10; tries++) {
            await ignoreFails(this.browser.openResources(file));
            await sleep(Delays.fast);
            const result = await ignoreFails(this.bench.getEditorView().openEditor(path.basename(file))) as TextEditor;
            await sleep(Delays.fast);
            if (result && await ignoreFails(result.getTitle()) === path.basename(file)) {
                return result;
            }
        }
        throw new Error("Could not open file " + file);
    }

    async triggerTypeChecker(editor: TextEditor, { checkName = "Rascal check", waitForFinish = false, timeout = Delays.verySlow, tplFile = "" } = {}) {
        const lastLine = await editor.getNumberOfLines();
        if (tplFile) {
            await ignoreFails(unlink(tplFile));
        }
        await editor.setTextAtLine(lastLine, await editor.getTextAtLine(lastLine) + " ");
        await sleep(50);
        await editor.save();
        await sleep(50);
        if (waitForFinish) {
            let doneChecking = async () => (await this.bench.getStatusBar().getItem(checkName)) === undefined;
            if (tplFile) {
                const oldDone = doneChecking;
                doneChecking = async () => await oldDone() && (await ignoreFails(stat(tplFile)) !== undefined);
            }

            await this.driver.wait(doneChecking, timeout, `${checkName} should be finished processing the module`);
        }
    }

    findCodeLens(editor: TextEditor, name: string, timeout = Delays.slow, message = `Cannot find code lens: ${name}`): Promise<CodeLens | undefined> {
        return this.driver.wait(() => editor.getCodeLens(name), timeout, message);
    }

    statusContains(needle: string): () => Promise<boolean> {
        return async () => {
            for (const st of await ignoreFails(this.bench.getStatusBar().getItems()) ?? []) {
                if ((await ignoreFails(st.getText()))?.includes(needle)) {
                    return true;
                }
            }
            return false;
        };
    }

    screenshot(name: string): Promise<void> {
        return this.browser.takeScreenshot(name.replace(/[/\\?%*:|"<>]/g, '-'));
    }
}
