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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.CodeLensOptions;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.ExecuteCommandOptions;
import org.eclipse.lsp4j.RenameFilesParams;
import org.eclipse.lsp4j.RenameOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.util.locations.ColumnMaps;
import org.rascalmpl.util.locations.LineColumnOffsetMap;
import org.rascalmpl.vscode.lsp.BaseWorkspaceService;
import org.rascalmpl.vscode.lsp.IBaseTextDocumentService;
import org.rascalmpl.vscode.lsp.TextDocumentState;
import org.rascalmpl.vscode.lsp.parametric.LanguageRegistry.LanguageParameter;
import org.rascalmpl.vscode.lsp.parametric.capabilities.CapabilityRegistration;
import org.rascalmpl.vscode.lsp.parametric.capabilities.CompletionCapability;
import org.rascalmpl.vscode.lsp.parametric.capabilities.FileOperationCapability;
import org.rascalmpl.vscode.lsp.rascal.conversion.SemanticTokenizer;
import org.rascalmpl.vscode.lsp.util.locations.Locations;

import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValue;

public class ParametricLanguageRouter extends BaseWorkspaceService implements IBaseTextDocumentService {

    private final Map<String, ISingleLanguageService> languageServices = new ConcurrentHashMap<>();
    private final Map<String, String> languagesByExtension = new ConcurrentHashMap<>();

    private @MonotonicNonNull CapabilityRegistration dynamicCapabilities;

    protected ParametricLanguageRouter(ExecutorService exec) {
        super(exec);
    }

    private ISingleLanguageService language(TextDocumentItem textDocument) {
        return language(textDocument.getUri());
    }

    private TextDocumentService language(VersionedTextDocumentIdentifier textDocument) {
        return language(textDocument.getUri());
    }

    private TextDocumentService language(TextDocumentIdentifier textDocument) {
        return language(textDocument.getUri());
    }

    private ISingleLanguageService language(String uri) {
        var ext = URIUtil.getExtension(URIUtil.assumeCorrectLocation(uri));
        var lang = languagesByExtension.get(ext);
        if (lang == null) {
            throw new IllegalStateException("No language exists for extension:" + ext);
        }
        var service = languageServices.get(ext);
        if (service == null) {
            throw new IllegalStateException("No language service exists for language: " + lang);
        }
        return service;
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        language(params.getTextDocument()).didOpen(params);
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        language(params.getTextDocument()).didChange(params);
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        language(params.getTextDocument()).didClose(params);
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        language(params.getTextDocument()).didSave(params);
    }

    @Override
    public void initializeServerCapabilities(ClientCapabilities clientCapabilities, ServerCapabilities result) {
                // Since the initialize request is the very first request after connecting, we can initialize the capabilities here
        // https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#initialize
        dynamicCapabilities = new CapabilityRegistration(availableClient(), exec, clientCapabilities
            , new CompletionCapability()
            , /* new FileOperationCapability.DidCreateFiles(exec), */ new FileOperationCapability.DidRenameFiles(exec), new FileOperationCapability.DidDeleteFiles(exec)
        );
        dynamicCapabilities.registerStaticCapabilities(result);

        var tokenizer = new SemanticTokenizer();

        result.setDefinitionProvider(true);
        result.setTextDocumentSync(TextDocumentSyncKind.Full);
        result.setHoverProvider(true);
        result.setReferencesProvider(true);
        result.setDocumentSymbolProvider(true);
        result.setImplementationProvider(true);
        result.setSemanticTokensProvider(tokenizer.options());
        result.setCodeActionProvider(true);
        result.setCodeLensProvider(new CodeLensOptions(false));
        result.setRenameProvider(new RenameOptions(true));
        result.setExecuteCommandProvider(new ExecuteCommandOptions(Collections.singletonList(getRascalMetaCommandName())));
        result.setInlayHintProvider(true);
        result.setSelectionRangeProvider(true);
        result.setFoldingRangeProvider(true);
        result.setCallHierarchyProvider(true);
    }

    @Override
    public void shutdown() {
        // TODO Kill all delegate processes
    }

    @Override
    public void pair(BaseWorkspaceService workspaceService) {
        pair(workspaceService);
    }

    @Override
    public void initialized() {
        initialized();
    }

    @Override
    public void registerLanguage(LanguageParameter lang) {
        // Main workhorse
        // TODO Start a delegate process with the right versions on the classpath for this language
    }

    @Override
    public void unregisterLanguage(LanguageParameter lang) {
        // TODO Kill the delegate process for this language and clean up maps
    }

    @Override
    public void projectAdded(String name, ISourceLocation projectRoot) {
        // No need to do anything
    }

    @Override
    public void projectRemoved(String name, ISourceLocation projectRoot) {
        // No need to do anything
    }

    @Override
    public CompletableFuture<IValue> executeCommand(String languageName, String command) {
        // TODO Figure out what to do
        return language(params).executeCommand(params);
    }

    @Override
    public LineColumnOffsetMap getColumnMap(ISourceLocation file) {
        return language(Locations.toUri(file).toString()).getColumnMap(file);
    }

    @Override
    public ColumnMaps getColumnMaps() {
        return language(params).getColumnMaps(params);
    }

    @Override
    public @Nullable TextDocumentState getDocumentState(ISourceLocation file) {
        return language(params).getDocumentState(params);
    }

    @Override
    public boolean isManagingFile(ISourceLocation file) {
        return language(params).isManagingFile(params);
    }

    @Override
    public void didRenameFiles(RenameFilesParams params, List<WorkspaceFolder> workspaceFolders) {
        // TODO Split by language/extension and inform each delegate of their own renamed files
        return language(params).didRenameFiles(params);
    }

    @Override
    public void cancelProgress(String progressId) {
        // TODO Since we don't know from which language this progress came, probably inform everyone
        return language(params).cancelProgress(params);
    }

}
