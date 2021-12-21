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

import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Stack;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.Logger;
import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.ProgressParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowDocumentParams;
import org.eclipse.lsp4j.WorkDoneProgressCreateParams;
import org.eclipse.lsp4j.WorkDoneProgressEnd;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.rascalmpl.ideservices.IDEServices;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.BrowseParameter;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.LanguageParameter;
import org.rascalmpl.vscode.lsp.util.Diagnostics;
import org.rascalmpl.vscode.lsp.util.DocumentChanges;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.IMap;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.IValue;
import jline.internal.Log;

/**
 * This server forwards IDE services requests by a Rascal terminal
 * directly to the LSP language client.
 */
public class LSPIDEServices implements IDEServices {
    private final Logger logger;

    private final IBaseLanguageClient languageClient;
    private final DocumentChanges docChanges;
    private final IBaseTextDocumentService docService;
    private final ThreadLocal<Deque<String>> activeProgress = ThreadLocal.withInitial(ArrayDeque::new);

    public LSPIDEServices(IBaseLanguageClient client, IBaseTextDocumentService docService, Logger logger) {
        this.languageClient = client;
        this.docChanges = new DocumentChanges(docService);
        this.docService = docService;
        this.logger = logger;
    }

    @Override
    public PrintWriter stderr() {
        assert false: "this should not be used here";
        return new PrintWriter(System.out);
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
    public void applyDocumentsEdits(IList edits) {
        languageClient.applyEdit(new ApplyWorkspaceEditParams(new WorkspaceEdit(docChanges.translateDocumentChanges(edits))));
    }

    private CompletableFuture<Void> tryRegisterProgress(String id) {
       return languageClient.createProgress(new WorkDoneProgressCreateParams(Either.forLeft(id)));
    }

    public CompletableFuture<Void> createProgressBar(String id) {
        return tryRegisterProgress(id)
            .thenApply(CompletableFuture::completedFuture)
            .exceptionally(t -> retry(t, 0, id))
            .thenCompose(Function.identity());
    }
    private CompletableFuture<Void> retry(Throwable first, int retry, String id) {
        if(retry >= 100) return failedFuture(first);
        return tryRegisterProgress(id)
            .thenApply(CompletableFuture::completedFuture)
            .exceptionally(t -> { first.addSuppressed(t); return retry(first, retry+1, id); })
            .thenCompose(Function.identity());
    }

    public static <T> CompletableFuture<T> failedFuture(Throwable t) {
        final CompletableFuture<T> cf = new CompletableFuture<>();
        cf.completeExceptionally(t);
        return cf;
    }


    private final ThreadLocal<CompletableFuture<Void>> progressBarRegistration
        = new ThreadLocal<>();

    @Override
    public void jobStart(String name, int workShare, int totalWork) {
        Deque<String> progress = activeProgress.get();
        String id = getProgressId();
        if (progress.isEmpty()) {
            logger.info("Creating new progress bar: {} {} {}", id, name, progress);
            // we are the first one, so we have to create a new progress bar
            // first we request the progress bar
            progressBarRegistration.set(
                createProgressBar(id)
                .thenRun(() -> {
                    logger.info("Valid initialized progress bar: {}", id);
                    // then we initialize it it
                    languageClient.notifyProgress(
                        new ProgressParams(
                                Either.forLeft(id),
                                Either.forRight(buildProgressBeginObject(name))
                    ));
                })
            );
        }
        else {
            CompletableFuture<Void> current = progressBarRegistration.get();
            if (current == null) {
                logger.error("Unexpected empty registration");
                return;
            }
            // other wise we have to update the progress bar to a new message
            current.thenRun(() -> languageClient.notifyProgress(
                new ProgressParams(
                    Either.forLeft(id),
                    Either.forRight(buildProgressStepObject(name)))));

        }
        progress.push(name);
    }

    private String getProgressId() {
        Thread t = Thread.currentThread();
        return "T" + Integer.toHexString(t.hashCode()) + "" + Long.toHexString(t.getId()) + "" + Integer.toHexString(System.identityHashCode(activeProgress.get()));

    }

    private Object buildProgressBeginObject(String name) {
        JsonObject result = new JsonObject();
        result.addProperty("kind", "begin");
        result.addProperty("title", name);
        result.addProperty("cancellable", false);
        return result;
    }


    private Object buildProgressStepObject(String message) {
        JsonObject result = new JsonObject();
        result.addProperty("kind", "report");
        result.addProperty("message", message);
        //result.addProperty("percentage", percentage);
        return result;
    }

    @Override
    public void jobStep(String name, String message, int workShare) {
        String topName = activeProgress.get().peekFirst();
        if (!Objects.equals(topName, name)) {
            logger.warn("Incorrect jobstep for non-top job, got {} expected {}", topName, name);
            return;
        }
        CompletableFuture<Void> current = progressBarRegistration.get();
        if (current == null) {
            logger.error("Unexpected empty registration");
            return;
        }
        current.thenRun(() -> languageClient.notifyProgress(
            new ProgressParams(
                    Either.forLeft(getProgressId()),
                    Either.forRight(buildProgressStepObject(message))
        )));
    }

    @Override
    public int jobEnd(String name, boolean succeeded) {
        String topName = activeProgress.get().peekFirst();
        if (!Objects.equals(topName, name)) {
            logger.warn("Incorrect jobstep for non-top job, got {} expected {}", topName, name);
            return 1;
        }
        activeProgress.get().pollFirst();

        if (activeProgress.get().isEmpty()) {
            logger.info("Finished progress bar: {}", name);
            // bottom of the stack, done with progress
                CompletableFuture<Void> current = progressBarRegistration.get();
                if (current == null) {
                    logger.error("Unexpected empty registration");
                    return 1;
                }
                current
                .handle((e,r) -> null) // always close, also in case of exception
                .thenRun(() -> languageClient.notifyProgress(
                    new ProgressParams(
                        Either.forLeft(getProgressId()),
                        Either.forLeft(new WorkDoneProgressEnd())
                )));
        }
        return 1;
    }

    @Override
    public void jobTodo(String name, int work) {
        // TODO
    }

    @Override
    public boolean jobIsCanceled(String name) {
        // TODO
        return false;
    }

    @Override
    public void warning(String message, ISourceLocation src) {
        logger.warn("{} : {}", src, message);
    }

    @Override
    public void registerLocations(IString scheme, IString auth, IMap map) {
        IDEServices.super.registerLocations(scheme, auth, map);
    }

    @Override
    public void registerDiagnostics(IList messages) {
        Map<ISourceLocation, List<Diagnostic>> translated = Diagnostics.translateMessages(messages, docService);

        for (Entry<ISourceLocation, List<Diagnostic>> entry : translated.entrySet()) {
            String uri = entry.getKey().getURI().toString();
            languageClient.publishDiagnostics(new PublishDiagnosticsParams(uri, entry.getValue()));
        }
    }

    @Override
    public void unregisterDiagnostics(IList resources) {
        for (IValue elem : resources) {
            ISourceLocation loc = (ISourceLocation) elem;
            languageClient.publishDiagnostics(new PublishDiagnosticsParams(loc.getURI().toString(), Collections.emptyList()));
        }
    }

}
