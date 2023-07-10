import { debug, DebugConfiguration } from "vscode";
import { RascalDebugAdapterDescriptorFactory } from "./RascalDebugAdapterDescriptorFactory";
import { RascalDebugConfigurationProvider } from "./RascalDebugConfigurationProvider";

export class RascalDebugClient {
    rascalDescriptorFactory: RascalDebugAdapterDescriptorFactory;

    constructor(){
        this.rascalDescriptorFactory = new RascalDebugAdapterDescriptorFactory();
        debug.registerDebugConfigurationProvider("rascalmpl", new RascalDebugConfigurationProvider());
        debug.registerDebugAdapterDescriptorFactory("rascalmpl", this.rascalDescriptorFactory);
    }

    async startDebuggingSession(serverPort: number){
        this.rascalDescriptorFactory.setServerPort(serverPort);
        const conf: DebugConfiguration = {type: "rascalmpl", name: "Rascal Debug", request: "attach"};
        debug.startDebugging(undefined, conf);
    }

}
