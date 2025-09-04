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

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.ConfigurationItem;
import org.eclipse.lsp4j.ConfigurationParams;
import org.eclipse.lsp4j.DeleteFilesParams;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.FileDelete;
import org.eclipse.lsp4j.FileOperationFilter;
import org.eclipse.lsp4j.FileOperationOptions;
import org.eclipse.lsp4j.FileOperationPattern;
import org.eclipse.lsp4j.FileOperationsServerCapabilities;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.RenameFilesParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.WorkspaceFoldersOptions;
import org.eclipse.lsp4j.WorkspaceServerCapabilities;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.WorkspaceService;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public abstract class BaseWorkspaceService implements WorkspaceService, LanguageClientAware {
    private static final Logger logger = LogManager.getLogger(BaseWorkspaceService.class);

    private @MonotonicNonNull LanguageClient client;

    public static final String RASCAL_LANGUAGE = "Rascal";
    public static final String RASCAL_META_COMMAND = "rascal-meta-command";
    public static final String RASCAL_COMMAND = "rascal-command";

    private final ExecutorService ownExecuter;

    private final IBaseTextDocumentService documentService;
    private final CopyOnWriteArrayList<WorkspaceFolder> workspaceFolders = new CopyOnWriteArrayList<>();
    private boolean hasConfigurationCapability = false;
    private boolean hasDynamicChangeConfigurationCapability = false;
    private final @MonotonicNonNull ConfigurationParams configParams;

    private final List<FileOperationPattern> interestedInFiles;


    protected BaseWorkspaceService(ExecutorService exec, IBaseTextDocumentService documentService, List<FileOperationPattern> interestedInFiles) {
        this.documentService = documentService;
        this.ownExecuter = exec;
        this.interestedInFiles = interestedInFiles;
        this.configParams = buildConfigurationParams();
    }


    public void initialize(ClientCapabilities clientCap, @Nullable List<WorkspaceFolder> currentWorkspaceFolders, ServerCapabilities capabilities) {
        this.workspaceFolders.clear();
        if (currentWorkspaceFolders != null) {
            this.workspaceFolders.addAll(currentWorkspaceFolders);
        }

        var clientWorkspaceCap = clientCap.getWorkspace();

        WorkspaceServerCapabilities workspaceCapabilities = new WorkspaceServerCapabilities();
        if (clientWorkspaceCap != null) {
            if (clientWorkspaceCap.getWorkspaceFolders().booleanValue()) {
                var folderOptions = new WorkspaceFoldersOptions();
                folderOptions.setSupported(true);
                folderOptions.setChangeNotifications(true);
                workspaceCapabilities.setWorkspaceFolders(folderOptions);
            }

            var fileOperationCapabilities = new FileOperationsServerCapabilities();
            var whichFiles = new FileOperationOptions(interestedInFiles.stream()
                .map(FileOperationFilter::new)
                .collect(Collectors.toList())
            );
            boolean watchesSet = false;
            if (clientWorkspaceCap.getFileOperations().getDidRename().booleanValue()) {
                fileOperationCapabilities.setDidRename(whichFiles);
                watchesSet = true;
            }
            if (clientWorkspaceCap.getFileOperations().getDidDelete().booleanValue()) {
                fileOperationCapabilities.setDidDelete(whichFiles);
                watchesSet = true;
            }
            if (watchesSet) {
                workspaceCapabilities.setFileOperations(fileOperationCapabilities);
            }
            hasConfigurationCapability = clientWorkspaceCap.getConfiguration().booleanValue();
            hasDynamicChangeConfigurationCapability = clientWorkspaceCap.getDidChangeConfiguration().getDynamicRegistration().booleanValue();
        }

        capabilities.setWorkspace(workspaceCapabilities);
    }

    public List<WorkspaceFolder> workspaceFolders() {
        return Collections.unmodifiableList(workspaceFolders);
    }

    @Override
    public void connect(LanguageClient client) {
        this.client = client;
    }

    /**
     * After the client has been initialized, register dynamic capabilities.
     */
    @SuppressWarnings("java:S1172")
    void initialized(InitializedParams params) {
        if (!hasDynamicChangeConfigurationCapability) {
            logger.warn("Client does not support listening to configuration changes");
            return;
        }

        client.registerCapability(new RegistrationParams(Collections.singletonList(new Registration("someIdThatMightNeedToChange", "workspace/didChangeConfiguration"))))
            .thenAccept(v -> {
                logger.debug("Registered for configuration change events from client");
                fetchAndUpdateSettings(); // get initial settings
            })
            .exceptionally(e -> {
                logger.catching(e);
                return null;
            });
    }

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams params) {
        // params.getSettings() is useless; we need to query and update all settings instead
        logger.debug("workspace/didChangeConfiguration: {}", params);
        fetchAndUpdateSettings();
    }

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
        // todo: use in the future
    }

    private void updateConfigurations(List<Object> items) {
        for (var item : items) {
            if (!(item instanceof JsonObject)) {
                throw new IllegalArgumentException("Expected JsonObject in configuration array, got: " + item);
            }

            JsonObject settings = (JsonObject) item;
            if (settings.has("extension")) {
                final var extension = settings.get("extension").getAsJsonObject();
                if (extension.has("minLogLevel")) {
                    final var minLogLevel = extension.get("minLogLevel").getAsString();
                    Configurator.setRootLevel(Level.toLevel(minLogLevel, Level.DEBUG));
                }
            }
        }
    }

    private void fetchAndUpdateSettings() {
        if (!hasConfigurationCapability) {
            logger.error("Client does not support workspace/configuration; cannot update settings");
            return;
        }

        client.configuration(configParams)
            .thenAccept(this::updateConfigurations)
            .exceptionally(e -> {
                logger.error("Error updating configuration from client", e);
                return null; // Void
            });
    }

    @Override
    public void didChangeWorkspaceFolders(DidChangeWorkspaceFoldersParams params) {
        var removed = params.getEvent().getRemoved();
        if (removed != null) {
            workspaceFolders.removeAll(removed);
        }
        var added = params.getEvent().getAdded();
        if (added != null) {
            workspaceFolders.addAll(added);
        }
    }

    @Override
    public void didRenameFiles(RenameFilesParams params) {
        logger.debug("workspace/didRenameFiles: {}", params.getFiles());

        ownExecuter.submit(() -> {
            documentService.didRenameFiles(params, workspaceFolders());
        });

        ownExecuter.submit(() -> {
            // cleanup the old files (we do not get a `didDelete` event)
            var oldFiles = params.getFiles().stream()
                .map(f -> f.getOldUri())
                .map(FileDelete::new)
                .collect(Collectors.toList());
            documentService.didDeleteFiles(new DeleteFilesParams(oldFiles));
        });
    }

    @Override
    public void didDeleteFiles(DeleteFilesParams params) {
        logger.debug("workspace/didDeleteFiles: {}", params.getFiles());

        ownExecuter.submit(() -> {
            documentService.didDeleteFiles(params);
        });
    }

    @Override
    public CompletableFuture<Object> executeCommand(ExecuteCommandParams params) {
        if (params.getCommand().startsWith(RASCAL_META_COMMAND) || params.getCommand().startsWith(RASCAL_COMMAND)) {
            String languageName = ((JsonPrimitive) params.getArguments().get(0)).getAsString();
            String command = ((JsonPrimitive) params.getArguments().get(1)).getAsString();
            return documentService.executeCommand(languageName, command).thenApply(v -> v);
        }

        return CompletableFuture.supplyAsync(() -> params.getCommand() + " was ignored.", ownExecuter);
    }

    private ConfigurationParams buildConfigurationParams() {
        ConfigurationParams params = new ConfigurationParams();
        var config = new ConfigurationItem();
        config.setSection("rascal");
        params.setItems(Collections.singletonList(config));
        return params;
    }

    protected final ExecutorService getExecuter() {
        return ownExecuter;
    }


}
