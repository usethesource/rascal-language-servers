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
import { BottomBarPanel, By, CodeLens, Locator, TerminalView, TextEditor, VSBrowser, WebDriver, WebElement, Workbench, until } from "vscode-extension-tester";

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
    public static readonly workspaceName = "test (Workspace)";
    public static readonly testProject = path.join(this.workspacePrefix, 'test-project');
    public static readonly libProject = path.join(this.workspacePrefix, 'test-lib');
    public static readonly mainFile = path.join(src(this.testProject), 'Main.rsc');
    public static readonly mainFileTpl = path.join(target(this.testProject),'$Main.tpl');
    public static readonly libCallFile = path.join(src(this.testProject), 'LibCall.rsc');
    public static readonly libCallFileTpl = path.join(target(this.testProject),'$LibCall.tpl');
    public static readonly libFile = path.join(src(this.libProject), 'Lib.rsc');
    public static readonly libFileTpl = path.join(target(this.libProject),'$Lib.tpl');

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
                if (lines[l]!.startsWith("rascal>")) {
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
    ) {
        this.driver = browser.driver;
    }

    async load() {
        await ignoreFails(this.browser.waitForWorkbench(Delays.slow));
        for (let t = 0; t < 5; t++) {
            try {
                const isWorkSpaceOpen =await this.driver.findElements(By.xpath(`//*[contains(text(),'${TestWorkspace.workspaceName}')]`));
                if (isWorkSpaceOpen !== undefined && isWorkSpaceOpen.length > 0) {
                    break;
                }
                await this.browser.openResources(TestWorkspace.workspaceFile);
            } catch (ex) {
                console.debug("Error opening workspace, retrying.", ex);
            }
        }
        await ignoreFails(this.browser.waitForWorkbench(Delays.normal));
        await ignoreFails(this.browser.waitForWorkbench(Delays.normal));
        const center = await ignoreFails(new Workbench().openNotificationsCenter());
        await ignoreFails(center?.clearAllNotifications());
        await ignoreFails(center?.close());
    }

    async cleanup() {
        await ignoreFails(this.revertOpenChanges());
        await ignoreFails(new Workbench().getEditorView().closeAllEditors());
        const center = await ignoreFails(new Workbench().openNotificationsCenter());
        await ignoreFails(center?.clearAllNotifications());
        await ignoreFails(center?.close());
    }

    hasElement(editor: TextEditor, selector: Locator, timeout: number, message: string): Promise<WebElement> {
        return this.driver.wait(() => editor.findElement(selector), timeout, message, 50);
    }

    hasWarningSquiggly(_editor: TextEditor, timeout = Delays.normal, message = "Missing warning squiggly"): Promise<WebElement> {
        return this.driver.wait(until.elementLocated(By.className("squiggly-warning")), timeout, message);
    }

    hasErrorSquiggly(_editor: TextEditor, timeout = Delays.normal, message = "Missing error squiggly"): Promise<WebElement> {
        return this.driver.wait(until.elementLocated(By.className("squiggly-error")), timeout, message, 50);
    }

    hasSyntaxHighlighting(editor: TextEditor, timeout = Delays.normal, message = "Syntax highlighting should be present"): Promise<WebElement> {
        return this.hasElement(editor, By.css('span[class^="mtk"]:not(.mtk1)'), timeout, message);
    }

    hasInlayHint(editor: TextEditor, timeout = Delays.normal, message = "Missing inlay hint") {
        return this.hasElement(editor, By.css('[class*="dyn-rule"'), timeout, message);
    }

    revertOpenChanges(): Promise<void> {
        return new Workbench().executeCommand("workbench.action.revertAndCloseActiveEditor");
    }

    async openModule(file: string): Promise<TextEditor> {
        return this.driver.wait(async () => {
            await ignoreFails(this.browser.openResources(file));
            const result = await ignoreFails(new Workbench().getEditorView().openEditor(path.basename(file))) as TextEditor;
            if (result && await ignoreFails(result.getTitle()) === path.basename(file)) {
                return result;
            }
            return undefined;
        }, Delays.normal, "Could not open file") as Promise<TextEditor>;
    }

    async triggerTypeChecker(editor: TextEditor, { checkName = "Rascal check", waitForFinish = false, timeout = Delays.verySlow, tplFile = "" } = {}) {
        const lastLine = await editor.getNumberOfLines();
        if (tplFile) {
            await ignoreFails(unlink(tplFile));
        }
        await editor.setTextAtLine(lastLine, await editor.getTextAtLine(lastLine) + " ");
        await sleep(50);
        await editor.save();
        if (waitForFinish) {
            const hasStatus = this.statusContains(checkName);
            await ignoreFails(this.driver.wait(hasStatus, Delays.normal, `${checkName} should have started after a save`));

            if (tplFile) {
                await this.driver.wait(() => ignoreFails(stat(tplFile)), timeout, `${tplFile} should exist by now`);
            }
            await this.driver.wait(async () => !(await hasStatus()), timeout, `${checkName} should be finished processing the module`);
        }
        else {
            await sleep(50);
        }
    }

    findCodeLens(editor: TextEditor, name: string, timeout = Delays.slow, message = `Cannot find code lens: ${name}`): Promise<CodeLens | undefined> {
        return this.driver.wait(() => editor.getCodeLens(name), timeout, message);
    }

    statusContains(needle: string): () => Promise<boolean> {
        return async () => {
            for (const st of await ignoreFails(new Workbench().getStatusBar().getItems()) ?? []) {
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


async function showRascalOutput(bbp: BottomBarPanel, channel: string) {
    const outputView = await bbp.openOutputView();
    await outputView.selectChannel(`${channel} Language Server`);
    return outputView;
}

export function printRascalOutputOnFailure(channel: 'Language Parametric Rascal' | 'Rascal MPL') {

    const ZOOM_OUT_FACTOR = 5;
    afterEach("print output in case of failure", async function () {
        if (!this.currentTest || this.currentTest.state !== "failed") { return; }
        try {
            for (let z = 0; z < ZOOM_OUT_FACTOR; z++) {
                await new Workbench().executeCommand('workbench.action.zoomOut');
            }
            const bbp = new BottomBarPanel();
            await bbp.maximize();
            console.log('**********************************************');
            console.log('***** Rascal MPL output for the failed tests: ');
            let textLines: WebElement[] = [];
            let tries = 0;
            while (textLines.length === 0 && tries < 3) {
                await showRascalOutput(bbp, channel);
                textLines = await bbp.findElements(By.className('view-line'));
                tries++;
            }

            for (const l of textLines) {
                console.log(await l.getText());
            }
            await bbp.closePanel();
        } catch (e) {
            console.log('Error capturing output: ', e);
        }
        finally {
            console.log('*******End output*****************************');
            for (let z = 0; z < ZOOM_OUT_FACTOR; z++) {
                await new Workbench().executeCommand('workbench.action.zoomIn');
            }
        }
    });
}
