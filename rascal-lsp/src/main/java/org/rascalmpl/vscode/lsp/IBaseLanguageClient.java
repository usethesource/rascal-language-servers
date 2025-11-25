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

import java.net.URI;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.services.LanguageClient;
import org.rascalmpl.vscode.lsp.parametric.LanguageRegistry.LanguageParameter;

import io.usethesource.vallang.IInteger;
import io.usethesource.vallang.IString;

public interface IBaseLanguageClient extends LanguageClient {
    @JsonNotification("rascal/showContent")
    void showContent(URI uri, IString title, IInteger viewColumn);

    @JsonNotification("rascal/receiveRegisterLanguage")
    void receiveRegisterLanguage(LanguageParameter lang);

    @JsonNotification("rascal/receiveUnregisterLanguage")
    void receiveUnregisterLanguage(LanguageParameter lang);

    @JsonNotification("rascal/editDocument")
    void editDocument(URI uri, @Nullable Range range, int viewColumn);

    /**
     * Notification sent to the vscode client to start a debugging session on the given debug adapter port
     */
    @JsonNotification("rascal/startDebuggingSession")
    void startDebuggingSession(int serverPort);

    /**
     * Notification sent to the vscode client to register the port on which the debug adapter server is listening
     * It is then used to make the link between a terminal process ID and the corresponding debug server port
     */
    @JsonNotification("rascal/registerDebugServerPort")
    void registerDebugServerPort(int processID, int serverPort);
}
