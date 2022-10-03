/*
 * Copyright (c) 2018-2021, NWO-I CWI and Swat.engineering
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
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.util.concurrent.ExecutionException;

import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ShowDocumentParams;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.rascalmpl.exceptions.RuntimeExceptionFactory;
import org.rascalmpl.ideservices.IDEServices;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.BrowseParameter;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.DocumentEditsParameter;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.EditParameter;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.JobEndParameter;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.JobStartParameter;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.JobStepParameter;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.LanguageParameter;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.RegisterDiagnosticsParameters;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.RegisterLocationsParameters;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.SourceLocationParameter;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.UnRegisterDiagnosticsParameters;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.WarningMessage;

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

    public TerminalIDEClient(int port) throws IOException {
        Socket socket = new Socket(InetAddress.getByName("127.0.0.1"), port);
        Launcher<ITerminalIDEServer> launch = new Launcher.Builder<ITerminalIDEServer>()
            .setRemoteInterface(ITerminalIDEServer.class)
            .setLocalService(this)
            .setInput(socket.getInputStream())
            .setOutput(socket.getOutputStream())
            .create();
        launch.startListening();
        server = launch.getRemoteProxy();
    }

    @Override
    public PrintWriter stderr() {
        assert false: "this method should not be used";
        return new PrintWriter(System.err);
    }

    @Override
    public void browse(URI uri) {
        server.browse(new BrowseParameter(uri.toString()));
    }

    @Override
    public void edit(ISourceLocation path) {
        ShowDocumentParams params = new ShowDocumentParams(path.getURI().toASCIIString());
        params.setTakeFocus(true);
        // TODO: add optional offset & length as range
       server.edit(params);
    }

    @Override
    public ISourceLocation resolveProjectLocation(ISourceLocation input) {
        try {
            return server.resolveProjectLocation(new SourceLocationParameter(input))
                .get()
                .getLocation();
        } catch (InterruptedException | ExecutionException e) {
            return input;
        }
    }

    @Override
    public void registerLanguage(IConstructor language) {
        server.receiveRegisterLanguage(
            new LanguageParameter(
                language.get(0).toString(),
                ((IString) language.get(1)).getValue(),
                ((IString) language.get(2)).getValue(),
                ((IString) language.get(3)).getValue(),
                ((IString) language.get(4)).getValue()
            )
        );
    }


    @Override
    public void unregisterLanguage(IConstructor language) {
        server.receiveUnregisterLanguage(
            new LanguageParameter(
                language.get(0).toString(),
                ((IString) language.get(1)).getValue(),
                ((IString) language.get(2)).getValue(),
                ((IString) language.get(3)).getValue(),
                ((IString) language.get(4)).getValue()
            )
        );
    }


    @Override
    public void applyDocumentsEdits(IList edits) {
        server.applyDocumentEdits(new DocumentEditsParameter(edits));
    }

    @Override
    public void jobStart(String name, int workShare, int totalWork) {
        server.jobStart(new JobStartParameter(name, workShare, totalWork));
    }

    @Override
    public void jobStep(String name, String message, int inc) {
        server.jobStep(new JobStepParameter(name, message, inc));
    }

    @Override
    public int jobEnd(String name, boolean succeeded) {
        try {
             server.jobEnd(new JobEndParameter(name, succeeded)).get().getAmount();
             return 1;
        } catch (InterruptedException | ExecutionException e) {
            throw RuntimeExceptionFactory.io(e.getMessage());
        }
    }

    @Override
    public boolean jobIsCanceled(String name) {
        try {
            return server.jobIsCanceled().get().isTrue();
        } catch (InterruptedException e) {
            return true;
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    @Override
    public void jobTodo(String name, int work) {
        // server.jobTodo(new AmountOfWork(work));
    }

    @Override
    public void warning(String message, ISourceLocation src) {
        server.warning(new WarningMessage(message, src));
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

    @Override
    public void logMessage(IConstructor msg) {
        server.logMessage(new MessageParams(getMessageType(msg), getMessageString(msg)));
    }

    @Override
    public void showMessage(IConstructor msg) {
        server.showMessage(new MessageParams(getMessageType(msg), getMessageString(msg)));
    }

    private String getMessageString(IConstructor msg) {
        return ((IString) msg.get("msg")).getValue();
    }

    private MessageType getMessageType(IConstructor msg) {
        MessageType type = null;
        switch (msg.getName()) {
            case "error":
                type = MessageType.Error;
                break;
            case "warning":
                type = MessageType.Warning;
                break;
            case "info":
                type = MessageType.Info;
                break;
            default:
                type = MessageType.Log;
                break;
        }
        return type;
    }
}
