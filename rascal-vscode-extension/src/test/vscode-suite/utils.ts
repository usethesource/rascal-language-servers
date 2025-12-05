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

import { assert, expect } from "chai";
import { stat, unlink } from "fs/promises";
import * as os from 'os';
import { env } from "process";
import { BottomBarPanel, By, CodeLens, EditorView, Key, Locator, TerminalView, TextEditor, VSBrowser, WebDriver, WebElement, WebElementCondition, Workbench, until } from "vscode-extension-tester";
import path = require("path");

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
    public static readonly licenseFile = path.join(this.testProject, 'LICENSE');
    public static readonly libCallFileTpl = path.join(target(this.testProject),'$LibCall.tpl');
    public static readonly libFile = path.join(src(this.libProject), 'Lib.rsc');
    public static readonly libFileTpl = path.join(target(this.libProject),'$Lib.tpl');
    public static readonly manifest = path.join(this.testProject, "META-INF", "RASCAL.MF");

    public static readonly importerFile = path.join(src(this.testProject), 'Importer.rsc');
    public static readonly importeeFile = path.join(src(this.testProject), 'Importee.rsc');

    public static readonly picoFile = path.join(src(this.testProject, 'pico'), 'testing.pico');
    public static readonly picoNewFile = path.join(src(this.testProject, 'pico'), 'testing.pico-new');
    public static readonly picoCallsFile = path.join(src(this.testProject, 'pico'), 'calls.pico');
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

    async waitForReplReady(wait : number = Delays.verySlow) {
        let output = "";
        try {
            let stopRunning = false;
            try {
                return await this.driver.wait(async () => {
                    if (stopRunning) {
                        // sometimes this code keeps running in a loop
                        // and messes up all the other interactions
                        // so we keep track if we're done, and make sure to
                        // exit quickly in this case.
                        return true;
                    }
                    output = await ignoreFails(this.terminal.getText()) ?? "";
                    if (/rascal>\s*$/.test(output)) {
                        stopRunning = true;
                        return true;
                    }
                    return false;
                }, wait, "Rascal prompt", 500);
            } catch (_ignored) {
                stopRunning = true;
                console.log("**** ignoring exception: ", _ignored);
                console.log('Terminal contents after failing to initialize REPL:');
                console.log(await this.terminal.getText());
                return false;
            }
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
        assert(await this.waitForReplReady(Delays.extremelySlow), "Repl prompt should print");
    }

    async execute(command: string, waitForReady = true, wait=Delays.verySlow) {
        const inputs = await this.terminal.findElements(By.className('xterm-helper-textarea'));
        for (const i of inputs) {
            // there can be multiple terminals, so we iterate over all of the to find the one that doesn't throw an exception
            await ignoreFails(i.clear());
            await ignoreFails(i.sendKeys(command + '\n'));
        }
        if (waitForReady) {
            assert(await this.waitForReplReady(wait), "Repl should have finished processing at some point: " + this.lastReplOutput);
        }
    }

    get lastOutput() { return this.lastReplOutput; }

    async terminate() {
        await ignoreFails(this.execute(":quit", false));
        await ignoreFails(this.bench.executeCommand("workbench.action.terminal.killAll"));
    }
}

function scopedElementLocated(scope:WebElement, selector: Locator): WebElementCondition {
    return new WebElementCondition("locating element in scope", async (_driver) => {
        try {
            const result = await ignoreFails(scope.findElements(selector));
            if (result && result.length > 0) {
                return result[0] ?? null;
            }
            return null;
        }
        catch (_ignored) {
            return null;
        }
    });
}

