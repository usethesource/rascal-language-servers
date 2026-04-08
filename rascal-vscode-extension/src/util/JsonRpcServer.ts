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
import * as rpc from 'vscode-jsonrpc/node';
import * as net from 'net';
import { Disposable, LogOutputChannel } from 'vscode';

/**
 * Json-rpc server that starts a server on a dynamic port
 */
export class JsonRpcServer implements Disposable {
    readonly serverPort: Promise<number>;
    private readonly server: net.Server;
    private activeClients: net.Socket[] = [];

    constructor(name: string, connectHandlers: (connection: rpc.MessageConnection) => Disposable, logger: LogOutputChannel) {
        this.server = net.createServer({noDelay: true}, newClient => {
            logger.info(`${name}: new connection ${JSON.stringify(newClient.address())}`);
            newClient.setNoDelay(true);
            const connection = rpc.createMessageConnection(newClient, newClient, {
                log: msg => logger.info(`${name}: ${msg}`),
                error: msg => logger.error(`${name}: ${msg}`),
                warn: msg => logger.warn(`${name}: ${msg}`),
                info: msg =>  logger.info(`${name}: ${msg}`)
            });
            newClient.on("error", e => logger.error(`${name} (client): ${e}`));
            this.activeClients.push(newClient);

            const disposables = connectHandlers(connection);

            newClient.on('end', () => {
                const index = this.activeClients.indexOf(newClient, 0);
                if (index >= 0) {
                    this.activeClients.splice(index, 1);
                }
                disposables.dispose();
                newClient.destroy();
            });

            connection.listen();
        });
        this.server.on('error', e => logger.error(`${name} (server): ${e}`));
        this.serverPort = new Promise((r, e) => {
            this.server.listen(0, "localhost", undefined, () => {
                const address = this.server.address();
                if (address && typeof(address) !== "string" && 'port' in address) {
                    logger.debug(`${name}: listening on ${JSON.stringify(address)}`);
                    r(address.port);
                } else {
                    e(new Error(`${name}: server address not valid: ${JSON.stringify(address)}`));
                }
            });
        });
    }

    dispose() {
        this.server.close();
        this.activeClients.forEach(c => c.destroy());
    }
}
