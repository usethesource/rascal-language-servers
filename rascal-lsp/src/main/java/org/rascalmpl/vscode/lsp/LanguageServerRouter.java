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

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.rascalmpl.uri.URIUtil;

import io.usethesource.vallang.ISourceLocation;

public class LanguageServerRouter extends BaseLanguageServer.ActualLanguageServer {

    private static final Logger logger = LogManager.getLogger(LanguageServerRouter.class);

    private final Map<String, String> languagesByExtension;
    private final Map<String, ISingleLanguageServer> languageServers;

    public LanguageServerRouter(Runnable onExit, ExecutorService exec) {
        super(onExit, exec, new RoutingTextDocumentService(), new RoutingWorkspaceService(exec));

        this.languagesByExtension = new ConcurrentHashMap<>();
        this.languageServers = new ConcurrentHashMap<>();
    }

    private ISingleLanguageServer route(ISourceLocation uri) {
        var lang = safeLanguage(uri).orElseThrow(() ->
            new UnsupportedOperationException(String.format("Rascal Parametric LSP has no support for this file, since no language is registered for extension '%s': %s", extension(uri), uri))
        );
        return languageByName(lang);
    }

    private ISingleLanguageServer languageByName(String lang) {
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

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        ((RoutingTextDocumentService) getTextDocumentService()).setRouter(this::route);
        ((RoutingWorkspaceService) getWorkspaceService()).setRouter(this::route);
        return super.initialize(params);
    }

}
