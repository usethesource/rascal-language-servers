import { DebugAdapterDescriptor, DebugAdapterDescriptorFactory, DebugAdapterExecutable, DebugAdapterServer, DebugSession, ProviderResult} from "vscode";

/**
 * Class creating a DebugAdapterServer connection when starting a debug session
 */
export class RascalDebugAdapterDescriptorFactory implements DebugAdapterDescriptorFactory {

    createDebugAdapterDescriptor(session: DebugSession, executable: DebugAdapterExecutable | undefined): ProviderResult<DebugAdapterDescriptor> {
        return new DebugAdapterServer(session.configuration.serverPort, "127.0.0.1");
    }

}
