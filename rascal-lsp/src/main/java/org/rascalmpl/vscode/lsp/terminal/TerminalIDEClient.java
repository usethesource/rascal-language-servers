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
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.rascalmpl.exceptions.RuntimeExceptionFactory;
import org.rascalmpl.ideservices.IDEServices;
import org.rascalmpl.repl.REPLContentServer;
import org.rascalmpl.repl.REPLContentServerManager;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.AmountOfWork;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.BooleanParameter;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.BrowseParameter;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.DocumentEditsParameter;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.EditParameter;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.JobStartParameter;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.JobStepParameter;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.LanguageParameter;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.SourceLocationParameter;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.WarningMessage;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.IValue;
import org.rascalmpl.values.functions.IFunction;

/**
 * This class provides IDE services to a Rascal REPL by
 * remote procedure invocation on a server which is embedded
 * into the LSP Rascal language server. That server forwards
 * the request to the Rascal IDE client (@see TerminalIDEServer)
 */
public class TerminalIDEClient implements IDEServices {
    private final ITerminalIDEServer server;
    private final REPLContentServerManager contentManager = new REPLContentServerManager();

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
    public void browse(URI uri) {
        server.browse(new BrowseParameter(uri.toString()));
    }

    @Override
    public void edit(ISourceLocation path) {
       server.edit(new EditParameter(path.getPath()));
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
    public void showInteractiveContent(IConstructor content) {
        String id = ((IString) content.get("id")).getValue();
        Function<IValue, IValue> callback = (t) -> ((IFunction) content.get("callback")).call(t);
        try {
            REPLContentServer server = contentManager.addServer(id, callback);
            browse(URIUtil.assumeCorrect("http", "localhost:" + server.getListeningPort(), "/"));
        } catch (IOException e) {
            throw RuntimeExceptionFactory.io(e.getMessage());
        }
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
    public void jobStep(String name, int inc) {
        server.jobStep(new JobStepParameter(name, inc));
    }

    @Override
    public int jobEnd(boolean succeeded) {
        try {
             server.jobEnd(new BooleanParameter(succeeded)).get().getAmount();
             return 1;
        } catch (InterruptedException | ExecutionException e) {
            throw RuntimeExceptionFactory.io(e.getMessage());
        }
    }

    @Override
    public boolean jobIsCanceled() {
        try {
            return server.jobIsCanceled().get().isTrue();
        } catch (InterruptedException e) {
            return true;
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    @Override
    public void jobTodo(int work) {
        // server.jobTodo(new AmountOfWork(work));
    }

    @Override
    public void warning(String message, ISourceLocation src) {
        server.warning(new WarningMessage(message, src));
    }
}
