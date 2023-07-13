package org.rascalmpl.vscode.lsp.dap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.lsp4j.debug.launch.DSPLauncher;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.rascalmpl.debug.DebugHandler;
import org.rascalmpl.interpreter.Evaluator;

import java.io.*;
import java.net.Socket;

public class RascalDebugAdapterLauncher {

    public static IDebugProtocolClient start(Evaluator evaluator, Socket clientSocket, DebugSocketServer socketServer) {
        try {
            final DebugHandler debugHandler = new DebugHandler();

            debugHandler.setTerminateAction(new Runnable() {
                @Override
                public void run() {
                    evaluator.removeSuspendTriggerListener(debugHandler);
                    try {
                        Thread.sleep(1000);
                        socketServer.disconnectClient();
                    } catch (InterruptedException e) {
                        final Logger logger = LogManager.getLogger(RascalDebugAdapterLauncher.class);
                        logger.fatal(e.getMessage(), e);
                        throw new RuntimeException(e);
                    }
                }
            });
            evaluator.addSuspendTriggerListener(debugHandler);

            RascalDebugAdapter server = new RascalDebugAdapter(debugHandler, evaluator);
            Launcher<IDebugProtocolClient> launcher = DSPLauncher.createServerLauncher(server, clientSocket.getInputStream(), clientSocket.getOutputStream());
            server.connect(launcher.getRemoteProxy());
            launcher.startListening();
            return launcher.getRemoteProxy();
        } catch (IOException e) {
            final Logger logger = LogManager.getLogger(RascalDebugAdapterLauncher.class);
            logger.fatal(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
