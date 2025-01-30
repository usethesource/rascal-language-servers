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
import * as vscode from 'vscode';
import { RascalDebugClient } from './RascalDebugClient';

export class RascalDebugViewProvider implements vscode.TreeDataProvider<RascalReplNode> {
    private changeEmitter = new vscode.EventEmitter<RascalReplNode | undefined>();
    readonly onDidChangeTreeData = this.changeEmitter.event;

    constructor(private readonly rascalDebugClient: RascalDebugClient, readonly context: vscode.ExtensionContext) {
        const fireEmitter = (_: vscode.Terminal | vscode.DebugSession | undefined) : void => {
            this.changeEmitter.fire(undefined);
        };

        vscode.window.onDidOpenTerminal(fireEmitter, this, context.subscriptions);
        vscode.window.onDidCloseTerminal(fireEmitter, this, context.subscriptions);
        vscode.window.onDidChangeActiveTerminal(fireEmitter, this, context.subscriptions);
        vscode.debug.onDidStartDebugSession(fireEmitter, this, context.subscriptions);
        vscode.debug.onDidTerminateDebugSession(fireEmitter, this, context.subscriptions);

        this.rascalDebugClient.portRegistrationEvent(fireEmitter, this, context.subscriptions);

        this.context.subscriptions.push(
            vscode.commands.registerCommand("rascalmpl.startDebuggerForRepl", (replNode: RascalReplNode) => {
                if (replNode.serverPort !== undefined) {
                    this.rascalDebugClient.startDebuggingSession(replNode.serverPort);
                }
            }, this)
        );
    }

    getTreeItem(element: RascalReplNode): vscode.TreeItem | Thenable<vscode.TreeItem> {
        return element;
    }
    getChildren(element?: RascalReplNode | undefined): vscode.ProviderResult<RascalReplNode[]> {
        if (element === undefined) {
            return this.updateRascalDebugView();
        }
        return [];
    }
    getParent?(_element: RascalReplNode): vscode.ProviderResult<RascalReplNode> {
        //The Rascal debug view gives a flat list of the opened Rascal terminals. As such, only root items exit in this view.
        return undefined;
    }
    resolveTreeItem?(item: vscode.TreeItem, _element: RascalReplNode, _token: vscode.CancellationToken): vscode.ProviderResult<vscode.TreeItem> {
        return item;
    }

    async makeLabel(label: string, processId : number) : Promise<string | vscode.TreeItemLabel> {
        if (await this.isActiveTerminal(processId)) {
            return {label: label, highlights : [[0, label.length]]};
        }
        return label;
    }

    async updateRascalDebugView() : Promise<RascalReplNode[]> {
        const result : RascalReplNode[] = [];
        for (const terminal of vscode.window.terminals) {
            const processId = await terminal.processId;
            if (processId === undefined) {
                continue;
            }
            if (terminal.name.includes("Rascal terminal")) {
                const label = await this.makeLabel(terminal.name, processId);
                const serverPort = this.rascalDebugClient.getServerPort(processId);
                const isDebugging = serverPort !== undefined && this.rascalDebugClient.isConnectedToDebugServer(serverPort);
                const replNode = new RascalReplNode(label, serverPort, isDebugging);
                if (serverPort !== undefined && !isDebugging) {
                    replNode.contextValue = "canStartDebugging";
                }
                result.push(replNode);
            }
        }
        return result;
    }

    async isActiveTerminal(processId : number) : Promise<boolean> {
        const activeTerminal = vscode.window.activeTerminal;
        if (activeTerminal === undefined) {
            return false;
        }
        return processId === await activeTerminal.processId;
    }

}

export class RascalReplNode extends vscode.TreeItem {
    constructor(label : string | vscode.TreeItemLabel, readonly serverPort : number | undefined, isDebugging : boolean) {
        super(label, vscode.TreeItemCollapsibleState.None);
        this.iconPath = new vscode.ThemeIcon(isDebugging ? "debug" : "terminal");
    }
}
