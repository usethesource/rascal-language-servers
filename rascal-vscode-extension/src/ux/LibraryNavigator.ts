/*
 * Copyright (c) 2018-2021, NWO-I CWI and Swat.engineering
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
import { buildMFChildPath } from './RascalMFValidator';
import {posix} from 'path'; // posix path join is always correct, also on windows
import { LanguageClient } from 'vscode-languageclient/node';

export class RascalLibraryProvider implements vscode.TreeDataProvider<RascalLibNode> {
    private changeEmitter = new vscode.EventEmitter<RascalLibNode | undefined>();
    readonly onDidChangeTreeData = this.changeEmitter.event;

    constructor(private readonly rascalClient: Promise<LanguageClient>) {
        vscode.workspace.onDidChangeWorkspaceFolders(_e => {
            this.changeEmitter.fire(undefined);
        });
    }

    getTreeItem(element: RascalLibNode): vscode.TreeItem | Thenable<vscode.TreeItem> {
        return element;
    }
    getChildren(element?: RascalLibNode | undefined): vscode.ProviderResult<RascalLibNode[]> {
        if (element === undefined) {
            return getRascalProjects(this.rascalClient);
        }
        return element.getChildren();
    }
    getParent(element: RascalLibNode): vscode.ProviderResult<RascalLibNode> {
        return element.parent;
    }

}

abstract class RascalLibNode extends vscode.TreeItem {
    constructor(resourceUriOrLabel: vscode.Uri | string, collapsibleState: vscode.TreeItemCollapsibleState | undefined, readonly parent: RascalLibNode | undefined) {
        /* overloaded constructors are strange in typescript */
        super(<never>resourceUriOrLabel, collapsibleState);
    }

    abstract getChildren(): vscode.ProviderResult<RascalLibNode[]>;

}

enum PathConfigMode {
    interpreter = 0,
    compiler = 1
}
class RascalProjectRoot extends RascalLibNode {
    constructor(readonly name: string, readonly loc: vscode.Uri, readonly rascalClient: Promise<LanguageClient>) {
        super(name, vscode.TreeItemCollapsibleState.Collapsed, undefined);
        this.id = `$project__${name}`;
        this.iconPath = new vscode.ThemeIcon("outline-view-icon");
    }

    async getChildren(): Promise<RascalLibNode[]> {
        const paths = await (await this.rascalClient).sendRequest<[string, string[]][]>("rascal/supplyPathConfig", { uri: this.loc.toString(), mode: PathConfigMode.interpreter});
        return paths.map(([n, u]) => new RascalPathNode(n, u, this));
    }
}

class RascalPathNode extends RascalLibNode {
    constructor(pathConfigPart: string, readonly subPaths: string[], parent: RascalLibNode) {
        super(pathConfigPart, vscode.TreeItemCollapsibleState.Collapsed, parent);
        this.id = `$lib__${parent.id}__${pathConfigPart}`;
        this.iconPath = new vscode.ThemeIcon("list-tree");
    }

    getChildren(): vscode.ProviderResult<RascalLibNode[]> {
        return this.subPaths.map(s => {
            if (s.startsWith("/")) {
                return vscode.Uri.file(s);
            }
            return vscode.Uri.parse(s);
        })
        .map(u => new RascalLibraryRoot(u.toString(), u, this));
    }
}

class RascalLibraryRoot extends RascalLibNode {
    constructor(label: string, readonly actualLocation: vscode.Uri, parent: RascalLibNode) {
        super(label, vscode.TreeItemCollapsibleState.Collapsed, parent);
        this.resourceUri = actualLocation;
        this.iconPath = new vscode.ThemeIcon("library");
    }

    getChildren(): vscode.ProviderResult<RascalLibNode[]> {
        let loc = this.actualLocation;
        if (loc.scheme === "file" && loc.path.endsWith(".jar")) {
            // we want to be able to jump into the jar, so let's change it to a rascal uri
            loc = loc.with({scheme: 'jar+file', path: loc.path + "!/"});
        }
        return getChildren(loc, this);
    }
}

async function getChildren(loc: vscode.Uri, parent: RascalLibNode): Promise < RascalLibNode[] > {
    const result: RascalLibNode[] = [];
    try {
        const children = await vscode.workspace.fs.readDirectory(loc);
        for (const [f, t] of children) {
            if (t === vscode.FileType.Directory) {
                result.push(new RascalFSDirEntry(f, loc, parent));
            }
            else if (f.endsWith(".rsc")) {
                result.push(new RascalFSFileEntry(f, loc, parent));
            }
        }
    } catch (_not_resolved) {
        console.log(_not_resolved);
        // swallow
    }
    return result;
}




class RascalFSDirEntry extends RascalLibNode {
    constructor(readonly dirName: string, parentDir: vscode.Uri, parent: RascalLibNode) {
        super(vscode.Uri.joinPath(parentDir, dirName), vscode.TreeItemCollapsibleState.Collapsed, parent);
        //this.iconPath = new vscode.ThemeIcon("file-directory");
    }

    getChildren(): vscode.ProviderResult<RascalLibNode[]> {
        // eslint-disable-next-line @typescript-eslint/no-non-null-assertion
        return getChildren(this.resourceUri!, this);
    }
}

class RascalFSFileEntry extends RascalLibNode {
    constructor(readonly fileName: string, readonly parentDir: vscode.Uri, parent: RascalLibNode) {
        super(vscode.Uri.joinPath(parentDir, fileName), vscode.TreeItemCollapsibleState.None, parent);
        this.command = <vscode.Command>{command:"vscode.open", title: "Open Rascal file", arguments: [this.resourceUri]};
    }

    getChildren(): vscode.ProviderResult<RascalLibNode[]> {
        return undefined;
    }
}

async function getRascalProjects(rascalClient: Promise<LanguageClient>): Promise<RascalLibNode[]> {
    const result: RascalLibNode[] = [];
    for (const wf of vscode.workspace.workspaceFolders || []) {
        try {
            await vscode.workspace.fs.stat(buildMFChildPath(wf.uri));
            const projectName = posix.basename(wf.uri.path);
            result.push(new RascalProjectRoot(projectName, wf.uri, rascalClient));
        }
        catch (_missingFile) {
            // ignore
        }
    }
    return result;
}
