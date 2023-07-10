import { log } from "console";
import {CancellationToken, DebugConfiguration, DebugConfigurationProvider, ProviderResult, WorkspaceFolder} from "vscode";

export class RascalDebugConfigurationProvider implements DebugConfigurationProvider {

    provideDebugConfigurations(folder: WorkspaceFolder | undefined, token?: CancellationToken | undefined): ProviderResult<DebugConfiguration[]> {
        log("provideDebugConfiguration")
        const conf: DebugConfiguration = {type: "rascalmpl", name: "Rascal Debug", request: "attach"};
        return [conf];
    }

    resolveDebugConfiguration(folder: WorkspaceFolder | undefined, debugConfiguration: DebugConfiguration, token?: CancellationToken | undefined): ProviderResult<DebugConfiguration> {
        log("ask for resolve");

        if (this.isEmptyDebugConfig(debugConfiguration)) {
            log("set up config");
            debugConfiguration.type = "rascalmpl";
            debugConfiguration.name = "Rascal Debug";
            debugConfiguration.request = "attach";
            debugConfiguration.__origin = "internal";
        }

        return debugConfiguration;
    }

    resolveDebugConfigurationWithSubstitutedVariables(folder: WorkspaceFolder | undefined, debugConfiguration: DebugConfiguration, token?: CancellationToken | undefined): ProviderResult<DebugConfiguration> {
        log("resolve with substituedVariables");
        return debugConfiguration;
    }
    
    // https://github.com/microsoft/vscode-java-debug/blob/main/src/configurationProvider.ts#L544
    private isEmptyDebugConfig(config: DebugConfiguration): boolean {
        return Object.keys(config).filter((key: string) => key !== "noDebug").length === 0;
    }

}