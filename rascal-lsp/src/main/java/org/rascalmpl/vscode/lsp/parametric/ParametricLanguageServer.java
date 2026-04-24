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

    protected static final int DEFAULT_PORT_NUMBER = 9999;

    protected static void start(int portNumber, @Nullable LanguageParameter dedicatedLanguage) {
        startLanguageServer(NamedThreadPool.single("parametric-lsp")
            , NamedThreadPool.cached("parametric")
            , threadPool -> new ParametricTextDocumentService(threadPool, dedicatedLanguage)
            , ParametricWorkspaceService::new
            , portNumber
        );
    }

    public static void main(String[] args) {
        int portNumber = DEFAULT_PORT_NUMBER;
        LanguageParameter dedicatedLanguage = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port":
                    portNumber = Integer.parseInt(args[++i]);
                    break;
                default:
                    dedicatedLanguage = new GsonBuilder().create().fromJson(args[0], LanguageParameter.class);
                    break;
            }
        }

        start(portNumber, dedicatedLanguage);
    }
}
