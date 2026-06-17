/*
 * Copyright (c) 2018-2025, NWO-I CWI and Swat.engineering
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
import { debug, DebugConfiguration, DebugSession, Terminal, window, EventEmitter, commands } from "vscode";
import { RascalDebugAdapterDescriptorFactory } from "./RascalDebugAdapterDescriptorFactory";
import { RascalDebugConfigurationProvider } from "./RascalDebugConfigurationProvider";


/**
 * Debug Client that stores running debug sessions and available REPL ports for debug sessions.
 */
export class RascalDebugClient {
    rascalDescriptorFactory: RascalDebugAdapterDescriptorFactory;
    debugSocketServersPorts: Map<number, number>; // Terminal processID -> socket server port for debug
    runningDebugSessionsPorts: Set<number>; // Stores all running debug session server ports

    private portEventEmitter = new EventEmitter<{processId: number, serverPort: number}>();
    readonly portRegistrationEvent = this.portEventEmitter.event;

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
            await this.updateTerminalViewButton();
        });

        debug.onDidStartDebugSession(async (debugsession: DebugSession) => {
            const port = getDebugPort(debugsession);
            if(port !== undefined){
                this.runningDebugSessionsPorts.add(port);
            }
            await this.updateTerminalViewButton();
        });

        debug.onDidTerminateDebugSession(async (debugsession: DebugSession) => {
            const port = getDebugPort(debugsession);
            if(port !== undefined){
                this.runningDebugSessionsPorts.delete(port);
            }
            await this.updateTerminalViewButton();
        });

        window.onDidChangeActiveTerminal(async _ => {
            await this.updateTerminalViewButton();
        });
    }

    async updateTerminalViewButton() {
        let isRascalTerminal = false;
        let isRascalTerminalDebugging = false;
        const activeTerminal = window.activeTerminal;
        if (activeTerminal) {
            isRascalTerminal = activeTerminal.name.includes("Rascal terminal");
            const processId = await activeTerminal.processId;
            if (isRascalTerminal && processId) {
                const serverPort = this.debugSocketServersPorts.get(processId);
                if (serverPort) {
                    isRascalTerminalDebugging = this.runningDebugSessionsPorts.has(serverPort);
                }
            }
        }
        await commands.executeCommand('setContext', 'rascalmpl.isRascalTerminalActive', isRascalTerminal);
        await commands.executeCommand('setContext', 'rascalmpl.activeRascalTerminalIsDebugging', isRascalTerminalDebugging);
    }

    async startDebuggingSession(serverPort: number){
        const conf: DebugConfiguration = {type: "rascalmpl", name: "Rascal debugger", request: "attach", serverPort: serverPort};
        await debug.startDebugging(undefined, conf);
    }

    registerDebugServerPort(processID: number, serverPort: number){
        this.debugSocketServersPorts.set(processID, serverPort);
        this.portEventEmitter.fire({"processId": processID, "serverPort": serverPort});
    }

    getServerPort(processId: number){
        return this.debugSocketServersPorts.get(processId);
    }

    isConnectedToDebugServer(serverPort: number){
        return this.runningDebugSessionsPorts.has(serverPort);
    }

}

function getDebugPort(session: DebugSession) {
    const port = session.configuration['serverPort'];
    if (port && typeof port === 'number') {
        return port;
    }
    return undefined;

}
