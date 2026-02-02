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
package org.rascalmpl.vscode.lsp.terminal;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.lsp4j.services.LanguageClient;
import org.rascalmpl.ideservices.GsonUtils;
import org.rascalmpl.ideservices.IRemoteIDEServices;
import org.rascalmpl.vscode.lsp.IBaseTextDocumentService;
import org.rascalmpl.vscode.lsp.IDEServicesConfiguration;

/**
 * Thread launcher for (remote) IDEServices, running within rascal-lsp
 */
public class RemoteIDEServicesThread extends Thread {
    private final ServerSocket serverSocket;
    private final LanguageClient languageClient;
    private final IBaseTextDocumentService docService;
    private final ExecutorService exec;

    public static final Logger logger = LogManager.getLogger(RemoteIDEServicesThread.class);

    public RemoteIDEServicesThread(ServerSocket serverSocket, LanguageClient languageClient, IBaseTextDocumentService docService, ExecutorService exec) {
        super("Remote IDE Services Thread");
        this.serverSocket = serverSocket;
        this.languageClient = languageClient;
        this.docService = docService;
        this.exec = exec;
    }

    @Override
    public void run() {
        try {
            while (true) {
                try {
                    Socket connection = serverSocket.accept();
                    connection.setTcpNoDelay(true);

                    Launcher<IRemoteIDEServices> remoteIDEServicesLauncher = new Launcher.Builder<IRemoteIDEServices>()
                        .setLocalService(new RemoteIDEServicesServer(languageClient, docService, exec))
                        .setRemoteInterface(IRemoteIDEServices.class)
                        .setInput(connection.getInputStream())
                        .setOutput(connection.getOutputStream())
                        .configureGson(GsonUtils::configureGson)
                        .setExecutorService(exec)
                        .setExceptionHandler(e -> {
                            logger.error(e);
                            return new ResponseError(ResponseErrorCode.InternalError, e.getMessage() == null ? "unknown" : e.getMessage(), e);
                        })
                        .create();

                    logger.trace("Remote IDE services thread started");
                    remoteIDEServicesLauncher.startListening();
                } catch (Throwable e) {
                    logger.error("Failed to start Remote IDE services thread");
                }
            }
        } finally {
            try {
                serverSocket.close();
            } catch (IOException e) {
                logger.error(e);
            }
        }
    }

    public static IDEServicesConfiguration startRemoteIDEServicesServer(LanguageClient languageClient, IBaseTextDocumentService docService, ExecutorService threadPool) {
        try {
            ServerSocket socket = new ServerSocket(0);
            new RemoteIDEServicesThread(socket, languageClient, docService, threadPool).start();
            return new IDEServicesConfiguration(socket.getLocalPort());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
