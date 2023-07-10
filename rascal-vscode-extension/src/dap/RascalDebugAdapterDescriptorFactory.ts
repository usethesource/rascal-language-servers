import { DebugAdapterDescriptor, DebugAdapterDescriptorFactory, DebugAdapterExecutable, DebugAdapterServer, DebugSession, ProviderResult } from "vscode";

export class RascalDebugAdapterDescriptorFactory implements DebugAdapterDescriptorFactory {

    serverPort: number;

    constructor(){
        this.serverPort = 0;
    }

    public setServerPort(serverPort: number){
        this.serverPort = serverPort;
    }

    public createDebugAdapterDescriptor(session: DebugSession, executable: DebugAdapterExecutable | undefined): ProviderResult<DebugAdapterDescriptor> {
        return new DebugAdapterServer(this.serverPort, "127.0.0.1");
    }

}
