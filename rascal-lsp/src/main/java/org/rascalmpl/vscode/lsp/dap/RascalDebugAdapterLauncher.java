package org.rascalmpl.vscode.lsp.dap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.lsp4j.debug.launch.DSPLauncher;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.eclipse.lsp4j.jsonrpc.Launcher;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutionException;

public class RascalDebugAdapterLauncher {

    public static void main(String[] args) {
        try {
            ServerSocket serverSocket = new ServerSocket(8889);
            Socket socket = serverSocket.accept();
            startDebugServer(socket.getInputStream(), socket.getOutputStream());
        }
        catch (Throwable e) {
            final Logger logger = LogManager.getLogger(RascalDebugAdapterLauncher.class);
            logger.fatal(e.getMessage(), e);
        }
    }

    public static void startDebugServer(InputStream in, OutputStream out) throws InterruptedException, ExecutionException, URISyntaxException, IOException {
        RascalDebugAdapterServer server = new RascalDebugAdapterServer();
        Launcher<IDebugProtocolClient> launcher = DSPLauncher.createServerLauncher(server, in, out);
        server.connect(launcher.getRemoteProxy());
        launcher.startListening();
    }
}
