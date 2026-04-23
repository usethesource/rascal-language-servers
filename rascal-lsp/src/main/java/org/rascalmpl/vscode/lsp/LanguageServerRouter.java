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
package org.rascalmpl.vscode.lsp;

import static org.rascalmpl.vscode.lsp.BaseLanguageServer.DEPLOY_MODE;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.vscode.lsp.parametric.LanguageRegistry.LanguageParameter;

import io.usethesource.vallang.IInteger;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;

public class LanguageServerRouter extends BaseLanguageServer.ActualLanguageServer implements IBaseLanguageClient {

    private static final Logger logger = LogManager.getLogger(LanguageServerRouter.class);

    private final Map<String, String> languagesByExtension;
    private final Map<String, CompletableFuture<IBaseLanguageServerExtensions>> languageServers;

    public LanguageServerRouter(Runnable onExit, ExecutorService exec) {
        super(onExit, exec, new RoutingTextDocumentService(exec), new RoutingWorkspaceService(exec));

        this.languagesByExtension = new ConcurrentHashMap<>();
        this.languageServers = new ConcurrentHashMap<>();
    }

    /*package*/ CompletableFuture<IBaseLanguageServerExtensions> languageByName(String lang) {
        var service = languageServers.get(lang);
        if (service == null) {
            throw new UnsupportedOperationException(String.format("Rascal Parametric LSP has no support for this file, since no language is registered with name '%s'", lang));
        }
        return service;
    }

    private Optional<String> safeLanguage(ISourceLocation loc) {
        var ext = extension(loc);
        if ("".equals(ext)) {
            var languages = new HashSet<>(languagesByExtension.values());
            if (languages.size() == 1) {
                logger.trace("File was opened without an extension; falling back to the single registered language for: {}", loc);
                return languages.stream().findFirst();
            } else {
                logger.error("File was opened without an extension and there are multiple languages registered, so we cannot pick a fallback for: {}", loc);
                return Optional.empty();
            }
        }
        return Optional.ofNullable(languagesByExtension.get(ext));
    }

    private static String extension(ISourceLocation doc) {
        return URIUtil.getExtension(doc);
    }

    private @Nullable CompletableFuture<IBaseLanguageServerExtensions> startServer(LanguageParameter lang) {
        try {
            var classPath = System.getProperty("java.class.path");
            InputStream in;
            OutputStream out;
            if (DEPLOY_MODE) {
                var proc = new ProcessBuilder("java"
                    , "-Dlog4j2.configurationFactory=org.rascalmpl.vscode.lsp.log.LogJsonConfiguration"
                    , "-Dlog4j2.level=DEBUG"
                    , "-Drascal.fallbackResolver=org.rascalmpl.vscode.lsp.uri.FallbackResolver"
                    , "-Drascal.lsp.deploy=" + DEPLOY_MODE
                    , "-Drascal.compilerClasspath=" + classPath
                    , "-cp" + classPath
                    , "org.rascalmpl.vscode.lsp.parametric.ParametricLanguageServer"
                ).start();
                in = proc.getInputStream();
                out = proc.getOutputStream();
            } else {
                // TODO Predictably increment ports for subsequent languages
                Socket socket = new Socket(InetAddress.getLoopbackAddress(), 9990);
                socket.setTcpNoDelay(true);
                in = socket.getInputStream();
                out = socket.getOutputStream();
            }
            var client = new Launcher.Builder<IBaseLanguageServerExtensions>()
                .setRemoteInterface(IBaseLanguageServerExtensions.class)
                .setLocalService(this)
                .setInput(in)
                .setOutput(out)
                .create();

            client.startListening();
            var server = client.getRemoteProxy();
            var delegateServerCaps = server.initialize(delegateInitializationParams());

            // When initialization is done, we can use the server
            return delegateServerCaps.thenApply(_c -> server);
        } catch (IOException e) {
            logger.fatal(e);
            return null;
        }
    }

    private InitializeParams delegateInitializationParams() {
        var params = new InitializeParams();
        return params;
    }

    public RoutingTextDocumentService getTextDocumentService() {
        return (RoutingTextDocumentService) super.getTextDocumentService();
    }

    public RoutingWorkspaceService getWorkspaceService() {
        return (RoutingWorkspaceService) super.getWorkspaceService();
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        getTextDocumentService().setServer(this);
        return super.initialize(params);
    }

    @Override
    public synchronized CompletableFuture<Void> sendRegisterLanguage(LanguageParameter lang) {
        // If we do not have a parametric server running for this language, start and initialize it.
        synchronized (this) {
            var remote = languageServers.computeIfAbsent(lang.getName(), name -> {
                for (var ext : lang.getExtensions()) {
                    languagesByExtension.put(ext, lang.getName());
                }
                return startServer(lang);
            });
        }

        return super.sendRegisterLanguage(lang);
    }

    @Override
    public synchronized CompletableFuture<Void> sendUnregisterLanguage(LanguageParameter lang) {
        // TODO Handle shutting down the remote parametric server iff it is empty now.
        return super.sendUnregisterLanguage(lang);
    }

    @Override
    public void telemetryEvent(Object object) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'telemetryEvent'");
    }

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'publishDiagnostics'");
    }

    @Override
    public void showMessage(MessageParams messageParams) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'showMessage'");
    }

    @Override
    public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'showMessageRequest'");
    }

    @Override
    public void logMessage(MessageParams message) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'logMessage'");
    }

    @Override
    public void showContent(URI uri, IString title, IInteger viewColumn) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'showContent'");
    }

    @Override
    public void receiveRegisterLanguage(LanguageParameter lang) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'receiveRegisterLanguage'");
    }

    @Override
    public void receiveUnregisterLanguage(LanguageParameter lang) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'receiveUnregisterLanguage'");
    }

    @Override
    public void editDocument(URI uri, @Nullable Range range, int viewColumn) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'editDocument'");
    }

    @Override
    public void startDebuggingSession(int serverPort) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'startDebuggingSession'");
    }

    @Override
    public void registerDebugServerPort(int processID, int serverPort) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'registerDebugServerPort'");
    }

}
