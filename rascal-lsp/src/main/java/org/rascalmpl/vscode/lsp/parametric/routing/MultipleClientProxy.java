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

import static org.rascalmpl.vscode.lsp.util.concurrent.CompletableFutureUtils.NOOP;

import java.net.URI;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
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
    private static final Supplier<CompletableFuture<Map<Object, List<Registration>>>> EMPTY_REGISTRATIONS = () -> CompletableFuture.completedFuture(new HashMap<>());

    private final IBaseLanguageClient client;
    private final ExecutorService exec;

    /**
     * The current registrations.
     * Map of method names to current registrations.
     * The inner map is keyed by registration options, with a list of registrations with those exact options. The first registration in this list is always registered with the client, while the others are kept for internal administration.
     */
    private final Map<String, CompletableFuture<Map<Object, List<Registration>>>> registrations = new ConcurrentHashMap<>();

    protected MultipleClientProxy(LanguageClient client, ExecutorService exec) {
        this.client = (IBaseLanguageClient) client;
        this.exec = exec;
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

    private static CompletableFuture<Map<Object, List<Registration>>> computeIfAbsent(@Nullable CompletableFuture<Map<Object, List<Registration>>> f) {
        return Objects.requireNonNullElseGet(f, EMPTY_REGISTRATIONS);
    }

    private CompletableFuture<Void> registerCapability(Registration r) {
        logger.trace("Incoming registration request for {}", r.getMethod());
        var res = registrations.compute(r.getMethod(), (method, f) ->
            computeIfAbsent(f).thenCompose(currentRegs -> {
                var equalOptRegs = currentRegs.computeIfAbsent(r.getRegisterOptions(), m -> new LinkedList<>());
                if (!equalOptRegs.isEmpty()) {
                    // This capability was already registered with these exact options.
                    // Do not do a duplicate registration with the actual client, since that will lead to an error.
                    // However, we do write down this registration for our own administration, in case we need it later.
                    logger.trace("This exact capability was registered with the client before - we ignore it for now: {}", r);
                    equalOptRegs.add(r);
                    return CompletableFuture.completedStage(currentRegs);
                }

                logger.trace("Registering {} with the client: {}", method, r);
                return client.registerCapability(new RegistrationParams(List.of(r)))
                    .thenAccept(v -> equalOptRegs.add(r))
                    .handle((v, t) -> {
                        if (t != null) {
                            logger.error("Exception while registering {}: {}", method, r, t);
                            equalOptRegs.remove(r);
                        }
                        return currentRegs;
                    });
            }));

        if (res == null) {
            return NOOP;
        }
        return res.thenAccept(v -> {}); // convert to Void
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

    private CompletableFuture<Void> unregisterCapability(Unregistration u) {
        var res = registrations.compute(u.getMethod(), (method, f) ->
            computeIfAbsent(f).thenCompose(currentRegs -> {
                for (var entry: currentRegs.entrySet()) {
                    var unreg = entry.getValue().stream().filter(r -> matches(r, u)).findAny();
                    if (!unreg.isPresent()) {
                        continue;
                    }

                    var regs = entry.getValue();
                    var idx = regs.indexOf(unreg.get());
                    if (idx != 0) {
                        // This registration is not registered with the client.
                        regs.remove(idx);
                        logger.trace("Ignoring registration for {} ({}), since it is still supported by other languages.", method, u.getId());
                        return CompletableFuture.completedFuture(currentRegs);
                    }

                    // This registration is registered with the client
                    // Unregister it and remove it from our local administration.
                    logger.trace("Unregistering {}: {}", method, u);
                    return client.unregisterCapability(new UnregistrationParams(List.of(u)))
                        .thenCompose(v -> {
                            regs.remove(idx);
                            if (!regs.isEmpty()) {
                                // We have more registrations from remotes, that the client does not know about.
                                // Since we just unregistered this method, we register the next in line again.
                                var reg = regs.get(0);
                                logger.trace("Re-registering {}, since other servers still support it: {}", method, reg);
                                return client.registerCapability(new RegistrationParams(List.of(reg)));
                            }
                            return NOOP;
                        })
                        .thenApply(v -> currentRegs);
                }

                // We received an unregistration for a registration that we do not know
                // TODO Throw an error?
                return CompletableFuture.completedFuture(currentRegs);
            })
        );

        if (res == null) {
            return NOOP;
        }
        return res.thenAccept(v -> {}); // convert to Void
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
