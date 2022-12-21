import * as vscode from 'vscode';
import { RascalExtension } from '../RascalExtension';
import { buildMFChildPath } from './RascalMFValidator';
import {posix} from 'path'; // posix path join is always correct, also on windows

export class RascalLibraryProvider implements vscode.TreeDataProvider<RascalLibNode> {
    private changeEmitter = new vscode.EventEmitter<RascalLibNode | undefined>();
    readonly onDidChangeTreeData = this.changeEmitter.event;

    constructor(private readonly rascal: RascalExtension) {
        vscode.workspace.onDidChangeWorkspaceFolders(e => {
            this.changeEmitter.fire(undefined);
        });
    }

    getTreeItem(element: RascalLibNode): vscode.TreeItem | Thenable<vscode.TreeItem> {
        return element;
    }
    getChildren(element?: RascalLibNode | undefined): vscode.ProviderResult<RascalLibNode[]> {
        if (element === undefined) {
            return getRascalProjects();
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
        super(<any>resourceUriOrLabel, collapsibleState);
    }

    abstract getChildren(): vscode.ProviderResult<RascalLibNode[]>;

}


class RascalProjectRoot extends RascalLibNode {
    constructor(readonly name: string, readonly loc: vscode.Uri) {
        super(name, vscode.TreeItemCollapsibleState.Collapsed, undefined);
        this.id = `$project__${name}`;
        this.iconPath = new vscode.ThemeIcon("outline-view-icon");
    }

    getChildren(): vscode.ProviderResult<RascalLibNode[]> {
        return [
            new RascalLibraryRoot("rascal", this.loc, this),
            new RascalLibraryRoot("rascal-lsp", this.loc, this),
        ];
    }
}

class RascalLibraryRoot extends RascalLibNode {
    private readonly rascalLoc: vscode.Uri;
    constructor(readonly libName: string, readonly projectLoc: vscode.Uri, parent: RascalLibNode) {
        super(`lib://${libName}`, vscode.TreeItemCollapsibleState.Collapsed, parent);
        this.rascalLoc = vscode.Uri.from({ scheme: "lib", authority: libName, path: "/"});
        this.id = `$lib__${parent.id}__${libName}`;
        this.iconPath = new vscode.ThemeIcon("library");
    }

    getChildren(): vscode.ProviderResult<RascalLibNode[]> {
        return getChildren(this.rascalLoc, this);
    }
}

async function getChildren(loc: vscode.Uri, parent: RascalLibNode): Promise < RascalLibNode[] > {
    const result: RascalLibNode[] = [];
    try {
        console.log(loc);
        const children = await vscode.workspace.fs.readDirectory(loc);
        console.log(children);
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
        super(parentDir.with({path: posix.join(parentDir.path, dirName)}), vscode.TreeItemCollapsibleState.Collapsed, parent);
        //this.iconPath = new vscode.ThemeIcon("file-directory");
    }

    getChildren(): vscode.ProviderResult<RascalLibNode[]> {
        return getChildren(this.resourceUri!, this);
    }
}

class RascalFSFileEntry extends RascalLibNode {
    constructor(readonly fileName: string, readonly parentDir: vscode.Uri, parent: RascalLibNode) {
        super(parentDir.with({path: posix.join(parentDir.path, fileName)}), vscode.TreeItemCollapsibleState.None, parent);
        this.command = <vscode.Command>{command:"vscode.open", title: "Open Rascal file", arguments: [this.resourceUri]};
    }

    getChildren(): vscode.ProviderResult<RascalLibNode[]> {
        return undefined;
    }
}

async function getRascalProjects(): Promise<RascalLibNode[]> {
    const result: RascalLibNode[] = [];
    for (const wf of vscode.workspace.workspaceFolders || []) {
        try {
            await vscode.workspace.fs.stat(buildMFChildPath(wf.uri));
            const projectName = posix.basename(wf.uri.path);
            result.push(new RascalProjectRoot(projectName, wf.uri));
        }
        catch (_missingFile) {
            // ignore
        }
    }
    return result;
}