function scopedElementLocatedCountTimes(scope:WebElement, selector: Locator, minCount: number): WebElementCondition {
    return new WebElementCondition("locating element in scope occuring at least ${minCount} times", async (_driver) => {
        try {
            const result = await scope.findElements(selector);
            if (result && result.length >= minCount) {
                return scope;
            }
            return null;
        }
        catch (_ignored) {
            return null;
        }
    });
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
        await assureDebugLevelLoggingIsEnabled();
    }

    async cleanup() {
        await ignoreFails(this.revertOpenChanges());
        await ignoreFails(new Workbench().getEditorView().closeAllEditors());
        const center = await ignoreFails(new Workbench().openNotificationsCenter());
        await ignoreFails(center?.clearAllNotifications());
        await ignoreFails(center?.close());
    }

    assertLineBecomes(editor: TextEditor, lineNumber: number, lineContents: string, msg: string, wait = Delays.verySlow) : Promise<boolean> {
        return this.driver.wait(async () => {
            const currentContent = (await editor.getTextAtLine(lineNumber)).trim();
            return currentContent === lineContents;
        }, wait, msg, 100);
    }

    hasElement(editor: TextEditor, selector: Locator, timeout: number, message: string): Promise<WebElement> {
        return this.driver.wait(scopedElementLocated(editor, selector), timeout, message, 50);
    }

    hasWarningSquiggly(_editor: TextEditor, timeout = Delays.normal, message = "Missing warning squiggly"): Promise<WebElement> {
        return this.driver.wait(until.elementLocated(By.className("squiggly-warning")), timeout, message);
    }

    hasErrorSquiggly(_editor: TextEditor, timeout = Delays.normal, message = "Missing error squiggly"): Promise<WebElement> {
        return this.driver.wait(until.elementLocated(By.className("squiggly-error")), timeout, message, 50);
    }

    hasRecoveredErrors(editor: TextEditor, errorCount: number, timeout = Delays.normal, message = "Missing recovered parse errors"): Promise<WebElement> {
        // We need to differentiate between real parse errors (error at first line) and recovered parse error (error at parse position).
        return this.driver.wait(scopedElementLocatedCountTimes(editor, By.className("squiggly-error"), errorCount), timeout, message, 50);
    }

    hasSyntaxHighlighting(editor: TextEditor, timeout = Delays.normal, message = "Syntax highlighting should be present"): Promise<WebElement> {
        return this.hasElement(editor, By.css('span[class^="mtk"]:not(.mtk1)'), timeout, message);
    }

    hasInlayHint(editor: TextEditor, timeout = Delays.normal, message = "Missing inlay hint") {
        return this.hasElement(editor, By.css('[class*="dyn-rule"'), timeout, message);
    }

    revertOpenChanges(): Promise<void> {
        let tryCount = 0;
        return this.driver.wait(async () => {
            tryCount++;
            try {
                await new Workbench().executeCommand("workbench.action.revertAndCloseActiveEditor");
            } catch (ex) {
                const title = ignoreFails(new TextEditor().getTitle()) ?? 'unknown';
                this.screenshot(`revert of ${title} failed ` + tryCount);
                console.log(`Revert of ${title} failed, but we ignore it`, ex);
            }
            try {
                let anyEditor = true;
                try {
                    anyEditor = (await (new EditorView().getOpenEditorTitles())).length > 0;
                } catch (_ignored) {
                    anyEditor = false;
                }
                if (!anyEditor) {
                    return true;
                }
                return !(await new TextEditor().isDirty());
            }
            catch (ignored) {
                this.screenshot("open editor check failed " + tryCount);
                console.log("Open editor dirty check failed: ", ignored);
                return false;

            }
        }, Delays.normal, "We should be able to undo").then(_b => {});
    }

    async openModule(file: string): Promise<TextEditor> {
        await this.browser.openResources(file);
        return this.driver.wait(async () => {
            const result = await ignoreFails(new Workbench().getEditorView().openEditor(path.basename(file))) as TextEditor;
            if (result && await ignoreFails(result.getTitle()) === path.basename(file)) {
                return result;
            }
            return undefined;
        }, Delays.normal, "Could not open file") as Promise<TextEditor>;
    }

    async appendSpace(editor: TextEditor, line = 1) {
        const prompt = await new Workbench().openCommandPrompt();
        await prompt.setText(`:${line},10000`);
        await prompt.confirm();
        await editor.typeText(' ');
    }

    async triggerTypeChecker(editor: TextEditor, { checkName = "Rascal check", waitForFinish = false, timeout = Delays.verySlow, tplFile = "" } = {}) {
        if (tplFile) {
            await ignoreFails(unlink(tplFile));
        }
        await this.appendSpace(editor);
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

    /**
     * This makes the code action menu popup _if there are code actions on the current line_
     * and then selects the first entry from the menu. This works only if the given actionLabel
     * indeed becomes the first menu item.
     *
     * @param editor
     * @param actionLabel
     */
    async triggerFirstCodeAction(editor: TextEditor, actionLabel:string) {
        const inputarea = await editor.findElement(By.className('inputarea'));
        await inputarea.sendKeys(Key.chord(TextEditor.ctlKey, "."));

        // finds an open menu with the right item in it (Change to a) and then select
        // the parent that handles UI events like click() and sendKeys()
        const menuContainer = await this.hasElement(editor, By.xpath("//div[contains(@class, 'focused') and contains(@class, 'action')]/span[contains(text(), '" + actionLabel + "')]//ancestor::*[contains(@class, 'monaco-list')]"), Delays.normal, actionLabel + " action should be available and focused");

        // menu container works a bit strangely, it ask the focus to keep track of it,
        // and manages clicks and menus on the highest level (not per item).
        await menuContainer.sendKeys(Key.RETURN);
    }

    async renameSymbol(editor: TextEditor, bench: Workbench, newName: string) {
        let renameSuccess = false;
        let tries = 0;
        while (!renameSuccess && tries < 5) {
            try {
                await bench.executeCommand("Rename Symbol");
                const renameBox = await this.hasElement(editor, By.className("rename-input"), Delays.normal, "Rename box should appear");
                await renameBox.sendKeys(Key.BACK_SPACE, Key.BACK_SPACE, Key.BACK_SPACE, newName, Key.ENTER);
                renameSuccess = true;
            }
            catch (e) {
                console.log("Rename failed to succeed, lets try again");
                await this.screenshot(`DSL-failed-rename-round-${tries}`);
                tries++;
            }
        }
        expect(renameSuccess, "We should have been able to trigger the rename box after 5 times");
    }

    async moveFile(fromFile: string, toDir: string, bench: Workbench) {
        const explorer = await (await bench.getActivityBar().getViewControl("Explorer"))!.openView();
        await bench.executeCommand("workbench.files.action.refreshFilesExplorer");
        const workspace = await explorer.getContent().getSection("test (Workspace)");
        await workspace.expand();

        // Move the file
        if (os.type() === "Darwin") {
            // Context menus are not supported for macOS:
            // https://github.com/redhat-developer/vscode-extension-tester/blob/main/KNOWN_ISSUES.md#macos-known-limitations-of-native-objects
            //
            // The following workaround triggers a move of `Lib.rsc` by cutting
            // and pasting that file using keyboard input. It works under the
            // assumption that `Lib.rsc` and `lib` are visible in the Explorer.
            // If this assumption breaks in the future, then see the
            // implementation of `DefaultTreeSection.findItem` for inspiration
            // on how to scroll the Explorer down:
            // https://github.com/redhat-developer/vscode-extension-tester/blob/1bd6c23b25673a76f4a9d139f4572c0ea6f55a7b/packages/page-objects/src/components/sidebar/tree/default/DefaultTreeSection.ts#L36-L59

            // Find the div that contains the whole visible tree in the Explorer
            const treeDiv = await workspace.findElement(By.className('monaco-list'));

            // Cut
            const libFileInTreeDiv = (await treeDiv.findElements(By.xpath(`.//div[@role='treeitem' and @aria-label='Lib.rsc']`)))[0];
            await libFileInTreeDiv?.click(); // Must click on this div instead of the object returned by `findItem`
            await treeDiv.sendKeys(Key.COMMAND, 'x', Key.COMMAND); // Only this div handles key events; not `libFileInTreeDiv`

            // Paste
            const libFolderInTreeDiv = (await treeDiv.findElements(By.xpath(`.//div[@role='treeitem' and @aria-label='lib']`)))[0];
            await libFolderInTreeDiv?.click(); // Must click on this div instead of the object returned by `findItem`
            await treeDiv.sendKeys(Key.COMMAND, 'v', Key.COMMAND); // Only this div handles key events; not `libFolderInTreeDiv`
        }

        else {
            // Context menus are supported for Windows and Linux
            const libFileInTree = await this.driver.wait(async() => workspace.findItem(fromFile), Delays.normal, "Cannot find source file");
            const libFolderInTree = await this.driver.wait(async() => workspace.findItem(toDir), Delays.normal, "Cannot find destination folder");
            await (await libFileInTree!.openContextMenu()).select("Cut");
            await (await libFolderInTree!.openContextMenu()).select("Paste");
        }
    }


    findCodeLens(editor: TextEditor, name: string, timeout = Delays.slow, message = `Cannot find code lens: ${name}`): Promise<CodeLens | undefined> {
        return this.driver.wait(() => ignoreFails(editor.getCodeLens(name)), timeout, message);

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

    private screenshotSeqNumber = 0;

    screenshot(name: string): Promise<void> {
        return this.browser.takeScreenshot(
            `${String(this.screenshotSeqNumber++).padStart(4, '0')}-` + // Make sorting screenshots chronologically in VS Code easier
            name.replace(/[/\\?%*:|"<>]/g, '-'));
    }
}


async function showRascalOutput(bbp: BottomBarPanel, channel: string) {
    const outputView = await bbp.openOutputView();
    await outputView.selectChannel(`${channel} Language Server`);
    return outputView;
}

let alreadySetup = false;

async function assureDebugLevelLoggingIsEnabled() {
    if (alreadySetup) {
        return;
    }
    alreadySetup = true; // to avoid doing this twice/parallel
    const prompt = await new Workbench().openCommandPrompt();
    await prompt.setText(">workbench.action.setLogLevel");
    await prompt.confirm();
    await prompt.setText("Debug");
    await prompt.confirm();
}

export function printRascalOutputOnFailure(channel: 'Language Parametric Rascal' | 'Rascal MPL', ide: () => IDEOperations) {

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
                textLines = await ignoreFails(bbp.findElements(By.className('view-line'))) ?? [];
                tries++;
            }
            if (textLines.length === 0) {
                console.log("We could not capture the output lines");
            }

            for (const l of textLines) {
                console.log(await l.getText());
            }
            await bbp.closePanel();
        } catch (e) {
            console.log('Error capturing output: ', e);
            ide().screenshot("uncaptured-output");
        }
        finally {
            console.log('*******End output*****************************');
            for (let z = 0; z < ZOOM_OUT_FACTOR; z++) {
                await new Workbench().executeCommand('workbench.action.zoomIn');
            }
        }
    });
}
