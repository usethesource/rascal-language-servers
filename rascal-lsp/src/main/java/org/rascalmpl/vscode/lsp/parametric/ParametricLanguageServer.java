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
package org.rascalmpl.vscode.lsp.parametric;


import com.google.gson.GsonBuilder;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.rascalmpl.vscode.lsp.BaseLanguageServer;
import org.rascalmpl.vscode.lsp.parametric.LanguageRegistry.LanguageParameter;
import org.rascalmpl.vscode.lsp.util.NamedThreadPool;

public class ParametricLanguageServer extends BaseLanguageServer {

    protected static void startParametric(ServerArgs args) {
        startLanguageServer(NamedThreadPool.single("parametric-lsp")
            , NamedThreadPool.cached("parametric")
            , threadPool -> new ParametricTextDocumentService(threadPool, args.getDedicatedLanguage(), args.isExitWhenEmpty())
            , ParametricWorkspaceService::new
            , args.getPort()
        );
    }

    public static void main(String[] args) {
        startParametric(parseArgs(args));
    }

    public static class ServerArgs {
        private int port = 9999;
        private @Nullable LanguageParameter dedicatedLanguage = null;
        private boolean exitWhenEmpty = false;

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public @Nullable LanguageParameter getDedicatedLanguage() {
            return dedicatedLanguage;
        }

        public void setDedicatedLanguage(LanguageParameter dedicatedLanguage) {
            this.dedicatedLanguage = dedicatedLanguage;
        }

        public boolean isExitWhenEmpty() {
            return exitWhenEmpty;
        }

        public void setExitWhenEmpty(boolean exitWhenEmpty) {
            this.exitWhenEmpty = exitWhenEmpty;
        }

    }

    protected static ServerArgs parseArgs(String[] args) {
        var serverArgs = new ServerArgs();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port":
                    serverArgs.setPort(Integer.parseInt(args[++i]));
                    break;
                case "--exitWhenEmpty":
                    serverArgs.setExitWhenEmpty(true);
                    break;
                default:
                    if (serverArgs.getDedicatedLanguage() == null) {
                        serverArgs.setDedicatedLanguage(new GsonBuilder().create().fromJson(args[i], LanguageParameter.class));
                    }
                    break;
            }
        }
        return serverArgs;
    }
}
