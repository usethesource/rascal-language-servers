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
package org.rascalmpl.vscode.lsp;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Stack;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import org.apache.logging.log4j.Logger;
import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.ProgressParams;
import org.eclipse.lsp4j.ShowDocumentParams;
import org.eclipse.lsp4j.WorkDoneProgressCreateParams;
import org.eclipse.lsp4j.WorkDoneProgressEnd;
import org.eclipse.lsp4j.WorkDoneProgressReport;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.rascalmpl.exceptions.RuntimeExceptionFactory;
import org.rascalmpl.ideservices.IDEServices;
import org.rascalmpl.repl.REPLContentServer;
import org.rascalmpl.repl.REPLContentServerManager;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.values.functions.IFunction;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.BrowseParameter;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.LanguageParameter;
import org.rascalmpl.vscode.lsp.util.DocumentChanges;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.IValue;

/**
 * This server forwards IDE services requests by a Rascal terminal
 * directly to the LSP language client.
 */
public class LSPIDEServices implements IDEServices {
    private final Logger logger;

    private final IBaseLanguageClient languageClient;
    private final DocumentChanges docChanges;
    private final Stack<String> jobs = new Stack<>();

    private final REPLContentServerManager contentManager = new REPLContentServerManager();

    public LSPIDEServices(IBaseLanguageClient client, IBaseTextDocumentService docService, Logger logger) {
        this.languageClient = client;
        this.docChanges = new DocumentChanges(docService);
        this.logger = logger;
    }

    @Override
    public void browse(URI uri) {
        languageClient.showContent(new BrowseParameter(uri.toString()));
    }

    @Override
    public void edit(ISourceLocation path) {
        languageClient.showDocument(new ShowDocumentParams(path.getURI().toString()));
    }

    @Override
    public ISourceLocation resolveProjectLocation(ISourceLocation input) {
        String projectName = input.getAuthority();

        try {
            return languageClient.workspaceFolders()
                .thenApply(fs -> fs.stream().filter(f -> f.getName().equals(projectName)).findAny())
                .thenApply(x -> x.isPresent() ? buildProjectChildLoc(x.get(), input) : input)
                .get();
        } catch (InterruptedException | ExecutionException e) {
            logger.catching(e);
            return input;
        }
    }

    private ISourceLocation buildProjectChildLoc(WorkspaceFolder folder, ISourceLocation input) {
        try {
            ISourceLocation root = URIUtil.createFromURI(folder.getUri());
            return URIUtil.getChildLocation(root, input.getPath());
        } catch (URISyntaxException e) {
            logger.catching(e);
            return input;
        }
    }

    @Override
    public void registerLanguage(IConstructor language) {
        LanguageParameter param = new LanguageParameter(
            language.get(0).toString(),
            ((IString) language.get(1)).getValue(),
            ((IString) language.get(2)).getValue(),
            ((IString) language.get(3)).getValue(),
            ((IString) language.get(4)).getValue()
        );

        languageClient.receiveRegisterLanguage(param);
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
        languageClient.applyEdit(new ApplyWorkspaceEditParams(new WorkspaceEdit(docChanges.translateDocumentChanges(edits))));
    }

    @Override
    public void jobStart(String name, int workShare, int totalWork) {
        languageClient.createProgress(new WorkDoneProgressCreateParams(Either.forLeft(name)));
    }

    @Override
    public void jobStep(String name, int workShare) {
        languageClient.notifyProgress(
            new ProgressParams(
                    Either.forLeft(jobs.pop()),
                    Either.forLeft(new WorkDoneProgressReport()))
                );
    }

    @Override
    public int jobEnd(boolean succeeded) {
        if (jobs.size() > 0) {
                languageClient.notifyProgress(
                    new ProgressParams(
                        Either.forLeft(jobs.pop()),
                        Either.forLeft(new WorkDoneProgressEnd())
                    ));
                return 1;
        }
        else {
            return 1;
        }
    }

    @Override
    public void jobTodo(int work) {
        // TODO
    }

    @Override
    public boolean jobIsCanceled() {
        // TODO
        return false;
    }

    @Override
    public void warning(String message, ISourceLocation src) {
        logger.warn("{} : {}", src, message);
    }
}
