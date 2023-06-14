package org.rascalmpl.vscode.lsp.dap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.lsp4j.debug.launch.DSPLauncher;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.rascalmpl.debug.AbstractInterpreterEventTrigger;
import org.rascalmpl.debug.DebugHandler;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutionException;

public class RascalDebugAdapterLauncher {


    public static void startDebugServer(AbstractInterpreterEventTrigger eventTrigger, DebugHandler debugHandler) {
        try {
            ServerSocket serverSocket = new ServerSocket(8889);
            RascalDebugAdapterServer server = new RascalDebugAdapterServer(eventTrigger, debugHandler);

            Socket socket = serverSocket.accept();
                    
            Launcher<IDebugProtocolClient> launcher = DSPLauncher.createServerLauncher(server, socket.getInputStream(), socket.getOutputStream());
            server.connect(launcher.getRemoteProxy());
            launcher.startListening();
        } catch (Throwable e) {
            final Logger logger = LogManager.getLogger(RascalDebugAdapterLauncher.class);
            logger.fatal(e.getMessage(), e);
        }
    }
}
