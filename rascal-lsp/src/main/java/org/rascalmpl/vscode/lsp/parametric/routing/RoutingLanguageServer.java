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
package org.rascalmpl.vscode.lsp.parametric.routing;

import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.rascalmpl.vscode.lsp.LanguageServerRouter;
import org.rascalmpl.vscode.lsp.parametric.LanguageRegistry.LanguageParameter;
import org.rascalmpl.vscode.lsp.parametric.ParametricLanguageServer;
import org.rascalmpl.vscode.lsp.util.NamedThreadPool;

public class RoutingLanguageServer extends ParametricLanguageServer {

    private static final Logger logger = LogManager.getLogger(RoutingLanguageServer.class);

    public static void startLanguageServer(ExecutorService requestPool, ExecutorService workerPool, int portNumber) {
        logger.info("Starting Rascal Language Server Router: {}", getVersion());
        printClassPath();

        if (DEPLOY_MODE) {
            startLSP(constructLSPClient(capturedIn, capturedOut, new LanguageServerRouter(() -> System.exit(0), workerPool), requestPool));
        }
        else {
            try (ServerSocket serverSocket = new ServerSocket(portNumber, 0, InetAddress.getByName("127.0.0.1"))) {
                logger.info("Rascal LSP server router listens on port number: {}", portNumber);
                while (true) {
                    startLSP(constructLSPClient(serverSocket.accept(), new LanguageServerRouter(() -> {}, workerPool), requestPool));
                }
            } catch (IOException e) {
                logger.fatal("Failure to start TCP server on port {}", portNumber, e);
            }
        }
    }

    public static void main(String[] args) {
        if (args.length > 0) {
            var dedicatedLanguage = new GsonBuilder().create().fromJson(args[0], LanguageParameter.class);
            start(DEFAULT_PORT_NUMBER, dedicatedLanguage);
        } else {
            startLanguageServer(NamedThreadPool.single("parametric-lsp-router-in")
                , NamedThreadPool.cached("parametric-router")
                , DEFAULT_PORT_NUMBER
            );
        }
    }
}
