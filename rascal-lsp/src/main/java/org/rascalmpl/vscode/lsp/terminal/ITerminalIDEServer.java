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
package org.rascalmpl.vscode.lsp.terminal;

import java.util.concurrent.CompletableFuture;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.rascalmpl.ideservices.IRemoteIDEServices.BrowseParameter;
import org.rascalmpl.ideservices.IRemoteIDEServices.DocumentEditsParameter;
import org.rascalmpl.ideservices.IRemoteIDEServices.LanguageParameter;
import org.rascalmpl.ideservices.IRemoteIDEServices.RegisterDiagnosticsParameters;
import org.rascalmpl.ideservices.IRemoteIDEServices.RegisterLocationsParameters;
import org.rascalmpl.ideservices.IRemoteIDEServices.SourceLocationParameter;
import org.rascalmpl.ideservices.IRemoteIDEServices.UnRegisterDiagnosticsParameters;

/**
 * Server interface for remote implementation of @see IDEServices
 */
public interface ITerminalIDEServer {
    @JsonRequest
    default CompletableFuture<Void> browse(BrowseParameter uri) {
        throw new UnsupportedOperationException();
    }

    @JsonRequest
    default CompletableFuture<Void> edit(EditorParameter edit)  {
        throw new UnsupportedOperationException();
    }

    @JsonRequest
    default CompletableFuture<SourceLocationParameter> resolveProjectLocation(SourceLocationParameter edit) {
        throw new UnsupportedOperationException();
    }

    @JsonRequest("rascal/receiveRegisterLanguage")
    default CompletableFuture<Void> receiveRegisterLanguage(LanguageParameter lang) {
        throw new UnsupportedOperationException();
    }

    @JsonRequest("rascal/receiveUnregisterLanguage")
    default CompletableFuture<Void> receiveUnregisterLanguage(LanguageParameter lang) {
        throw new UnsupportedOperationException();
    }

    @JsonRequest("rascal/applyDocumentEdits")
    default CompletableFuture<Void> applyDocumentEdits(DocumentEditsParameter edits) {
        throw new UnsupportedOperationException();
    }

    @JsonNotification("rascal/showHTML")
    default void showHTML(BrowseParameter content) {
        throw new UnsupportedOperationException();
    }

    @JsonNotification("rascal/registerLocations")
    default void registerLocations(RegisterLocationsParameters param) {
        throw new UnsupportedOperationException();
    }

    @JsonNotification("rascal/registerDiagnostics")
    default void registerDiagnostics(RegisterDiagnosticsParameters param) {
        throw new UnsupportedOperationException();
    }

    @JsonNotification("rascal/unregisterDiagnostics")
    default void unregisterDiagnostics(UnRegisterDiagnosticsParameters param) {
        throw new UnsupportedOperationException();
    }

    @JsonNotification("rascal/startDebuggingSession")
    default void startDebuggingSession(int serverPort) {
        throw new UnsupportedOperationException();
    }

    @JsonNotification("rascal/registerDebugServerPort")
    default void registerDebugServerPort(int processID, int serverPort) {
        throw new UnsupportedOperationException();
    }

    public static class EditParameter {
        private String module;

        public EditParameter(String module) {
            this.module = module;
        }

        public String getModule() {
            return module;
        }

        @Override
        public String toString() {
            return "editParameter: " + module;
        }
    }

    public static class EditorParameter {
        private String uri;
        private int viewColumn;
        private @Nullable Range range;

        public EditorParameter(String uri, @Nullable Range range, int viewColumn) {
            this.uri = uri;
            this.range = range;
            this.viewColumn = viewColumn;
        }

        public @Nullable Range getRange() {
            return range;
        }

        public String getUri() {
            return uri;
        }

        public int getViewColumn() {
            return viewColumn;
        }
    }

}
