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
import java.util.concurrent.CompletableFuture;
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
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.ShowDocumentParams;
import org.eclipse.lsp4j.ShowDocumentResult;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.TextDocumentContentRefreshParams;
import org.eclipse.lsp4j.UnregistrationParams;
import org.eclipse.lsp4j.WorkDoneProgressCreateParams;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.rascalmpl.uri.remote.jsonrpc.ISourceLocationChanged;
import org.rascalmpl.vscode.lsp.IBaseLanguageClient;
import org.rascalmpl.vscode.lsp.parametric.LanguageRegistry.LanguageParameter;

import io.usethesource.vallang.IInteger;
import io.usethesource.vallang.IString;

/**
 * Client proxy implementation that aggregates results from multiple servers before forwarding to its own client.
 */
public class MultipleClientProxy implements IBaseLanguageClient, LanguageClientAware {

    private static final Logger logger = LogManager.getLogger(MultipleClientProxy.class);

    private IBaseLanguageClient client;

    @Override
    public void connect(LanguageClient client) {
        this.client = (IBaseLanguageClient) client;
    }

    protected IBaseLanguageClient availableClient() {
        if (client == null) {
            throw new IllegalStateException("Language Client has not been connected yet");
        }
        return client;
    }

    @Override
    public void telemetryEvent(Object object) {
        availableClient().telemetryEvent(object);
    }

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
        availableClient().publishDiagnostics(diagnostics);
    }

    @Override
    public void showMessage(MessageParams messageParams) {
        availableClient().showMessage(messageParams);
    }

    @Override
    public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
        return availableClient().showMessageRequest(requestParams);
    }

    @Override
    public void logMessage(MessageParams message) {
        availableClient().logMessage(message);
    }

    @Override
    public void showContent(URI uri, IString title, IInteger viewColumn) {
        availableClient().showContent(uri, title, viewColumn);
    }

    @Override
    public void receiveRegisterLanguage(LanguageParameter lang) {
        logger.debug("rascal/receiveRegisterLanguage({}, {})", lang.getName(), lang.getMainFunction());
        availableClient().receiveRegisterLanguage(lang);
    }

    @Override
    public void receiveUnregisterLanguage(LanguageParameter lang) {
        logger.debug("rascal/receiveUnregisterLanguage({}, {})", lang.getName(), lang.getMainFunction());
        availableClient().receiveUnregisterLanguage(lang);
    }

    @Override
    public void editDocument(URI uri, @Nullable Range range, int viewColumn) {
        availableClient().editDocument(uri, range, viewColumn);
    }

    @Override
    public void startDebuggingSession(int serverPort) {
        availableClient().startDebuggingSession(serverPort);
    }

    @Override
    public void registerDebugServerPort(int processID, int serverPort) {
        availableClient().registerDebugServerPort(processID, serverPort);
    }

    @Override
    public CompletableFuture<Void> createProgress(WorkDoneProgressCreateParams params) {
        return availableClient().createProgress(params);
    }

    @Override
    public void notifyProgress(ProgressParams params) {
        availableClient().notifyProgress(params);
    }

    @Override
    public CompletableFuture<ApplyWorkspaceEditResponse> applyEdit(ApplyWorkspaceEditParams params) {
        return availableClient().applyEdit(params);
    }

    @Override
    public CompletableFuture<List<Object>> configuration(ConfigurationParams configurationParams) {
        return availableClient().configuration(configurationParams);
    }

    @Override
    public void logTrace(LogTraceParams params) {
        availableClient().logTrace(params);
    }

    @Override
    public CompletableFuture<Void> refreshCodeLenses() {
        return availableClient().refreshCodeLenses();
    }

    @Override
    public CompletableFuture<Void> refreshDiagnostics() {
        return availableClient().refreshDiagnostics();
    }

    @Override
    public CompletableFuture<Void> refreshFoldingRanges() {
        return availableClient().refreshFoldingRanges();
    }

    @Override
    public CompletableFuture<Void> refreshInlayHints() {
        return availableClient().refreshInlayHints();
    }

    @Override
    public CompletableFuture<Void> refreshInlineValues() {
        return availableClient().refreshInlineValues();
    }

    @Override
    public CompletableFuture<Void> refreshSemanticTokens() {
        return availableClient().refreshSemanticTokens();
    }

    @Override
    public CompletableFuture<Void> refreshTextDocumentContent(TextDocumentContentRefreshParams params) {
        return client.refreshTextDocumentContent(params);
    }

    @Override
    public CompletableFuture<ShowDocumentResult> showDocument(ShowDocumentParams params) {
        return availableClient().showDocument(params);
    }

    @Override
    public CompletableFuture<Void> registerCapability(RegistrationParams params) {
        // TODO Collect/maintain capabilities of all delegate servers, combine, and unregister capabilities if necessary based on that.
        return availableClient().registerCapability(params);
    }

    @Override
    public CompletableFuture<Void> unregisterCapability(UnregistrationParams params) {
        // TODO Collect/maintain capabilities of all delegate servers, combine, and unregister capabilities if necessary based on that.
        return availableClient().unregisterCapability(params);
    }

    @Override
    public CompletableFuture<List<WorkspaceFolder>> workspaceFolders() {
        return availableClient().workspaceFolders();
    }

    @Override
    public void sourceLocationChanged(ISourceLocationChanged changed) {
        availableClient().sourceLocationChanged(changed);
    }

}
