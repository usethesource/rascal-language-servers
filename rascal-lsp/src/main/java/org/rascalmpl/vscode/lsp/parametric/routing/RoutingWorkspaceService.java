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
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.rascalmpl.vscode.lsp.BaseWorkspaceService;
import org.rascalmpl.vscode.lsp.util.concurrent.CompletableFutureUtils;

/**
 * A language-parametric workspace service that routes incoming requests to remote dedicated language servers.
 */
public class RoutingWorkspaceService extends BaseWorkspaceService {

    private @MonotonicNonNull ActualRoutingLanguageServer parentServer;

    public RoutingWorkspaceService(ExecutorService exec) {
        super(exec);
    }

    public void setParentServer(ActualRoutingLanguageServer server) {
        this.parentServer = server;
    }

    private ActualRoutingLanguageServer availableServer() {
        if (parentServer == null) {
            throw new IllegalStateException("Server not connected yet.");
        }
        return parentServer;
    }

    @Override
    public CompletableFuture<Object> executeCommand(ExecuteCommandParams commandParams) {
        if (commandParams.getCommand().startsWith(RASCAL_META_COMMAND) || commandParams.getCommand().startsWith(RASCAL_COMMAND)) {
            var languageName = ((JsonPrimitive) commandParams.getArguments().get(0)).getAsString();
            return availableServer().languageByName(languageName)
                .thenCompose(s -> s.getWorkspaceService().executeCommand(commandParams));
        }

        return CompletableFutureUtils.completedFuture(commandParams.getCommand() + " was ignored.", getExecutor());
    }


}
