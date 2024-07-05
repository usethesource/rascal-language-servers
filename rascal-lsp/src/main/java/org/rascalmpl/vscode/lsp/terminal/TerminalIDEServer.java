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

import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ProgressParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowDocumentParams;
import org.eclipse.lsp4j.ShowDocumentResult;
import org.eclipse.lsp4j.WorkDoneProgressCreateParams;
import org.eclipse.lsp4j.WorkDoneProgressEnd;
import org.eclipse.lsp4j.WorkDoneProgressReport;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.rascalmpl.uri.LogicalMapResolver;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.vscode.lsp.BaseWorkspaceService;
import org.rascalmpl.vscode.lsp.IBaseLanguageClient;
import org.rascalmpl.vscode.lsp.IBaseTextDocumentService;
import org.rascalmpl.vscode.lsp.util.Diagnostics;
import org.rascalmpl.vscode.lsp.util.DocumentChanges;
import org.rascalmpl.vscode.lsp.util.locations.Locations;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValue;

/**
 * This server forwards IDE services requests by a Rascal terminal
 * directly to the LSP language client.
 */
public class TerminalIDEServer implements ITerminalIDEServer {
    private static final Logger logger = LogManager.getLogger(TerminalIDEServer.class);

    private final IBaseLanguageClient languageClient;
    private final DocumentChanges docChanges;
    private final Set<String> jobs = new HashSet<>();
    private final IBaseTextDocumentService docService;
    private final BaseWorkspaceService workspaceService;

    public TerminalIDEServer(IBaseLanguageClient client, IBaseTextDocumentService docService, BaseWorkspaceService workspaceService) {
        this.languageClient = client;
        this.workspaceService = workspaceService;
        this.docChanges = new DocumentChanges(docService);
        this.docService = docService;
    }

    @Override
    public CompletableFuture<Void> browse(BrowseParameter uri) {
        logger.trace("browse({})", uri);
        return CompletableFuture.runAsync(() -> { languageClient.showContent(uri); });
    }

    @Override
    public CompletableFuture<ShowDocumentResult> edit(ShowDocumentParams edit) {
        logger.trace("edit({})", edit);
        return languageClient.showDocument(edit);
    }

    @Override
    public CompletableFuture<SourceLocationParameter> resolveProjectLocation(SourceLocationParameter loc) {
        logger.trace("resolveProjectLocation({})", loc);
        ISourceLocation input = loc.getLocation();
        String projectName = input.getAuthority();

        for (var folder: workspaceService.workspaceFolders()) {
            if (folder.getName().equals(projectName)) {
                return CompletableFuture.completedFuture(buildProjectChildLoc(folder, input, loc));
            }
        }
        return CompletableFuture.completedFuture(loc);
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
    public CompletableFuture<Void> receiveUnregisterLanguage(LanguageParameter lang) {
        // we forward the request from the terminal to register a language
        // straight into the client:
        return CompletableFuture.runAsync(() -> {
            languageClient.receiveUnregisterLanguage(lang);
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
        if (!jobs.contains(param.getName())) {
            jobs.add(param.getName());
            return languageClient.createProgress(new WorkDoneProgressCreateParams(Either.forLeft(param.getName())));
        }
        else {
            logger.debug("job " + param.getName() + " was already running. ignored.");
            return CompletableFuture.completedFuture(null);
        }
    }

    @Override
    public CompletableFuture<Void> jobStep(JobStepParameter param) {
        if (jobs.contains(param.getName())) {
            return CompletableFuture.supplyAsync(() -> {
                languageClient.notifyProgress(
                        new ProgressParams(
                                Either.forLeft(param.getName()),
                                Either.forLeft(new WorkDoneProgressReport()))
                        );
                return null;
            }
            );
        }
        else {
            logger.debug("stepping a job that does not exist: " + param.getName());
            return CompletableFuture.completedFuture(null);
        }
    }

    @Override
    public CompletableFuture<AmountOfWork> jobEnd(JobEndParameter param) {
        if (jobs.contains(param.getName())) {
            jobs.remove(param.getName());
            return CompletableFuture.supplyAsync(() -> {
                languageClient.notifyProgress(
                    new ProgressParams(
                        Either.forLeft(param.getName()),
                        Either.forLeft(new WorkDoneProgressEnd())
                    ));
                return new AmountOfWork(1);
            }
            );
        }
        else {
            logger.debug("ended an non-existing job: " + param.getName());
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

    @Override
    public void registerLocations(RegisterLocationsParameters param) {
        URIResolverRegistry.getInstance().registerLogical(
            new LogicalMapResolver(
                param.getRawScheme(),
                param.getRawAuthority(),
                param.getMapping()
            )
        );
    }

    @Override
    public void registerDiagnostics(RegisterDiagnosticsParameters param) {
        Map<ISourceLocation, List<Diagnostic>> translated = Diagnostics.translateMessages(param.getMessages(), docService);

        for (Entry<ISourceLocation, List<Diagnostic>> entry : translated.entrySet()) {
            String uri = entry.getKey().getURI().toString();
            languageClient.publishDiagnostics(new PublishDiagnosticsParams(uri, entry.getValue()));
        }
    }

    @Override
    public void unregisterDiagnostics(UnRegisterDiagnosticsParameters param) {
        for (IValue elem : param.getLocations()) {
            ISourceLocation loc = Locations.toPhysicalIfPossible((ISourceLocation) elem);
            languageClient.publishDiagnostics(new PublishDiagnosticsParams(loc.getURI().toString(), Collections.emptyList()));
        }
    }

    @Override
    public void startDebuggingSession(int serverPort) {
        languageClient.startDebuggingSession(serverPort);
    }

    @Override
    public void registerDebugServerPort(int processID, int serverPort) {
        languageClient.registerDebugServerPort(processID, serverPort);
    }
}
