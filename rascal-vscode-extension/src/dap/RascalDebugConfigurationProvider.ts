import {CancellationToken, DebugConfiguration, DebugConfigurationProvider, ProviderResult, WorkspaceFolder, window} from "vscode";
import { RascalDebugClient } from "./RascalDebugClient";

export class RascalDebugConfigurationProvider implements DebugConfigurationProvider {

    debugClient: RascalDebugClient;

    constructor(debugClient: RascalDebugClient){
        this.debugClient = debugClient;
    }

    provideDebugConfigurations(folder: WorkspaceFolder | undefined, token?: CancellationToken | undefined): ProviderResult<DebugConfiguration[]> {
        const conf: DebugConfiguration = {type: "rascalmpl", name: "Rascal Debug", request: "attach"};
        return [conf];
    }

    async resolveDebugConfiguration(folder: WorkspaceFolder | undefined, debugConfiguration: DebugConfiguration, token?: CancellationToken | undefined): Promise<DebugConfiguration> {
        return new Promise((resolve, reject) => {
            if (debugConfiguration.type === undefined) {
                debugConfiguration.type = "rascalmpl";
                debugConfiguration.name = "Rascal Debug";
                debugConfiguration.request = "attach";
            }

            if (!debugConfiguration.serverPort){
                window.activeTerminal?.processId.then((value: number|undefined) => {
                    const port = this.debugClient.getServerPort(value);
                    if(port === undefined) {
                        reject("Active terminal has not a debug server port registered !");
                    } else {
                        if(this.debugClient.isConnectedToDebugServer(port)){
                            reject("This REPL has already a running debug session !");
                        } else {
                            debugConfiguration.serverPort = port;
                            resolve(debugConfiguration);
                        }
                    }
                }, (reason: any) => {
                    reject(reason);
                });
            } else {
                resolve(debugConfiguration);
            }
        });
    }

}