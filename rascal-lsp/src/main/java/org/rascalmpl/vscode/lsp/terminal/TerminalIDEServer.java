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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.vscode.lsp.IBaseLanguageClient;

import io.usethesource.vallang.ISourceLocation;

/**
 * This server forwards IDE services requests by a Rascal terminal
 * directly to the LSP language client.
 */
public class TerminalIDEServer implements ITerminalIDEServer {
    private static final Logger logger = LogManager.getLogger(TerminalIDEServer.class);

    private final IBaseLanguageClient languageClient;

    public TerminalIDEServer(IBaseLanguageClient client) {
        this.languageClient = client;
    }

    @Override
    public CompletableFuture<Void> browse(BrowseParameter uri) {
        logger.trace("browse({})", uri);
        languageClient.showContent(uri);
        return CompletableFuture.completedFuture(null);
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
        try {
            ISourceLocation input = loc.getLocation();

            for (WorkspaceFolder folder : languageClient.workspaceFolders().get()) {
                // TODO check if everything goes ok encoding-wise
                if (folder.getName().equals(input.getAuthority())) {
                    ISourceLocation root = URIUtil.createFromURI(folder.getUri());
                    ISourceLocation newLoc = URIUtil.getChildLocation(root, input.getPath());
                    return CompletableFuture.completedFuture(new SourceLocationParameter(newLoc));
                }
            }

            return CompletableFuture.completedFuture(loc);
        }
        catch (URISyntaxException | InterruptedException | ExecutionException e) {
            logger.error(e);
            return CompletableFuture.completedFuture(loc);
        }
    }

    @Override
    public CompletableFuture<Void> receiveRegisterLanguage(LanguageParameter lang) {
        // we forward the request from the terminal to register a language
        // straight into the client:
        languageClient.receiveRegisterLanguage(lang);
        return CompletableFuture.completedFuture(null);
    }

}
