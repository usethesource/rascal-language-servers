/*
 * Copyright (c) 2018-2021, NWO-I CWI and Swat.engineering
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.rascalmpl.vscode.lsp;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer;
import org.rascalmpl.vscode.lsp.terminal.TerminalIDEServer;

public class IDEServicesThread extends Thread {
    private final IBaseLanguageClient ideClient;
    private final ServerSocket serverSocket;
    private static final Logger logger = LogManager.getLogger(IDEServicesThread.class);

    public IDEServicesThread(IBaseLanguageClient client, ServerSocket socket) {
        super("Terminal IDE Services Thread");
        setDaemon(true);
        this.serverSocket = socket;
        this.ideClient = client;
    }

    @Override
    public void run() {
        try {
            while(true) {
                try {
                    Socket connection = serverSocket.accept();

                    Launcher<ITerminalIDEServer> ideServicesServerLauncher = new Launcher.Builder<ITerminalIDEServer>()
                        .setLocalService(new TerminalIDEServer(ideClient))
                        .setRemoteInterface(ITerminalIDEServer.class) // TODO this should be an empty interface?
                        .setInput(connection.getInputStream())
                        .setOutput(connection.getOutputStream())
                        .setExceptionHandler(e -> {
                            logger.error(e);
                            return new ResponseError(ResponseErrorCode.InternalError, e.getMessage(), e);
                        })
                        .create();

                    logger.trace("Terminal IDE services starts listening");
                    ideServicesServerLauncher.startListening();
                }
                catch (Throwable e) {
                    logger.error("Making a connection for Terminal IDE services failed", e);
                }
            }
        }
        finally {
            try {
                serverSocket.close();
            } catch (IOException e) {
                logger.error(e);
            }
        }
    }

    /**
     * Start a server for remote IDE services. These are used
     * by an implementation of @see IDEServices that is given to Rascal terminal REPLS
     * when they are started in the context of the LSP. This way
     * Rascal REPLs get access to @see IDEServices to provide browsers
     * for interactive visualizations, starting editors and resolving IDE project URI.
     *
     * @return the port number that the IDE services are running on
     * @throws IOException when a new server socket can not be established.
     */
    public static IDEServicesConfiguration startIDEServices(IBaseLanguageClient client) {
        try {
            ServerSocket socket = new ServerSocket(0);

            new IDEServicesThread(client, socket).start();

            return new IDEServicesConfiguration(socket.getLocalPort());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
