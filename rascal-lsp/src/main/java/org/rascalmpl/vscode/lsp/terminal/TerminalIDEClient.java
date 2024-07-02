/*
 * Copyright (c) 2018-2023, NWO-I CWI and Swat.engineering
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
import java.io.PrintWriter;
import java.io.Reader;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.util.concurrent.ExecutionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.lsp4j.ShowDocumentParams;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.rascalmpl.debug.IRascalMonitor;
import org.rascalmpl.ideservices.IDEServices;
import org.rascalmpl.library.Prelude;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.BrowseParameter;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.DocumentEditsParameter;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.LanguageParameter;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.RegisterDiagnosticsParameters;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.RegisterLocationsParameters;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.SourceLocationParameter;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.UnRegisterDiagnosticsParameters;
import org.rascalmpl.vscode.lsp.util.locations.ColumnMaps;
import org.rascalmpl.vscode.lsp.util.locations.Locations;
import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.IMap;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;

/**
 * This class provides IDE services to a Rascal REPL by
 * remote procedure invocation on a server which is embedded
 * into the LSP Rascal language server. That server forwards
 * the request to the Rascal IDE client (@see TerminalIDEServer)
 */
public class TerminalIDEClient implements IDEServices {
    private final ITerminalIDEServer server;
    private static final Logger logger = LogManager.getLogger(TerminalIDEClient.class);
    private final ColumnMaps columns = new ColumnMaps(this::getContents);
    private final IRascalMonitor monitor;

    public TerminalIDEClient(int port, IRascalMonitor monitor) throws IOException {
        @SuppressWarnings("java:S2095") // we don't have to close the socket, we are passing it off to the lsp4j framework
        Socket socket = new Socket(InetAddress.getLoopbackAddress(), port);
        socket.setTcpNoDelay(true);
        Launcher<ITerminalIDEServer> launch = new Launcher.Builder<ITerminalIDEServer>()
            .setRemoteInterface(ITerminalIDEServer.class)
            .setLocalService(this)
            .setInput(socket.getInputStream())
            .setOutput(socket.getOutputStream())
            .create();
        launch.startListening();
        server = launch.getRemoteProxy();
        this.monitor = monitor;
    }

    @Override
    public PrintWriter stderr() {
        assert false: "this method should not be used";
        return new PrintWriter(System.err);
    }

    @Override
    public void browse(URI uri, String title, int viewColumn) {
        server.browse(new BrowseParameter(uri.toString(), title, viewColumn));
    }

    @Override
    public void edit(ISourceLocation path) {
        try {
            ISourceLocation physical = URIResolverRegistry.getInstance().logicalToPhysical(path);
            ShowDocumentParams params = new ShowDocumentParams(physical.getURI().toASCIIString());
            params.setTakeFocus(true);

            if (physical.hasOffsetLength()) {
                params.setSelection(Locations.toRange(physical, columns));
            }

            server.edit(params);
        } catch (IOException e) {
            logger.info("ignored edit of {} because {}", path, e);
        }
    }

    private String getContents(ISourceLocation file) {
        try (Reader src = URIResolverRegistry.getInstance().getCharacterReader(file.top())) {
            return Prelude.consumeInputStream(src);
        }
        catch (IOException e) {
            logger.error("Error opening file {} to get contents", file, e);
            return "";
        }
    }

    @Override
    public ISourceLocation resolveProjectLocation(ISourceLocation input) {
        try {
            return server.resolveProjectLocation(new SourceLocationParameter(input))
                .get()
                .getLocation();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return input;
        } catch (ExecutionException e) {
            logger.error("Failed to resolve project location: {}", input, e.getCause());
            return input;
        }
    }

    @Override
    public void registerLanguage(IConstructor language) {
        server.receiveRegisterLanguage(LanguageParameter.fromRascalValue(language));
    }


    @Override
    public void unregisterLanguage(IConstructor language) {
        server.receiveUnregisterLanguage(LanguageParameter.fromRascalValue(language));
    }

    @Override
    public void applyDocumentsEdits(IList edits) {
        server.applyDocumentEdits(new DocumentEditsParameter(edits));
    }

    @Override
    public void jobStart(String name, int workShare, int totalWork) {
        monitor.jobStart(name, workShare, totalWork);
    }

    @Override
    public void jobStep(String name, String message, int inc) {
        monitor.jobStep(name, message, inc);
    }

    @Override
    public int jobEnd(String name, boolean succeeded) {
        return monitor.jobEnd(name, succeeded);
    }

    @Override
    public void endAllJobs() {
        monitor.endAllJobs();
    }

    @Override
    public boolean jobIsCanceled(String name) {
        return monitor.jobIsCanceled(name);
    }

    @Override
    public void jobTodo(String name, int work) {
        monitor.jobTodo(name, work);
    }

    @Override
    public void warning(String message, ISourceLocation src) {
        monitor.warning(message, src);
    }

    @Override
    public void registerLocations(IString scheme, IString auth, IMap map) {
        // register the map both on the LSP server side, for handling links and stuff,
        // locally here in the terminal, for local IO:
        server.registerLocations(new RegisterLocationsParameters(scheme, auth, map));
        IDEServices.super.registerLocations(scheme, auth, map);
    }

    @Override
    public void registerDiagnostics(IList messages) {
        server.registerDiagnostics(new RegisterDiagnosticsParameters(messages));
    }

    @Override
    public void unregisterDiagnostics(IList resources) {
        server.unregisterDiagnostics(new UnRegisterDiagnosticsParameters(resources));
    }

    public void startDebuggingSession(int serverPort){
        server.startDebuggingSession(serverPort);
    }

    public void registerDebugServerPort(int processID, int serverPort){
        server.registerDebugServerPort(processID, serverPort);
    }

}
