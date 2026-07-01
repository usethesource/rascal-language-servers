/*
 * Copyright (c) 2018-2025, NWO-I CWI and Swat.engineering
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
package org.rascalmpl.vscode.lsp.parametric.routing;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse;
import org.eclipse.lsp4j.ConfigurationParams;
import org.eclipse.lsp4j.LogTraceParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.ProgressParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.ShowDocumentParams;
import org.eclipse.lsp4j.ShowDocumentResult;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.TextDocumentContentRefreshParams;
import org.eclipse.lsp4j.Unregistration;
import org.eclipse.lsp4j.UnregistrationParams;
import org.eclipse.lsp4j.WorkDoneProgressCreateParams;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.services.LanguageClient;
import org.rascalmpl.uri.remote.jsonrpc.ISourceLocationChanged;
import org.rascalmpl.vscode.lsp.IBaseLanguageClient;
import org.rascalmpl.vscode.lsp.parametric.LanguageRegistry.LanguageParameter;
import org.rascalmpl.vscode.lsp.util.concurrent.CompletableFutureUtils;

import io.usethesource.vallang.IInteger;
import io.usethesource.vallang.IString;

/**
 * Client proxy implementation that aggregates results from multiple servers before forwarding to its own client.
 */
public class MultipleClientProxy implements IBaseLanguageClient {

    private static final Logger logger = LogManager.getLogger(MultipleClientProxy.class);

    private final IBaseLanguageClient client;
    private final ExecutorService exec;
    private final CompletableFuture<Void> noop;

    private final Map<String, Set<Registration>> currentRegistrations = new ConcurrentHashMap<>();


    protected MultipleClientProxy(LanguageClient client, ExecutorService exec) {
        this.client = (IBaseLanguageClient) client;
        this.exec = exec;
        this.noop = CompletableFutureUtils.completedFuture(null, exec);
    }

