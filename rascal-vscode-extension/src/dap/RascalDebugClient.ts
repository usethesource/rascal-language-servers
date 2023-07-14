import { debug, DebugConfiguration, DebugSession, Terminal, window } from "vscode";
import { RascalDebugAdapterDescriptorFactory } from "./RascalDebugAdapterDescriptorFactory";
import { RascalDebugConfigurationProvider } from "./RascalDebugConfigurationProvider";


/**
 * Debug Client that stores running debug sessions and available REPL ports for debug sessions.
 */
export class RascalDebugClient {
    rascalDescriptorFactory: RascalDebugAdapterDescriptorFactory;
    debugSocketServersPorts: Map<number, number>; // Terminal processID -> socket server port for debug
    runningDebugSessionsPorts: Set<number>; // Stores all running debug session server ports

    constructor(){
        this.rascalDescriptorFactory = new RascalDebugAdapterDescriptorFactory();
        this.debugSocketServersPorts = new Map<number, number>();
        this.runningDebugSessionsPorts = new Set<number>();

        debug.registerDebugConfigurationProvider("rascalmpl", new RascalDebugConfigurationProvider(this));
        debug.registerDebugAdapterDescriptorFactory("rascalmpl", this.rascalDescriptorFactory);

        window.onDidCloseTerminal(async (terminal: Terminal) => {
            const processId = await terminal.processId;
            if(processId !== undefined && this.debugSocketServersPorts.has(processId)){
                this.debugSocketServersPorts.delete(processId);
            }
        });

        debug.onDidStartDebugSession(async (debugsession: DebugSession) => {
            if(debugsession.configuration.serverPort !== undefined && typeof debugsession.configuration.serverPort === "number"){
                this.runningDebugSessionsPorts.add(debugsession.configuration.serverPort);
            }
        });

        debug.onDidTerminateDebugSession(async (debugsession: DebugSession) => {
            if(debugsession.configuration.serverPort !== undefined && typeof debugsession.configuration.serverPort === "number" 
            && this.runningDebugSessionsPorts.has(debugsession.configuration.serverPort)){
                this.runningDebugSessionsPorts.delete(debugsession.configuration.serverPort);
            }
        });

    }

    async startDebuggingSession(serverPort: number){
        const conf: DebugConfiguration = {type: "rascalmpl", name: "Rascal Debug", request: "attach", serverPort: serverPort};
        debug.startDebugging(undefined, conf);
    }

    registerDebugServerPort(processID: number, serverPort: number){
        this.debugSocketServersPorts.set(processID, serverPort);
    }

    getServerPort(processID: number | undefined){
        if(processID !== undefined && this.debugSocketServersPorts.has(processID)){
            return this.debugSocketServersPorts.get(processID);
        }
        return undefined;
    }

    isConnectedToDebugServer(serverPort: number){
        return this.runningDebugSessionsPorts.has(serverPort);
    }

}
