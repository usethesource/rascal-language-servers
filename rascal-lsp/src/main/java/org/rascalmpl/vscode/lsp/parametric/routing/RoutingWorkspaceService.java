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

import com.google.gson.JsonPrimitive;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.eclipse.lsp4j.CreateFilesParams;
import org.eclipse.lsp4j.DeleteFilesParams;
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.RenameFilesParams;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.rascalmpl.vscode.lsp.BaseWorkspaceService;
import org.rascalmpl.vscode.lsp.IBaseLanguageServerExtensions;
import org.rascalmpl.vscode.lsp.util.DocumentRouter;
import org.rascalmpl.vscode.lsp.util.concurrent.CompletableFutureUtils;
import org.rascalmpl.vscode.lsp.util.locations.Locations;

import io.usethesource.vallang.ISourceLocation;

/**
 * A language-parametric workspace service that routes incoming requests to remote dedicated language servers.
 */
public class RoutingWorkspaceService extends BaseWorkspaceService implements DocumentRouter<CompletableFuture<WorkspaceService>> {

    private @MonotonicNonNull DocumentRouter<CompletableFuture<IBaseLanguageServerExtensions>> serverRouter;

    public RoutingWorkspaceService(ExecutorService exec) {
        super(exec);
    }

    /*package*/ void setServerRouter(DocumentRouter<CompletableFuture<IBaseLanguageServerExtensions>> server) {
        this.serverRouter = server;
    }

    private DocumentRouter<CompletableFuture<IBaseLanguageServerExtensions>> availableServerRouter() {
        if (serverRouter == null) {
            // This should only happen if we forgot to call `setServerRouter` before finishing initialization
            throw new IllegalStateException("No server router available");
        }
        return serverRouter;
    }

    @Override
    public Stream<CompletableFuture<WorkspaceService>> allRoutes() {
        return availableServerRouter().allRoutes(server -> server.thenApply(LanguageServer::getWorkspaceService));
    }

    @Override
    public CompletableFuture<WorkspaceService> route(ISourceLocation loc) {
        return availableServerRouter().route(loc).thenApply(LanguageServer::getWorkspaceService);
    }

    @Override
    public CompletableFuture<WorkspaceService> route(String languageName) {
        return availableServerRouter().route(languageName).thenApply(LanguageServer::getWorkspaceService);
    }

    @Override
    public CompletableFuture<Object> executeCommand(ExecuteCommandParams commandParams) {
        if (commandParams.getCommand().startsWith(RASCAL_META_COMMAND) || commandParams.getCommand().startsWith(RASCAL_COMMAND)) {
            var languageName = ((JsonPrimitive) commandParams.getArguments().get(0)).getAsString();
            return route(languageName).thenCompose(s -> s.executeCommand(commandParams));
        }

        return CompletableFutureUtils.completedFuture(commandParams.getCommand() + " was ignored, since it is not a Rascal LSP command.", getExecutor());
    }

    @Override
    public void didCreateFiles(CreateFilesParams params) {
        params.getFiles().stream()
            .collect(Collectors.groupingBy(f -> route(Locations.toLoc(f.getUri()))))
            .forEach((r, creates) -> r.thenAccept(s -> s.didCreateFiles(new CreateFilesParams(creates))));
    }

    @Override
    public void didDeleteFiles(DeleteFilesParams params) {
        params.getFiles().stream()
            .collect(Collectors.groupingBy(f -> route(Locations.toLoc(f.getUri()))))
            .forEach((r, deletes) -> r.thenAccept(s -> s.didDeleteFiles(new DeleteFilesParams(deletes))));
    }

    @Override
    public void didRenameFiles(RenameFilesParams params) {
        params.getFiles().stream()
            .collect(Collectors.groupingBy(f -> route(Locations.toLoc(f.getOldUri())))) // like VS Code, notify language associated with the old file name
            .forEach((r, renames) -> r.thenAccept(s -> s.didRenameFiles(new RenameFilesParams(renames))));
    }

    @Override
    public void didChangeWorkspaceFolders(DidChangeWorkspaceFoldersParams params) {
        super.didChangeWorkspaceFolders(params);
        CompletableFutureUtils.reduce(allRoutes(f -> f.thenAccept(ws -> ws.didChangeWorkspaceFolders(params))), getExecutor());
    }

}
