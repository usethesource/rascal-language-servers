import { DebugAdapterDescriptor, DebugAdapterDescriptorFactory, DebugAdapterExecutable, DebugAdapterServer, DebugSession, ProviderResult } from "vscode";

export class RascalDebugAdapterDescriptorFactory implements DebugAdapterDescriptorFactory {

    // eslint-disable-next-line @typescript-eslint/no-empty-function
    constructor(){}

    createDebugAdapterDescriptor(session: DebugSession, executable: DebugAdapterExecutable | undefined): ProviderResult<DebugAdapterDescriptor> {
        return new DebugAdapterServer(8889, "127.0.0.1");
    }

}