    @Override
    public void telemetryEvent(Object object) {
        client.telemetryEvent(object);
    }

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
        client.publishDiagnostics(diagnostics);
    }

    @Override
    public void showMessage(MessageParams messageParams) {
        client.showMessage(messageParams);
    }

    @Override
    public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
        return client.showMessageRequest(requestParams);
    }

    @Override
    public void logMessage(MessageParams message) {
        client.logMessage(message);
    }

    @Override
    public void showContent(URI uri, IString title, IInteger viewColumn) {
        client.showContent(uri, title, viewColumn);
    }

    @Override
    public void receiveRegisterLanguage(LanguageParameter lang) {
        logger.debug("rascal/receiveRegisterLanguage({}, {})", lang.getName(), lang.getMainFunction());
        client.receiveRegisterLanguage(lang);
    }

    @Override
    public void receiveUnregisterLanguage(LanguageParameter lang) {
        logger.debug("rascal/receiveUnregisterLanguage({}, {})", lang.getName(), lang.getMainFunction());
        client.receiveUnregisterLanguage(lang);
    }

    @Override
    public void editDocument(URI uri, @Nullable Range range, int viewColumn) {
        client.editDocument(uri, range, viewColumn);
    }

    @Override
    public void startDebuggingSession(int serverPort) {
        client.startDebuggingSession(serverPort);
    }

    @Override
    public void registerDebugServerPort(int processID, int serverPort) {
        client.registerDebugServerPort(processID, serverPort);
    }

    @Override
    public CompletableFuture<Void> createProgress(WorkDoneProgressCreateParams params) {
        return client.createProgress(params);
    }

    @Override
    public void notifyProgress(ProgressParams params) {
        client.notifyProgress(params);
    }

    @Override
    public CompletableFuture<ApplyWorkspaceEditResponse> applyEdit(ApplyWorkspaceEditParams params) {
        return client.applyEdit(params);
    }

    @Override
    public CompletableFuture<List<Object>> configuration(ConfigurationParams configurationParams) {
        return client.configuration(configurationParams);
    }

    @Override
    public void logTrace(LogTraceParams params) {
        client.logTrace(params);
    }

    @Override
    public CompletableFuture<Void> refreshCodeLenses() {
        return client.refreshCodeLenses();
    }

    @Override
    public CompletableFuture<Void> refreshDiagnostics() {
        return client.refreshDiagnostics();
    }

    @Override
    public CompletableFuture<Void> refreshFoldingRanges() {
        return client.refreshFoldingRanges();
    }

    @Override
    public CompletableFuture<Void> refreshInlayHints() {
        return client.refreshInlayHints();
    }

    @Override
    public CompletableFuture<Void> refreshInlineValues() {
        return client.refreshInlineValues();
    }

    @Override
    public CompletableFuture<Void> refreshSemanticTokens() {
        return client.refreshSemanticTokens();
    }

    @Override
    public CompletableFuture<Void> refreshTextDocumentContent(TextDocumentContentRefreshParams params) {
        return client.refreshTextDocumentContent(params);
    }

    @Override
    public CompletableFuture<ShowDocumentResult> showDocument(ShowDocumentParams params) {
        return client.showDocument(params);
    }

    @Override
    public CompletableFuture<Void> registerCapability(RegistrationParams params) {
        return CompletableFutureUtils
            .reduce(params
                .getRegistrations()
                .parallelStream()
                .map(this::registerCapability), exec)
            .thenAccept(v -> {}); // convert to Void
    }

    private CompletableFuture<Void> registerCapability(Registration r) {
        var c = currentRegistrations.computeIfAbsent(r.getMethod(), k -> new CopyOnWriteArraySet<>());
        // Lock on the registrations for this method for a moment
        synchronized (c) {
            var similarRegOpt = c.stream().filter(rr -> Objects.equals(rr.getRegisterOptions(), r.getRegisterOptions())).findAny();
            if (similarRegOpt.isPresent()) {
                logger.trace("A registration for {} with the same options already exists; ignoring this one", r.getMethod());
                logger.trace("{} vs. {}", similarRegOpt.get(), r);
                return noop;
            }

            c.add(r);
            return client.registerCapability(new RegistrationParams(List.of(r)))
                .exceptionally(t -> {
                    logger.error("Exception while registering {}", r,  t);
                    c.remove(r);
                    return null;
                });
        }
    }

    @Override
    public CompletableFuture<Void> unregisterCapability(UnregistrationParams params) {
        return CompletableFutureUtils
            .reduce(params
                .getUnregisterations()
                .parallelStream()
                .map(this::unregisterCapability), exec)
            .thenAccept(v -> {}); // convert to Void
    }

    private boolean matches(Registration r, Unregistration u) {
        return r.getId().equals(u.getId())
            && r.getMethod().equals(u.getMethod());
    }

    private Predicate<Registration> matches(Unregistration u) {
        return r -> matches(r, u);
    }

    private CompletableFuture<Void> unregisterCapability(Unregistration u) {
        var c = currentRegistrations.get(u.getMethod());
        if (c == null) {
            return noop;
        }

        synchronized (c) {
            var cs = c.stream().filter(matches(u)).collect(Collectors.toSet());
            if (cs.isEmpty()) {
                logger.trace("No registrations for {} exist; ignoring this request: {}", u.getMethod(), u);
                // No registrations => nothing to unregister
                return noop;
            }

            c.removeAll(cs);
            return client.unregisterCapability(new UnregistrationParams(List.of(u)))
                .exceptionally(t -> {
                    logger.error("Exception while unregistering {} ({})", u.getMethod(), u, t);
                    c.addAll(cs);
                    return null;
                });
        }
    }

    @Override
    public CompletableFuture<List<WorkspaceFolder>> workspaceFolders() {
        return client.workspaceFolders();
    }

    @Override
    public void sourceLocationChanged(ISourceLocationChanged changed) {
        client.sourceLocationChanged(changed);
    }

}
