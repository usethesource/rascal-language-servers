package org.rascalmpl.vscode.lsp.dap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.lsp4j.debug.launch.DSPLauncher;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.rascalmpl.debug.DebugHandler;
import org.rascalmpl.interpreter.Evaluator;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class RascalDebugAdapterLauncher {

    static ServerSocket serverSocket;

    public static DebugHandler startDebugServer(Evaluator evaluator) {
        try {
            serverSocket = new ServerSocket(8889);
            DebugHandler debugHandler = new DebugHandler();
            RascalDebugAdapter server = new RascalDebugAdapter(debugHandler, evaluator);

            Thread t = new Thread() {
                public void run() {
                    try {
                        Socket socket = serverSocket.accept();

                        Launcher<IDebugProtocolClient> launcher = DSPLauncher.createServerLauncher(server, socket.getInputStream(), socket.getOutputStream());
                        server.connect(launcher.getRemoteProxy());
                        launcher.startListening();
                    } catch (IOException e) {
                        final Logger logger = LogManager.getLogger(RascalDebugAdapterLauncher.class);
                        logger.fatal(e.getMessage(), e);
                        throw new RuntimeException(e);
                    }
                }
            };
            t.setDaemon(true);
            t.start();
            return debugHandler;
        } catch (IOException e) {
            final Logger logger = LogManager.getLogger(RascalDebugAdapterLauncher.class);
            logger.fatal(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    public static void stopDebugServer() {
        try {
            serverSocket.close();
        } catch (IOException e) {
            final Logger logger = LogManager.getLogger(RascalDebugAdapterLauncher.class);
            logger.fatal(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
