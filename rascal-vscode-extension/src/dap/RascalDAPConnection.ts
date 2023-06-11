import { debug, window } from "vscode";
import { RascalDebugAdapterDescriptorFactory } from "./RascalDebugAdapterDescriptorFactory";


export async function activateDebugAdapterClient() {
    const workspace = await window.showWorkspaceFolderPick();
    debug.startDebugging(workspace, "rascalmpl");
}

export function registerDebugAdapter(){
    debug.registerDebugAdapterDescriptorFactory("rascalmpl", new RascalDebugAdapterDescriptorFactory());
}