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
import * as vscode from 'vscode';
import { RascalDebugClient } from './RascalDebugClient';

export class RascalDebugViewProvider implements vscode.TreeDataProvider<RascalReplNode> {
    private changeEmitter = new vscode.EventEmitter<RascalReplNode | undefined>();
    readonly onDidChangeTreeData = this.changeEmitter.event;

    constructor(private readonly rascalDebugClient: RascalDebugClient) {
        vscode.window.onDidOpenTerminal(_e => {
            this.changeEmitter.fire(undefined);
        });
        vscode.window.onDidCloseTerminal(_e => {
            this.changeEmitter.fire(undefined);
        });
        vscode.window.onDidChangeActiveTerminal(_e => {
            this.changeEmitter.fire(undefined);
        });
        vscode.debug.onDidStartDebugSession(_e => {
            this.changeEmitter.fire(undefined);
        });
        vscode.debug.onDidTerminateDebugSession(_e => {
            this.changeEmitter.fire(undefined);
        });
        vscode.workspace.onDidChangeWorkspaceFolders(_e => {
            this.changeEmitter.fire(undefined);
        });
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
        return undefined;
    }
    resolveTreeItem?(item: vscode.TreeItem, _element: RascalReplNode, _token: vscode.CancellationToken): vscode.ProviderResult<vscode.TreeItem> {
        return item;
    }

    async updateRascalDebugView() : Promise<vscode.TreeItem[]> {
        const result : RascalReplNode[] = [];
        for (const terminal of vscode.window.terminals) {
            const processId = await terminal.processId;
            if (processId === undefined) {
                continue;
            }
            if (terminal.name.includes("Rascal terminal")) {
                let label = terminal.name;
                if (await this.isActiveTerminal(terminal.processId)) {
                    label += "***";
                }
                const serverPort = this.rascalDebugClient.getServerPort(await terminal.processId);
                const replNode = new RascalReplNode(label, serverPort);
                if (serverPort !== undefined && this.rascalDebugClient.isConnectedToDebugServer(serverPort)) {
                    replNode.contextValue = "isDebugging";
                } else {
                    replNode.contextValue = "isNotDebugging";
                }
                result.push(replNode);
            }
        }
        return result;
    }

    async isActiveTerminal(processId : Thenable<number | undefined>) : Promise<boolean> {
        const activeTerminal = vscode.window.activeTerminal;
        if (activeTerminal === undefined) {
            return false;
        }
        return await processId === await activeTerminal.processId;
    }

}

export class RascalReplNode extends vscode.TreeItem {
    serverPort? : number | undefined;

    constructor(label : string | vscode.TreeItemLabel, serverPort : number | undefined) {
        super(label, vscode.TreeItemCollapsibleState.None);
        this.serverPort = serverPort;
    }
}
