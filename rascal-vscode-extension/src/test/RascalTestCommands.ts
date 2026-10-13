import * as vscode from "vscode";

export class RascalTestCommands implements vscode.Disposable {
    private readonly toDispose: vscode.Disposable[] = [];

    constructor(private readonly logger: vscode.LogOutputChannel) {
        this.toDispose.push(vscode.commands.registerCommand("rascalmpl.test_copyTerminalContents", () => this.copyTerminalContents()));
    }
    async copyTerminalContents() {
        this.logger.trace("Received debug command to copy terminal contents into clipboard");
        await vscode.commands.executeCommand('workbench.action.terminal.selectAll');
        await vscode.commands.executeCommand('editor.action.clipboardCopyAction');
    }

    dispose() {
        vscode.Disposable.from(...this.toDispose).dispose();
    }
}
