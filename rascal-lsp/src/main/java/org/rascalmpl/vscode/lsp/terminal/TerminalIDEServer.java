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

import java.net.URISyntaxException;
import java.util.Stack;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ProgressParams;
import org.eclipse.lsp4j.WorkDoneProgressCreateParams;
import org.eclipse.lsp4j.WorkDoneProgressEnd;
import org.eclipse.lsp4j.WorkDoneProgressReport;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.vscode.lsp.IBaseLanguageClient;
import org.rascalmpl.vscode.lsp.IBaseTextDocumentService;
import org.rascalmpl.vscode.lsp.util.DocumentChanges;

import io.usethesource.vallang.IList;
import io.usethesource.vallang.ISourceLocation;

/**
 * This server forwards IDE services requests by a Rascal terminal
 * directly to the LSP language client.
 */
public class TerminalIDEServer implements ITerminalIDEServer {
    private static final Logger logger = LogManager.getLogger(TerminalIDEServer.class);

    private final IBaseLanguageClient languageClient;
    private final DocumentChanges docChanges;
    private final Stack<String> jobs = new Stack<>();

    public TerminalIDEServer(IBaseLanguageClient client, IBaseTextDocumentService docService) {
        this.languageClient = client;
        this.docChanges = new DocumentChanges(docService);
    }

    @Override
    public CompletableFuture<Void> browse(BrowseParameter uri) {
        logger.trace("browse({})", uri);
        return CompletableFuture.runAsync(() -> { languageClient.showContent(uri); });
    }

    @Override
    public CompletableFuture<Void> edit(EditParameter edit) {
        logger.trace("edit({})", edit);
        languageClient.showMessage(new MessageParams(MessageType.Info, "trying to edit: " + edit.getModule()));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<SourceLocationParameter> resolveProjectLocation(SourceLocationParameter loc) {
        logger.trace("resolveProjectLocation({})", loc);
        ISourceLocation input = loc.getLocation();
        String projectName = input.getAuthority();

        return languageClient.workspaceFolders()
            .thenApply(fs -> fs.stream().filter(f -> f.getName().equals(projectName)).findAny())
            .thenApply(x -> x.isPresent() ? buildProjectChildLoc(x.get(), input, loc) : loc);
    }

    private SourceLocationParameter buildProjectChildLoc(WorkspaceFolder folder, ISourceLocation input, SourceLocationParameter fallback) {
        try {
            ISourceLocation root = URIUtil.createFromURI(folder.getUri());
            ISourceLocation newLoc = URIUtil.getChildLocation(root, input.getPath());
            return new SourceLocationParameter(newLoc);
        } catch (URISyntaxException e) {
            logger.catching(e);
            return fallback;
        }
    }

    @Override
    public CompletableFuture<Void> receiveRegisterLanguage(LanguageParameter lang) {
        // we forward the request from the terminal to register a language
        // straight into the client:
        return CompletableFuture.runAsync(() -> {
            languageClient.receiveRegisterLanguage(lang);
        });
    }

    @Override
    public void showHTML(BrowseParameter content) {
        languageClient.showContent(content);
    }

    @Override
    public CompletableFuture<Void> applyDocumentEdits(DocumentEditsParameter edits) {
        IList list = edits.getEdits();

        return CompletableFuture.runAsync(() -> {
            languageClient.applyEdit(new ApplyWorkspaceEditParams(new WorkspaceEdit(docChanges.translateDocumentChanges(list))));
        });
    }

    @Override
    public CompletableFuture<Void> jobStart(JobStartParameter param) {
        // TODO does this have the intended semantics?
        jobs.push(param.getName());
        return languageClient.createProgress(new WorkDoneProgressCreateParams(Either.forLeft(param.getName())));
    }

    @Override
    public CompletableFuture<Void> jobStep(JobStepParameter param) {
        return CompletableFuture.supplyAsync(() -> {
            languageClient.notifyProgress(
                    new ProgressParams(
                            Either.forLeft(jobs.pop()),
                            Either.forLeft(new WorkDoneProgressReport()))
                    );
            return null;
        }
        );
    }

    @Override
    public CompletableFuture<AmountOfWork> jobEnd(BooleanParameter param) {
        if (jobs.size() > 0) {
            return CompletableFuture.supplyAsync(() -> {
                languageClient.notifyProgress(
                    new ProgressParams(
                        Either.forLeft(jobs.pop()),
                        Either.forLeft(new WorkDoneProgressEnd())
                    ));
                return new AmountOfWork(1);
            }
            );
        }
        else {
            return CompletableFuture.completedFuture(new AmountOfWork(1));
        }
    }

    @Override
    public CompletableFuture<Void> jobTodo(AmountOfWork param) {
         // TODO think of how to implement this
         return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<BooleanParameter> jobIsCanceled() {
        // TODO think of how to implement this
        return CompletableFuture.completedFuture(new BooleanParameter(false));
    }

    @Override
    public void warning(WarningMessage param) {
        languageClient.showMessage(new MessageParams(MessageType.Warning, param.getLocation() + ":" + param.getMessage()));
    }
}
