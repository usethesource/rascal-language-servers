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
package org.rascalmpl.vscode.lsp.rascal;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.FileOperationFilter;
import org.eclipse.lsp4j.FileOperationOptions;
import org.eclipse.lsp4j.FileOperationPattern;
import org.eclipse.lsp4j.FileOperationPatternKind;
import org.eclipse.lsp4j.FileOperationsServerCapabilities;
import org.eclipse.lsp4j.FileOperationsWorkspaceCapabilities;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentContentCapabilities;
import org.eclipse.lsp4j.TextDocumentContentParams;
import org.eclipse.lsp4j.TextDocumentContentRegistrationOptions;
import org.eclipse.lsp4j.TextDocumentContentResult;
import org.eclipse.lsp4j.WorkspaceClientCapabilities;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.WorkspaceServerCapabilities;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.rascalmpl.library.Prelude;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.vscode.lsp.BaseWorkspaceService;
import org.rascalmpl.vscode.lsp.util.Nullables;
import org.rascalmpl.vscode.lsp.util.locations.Locations;
import io.usethesource.vallang.ISourceLocation;

public class RascalWorkspaceService extends BaseWorkspaceService {

    private static final URIResolverRegistry REG = URIResolverRegistry.getInstance();
    private static final Logger logger = LogManager.getLogger(RascalWorkspaceService.class);
    private volatile boolean supportsTextDocumentContent = false;

    RascalWorkspaceService(ExecutorService exec) {
        super(exec);
    }

    @Override
    public void initialize(ClientCapabilities clientCap, @Nullable List<WorkspaceFolder> currentWorkspaceFolders,
            ServerCapabilities capabilities) {
        super.initialize(clientCap, currentWorkspaceFolders, capabilities);

        var workspaceCap = Nullables.ensureNonNullAndGet(capabilities, ServerCapabilities::getWorkspace, ServerCapabilities::setWorkspace, WorkspaceServerCapabilities::new);
        var fileOperationCapabilities = Nullables.ensureNonNullAndGet(workspaceCap, WorkspaceServerCapabilities::getFileOperations, WorkspaceServerCapabilities::setFileOperations, FileOperationsServerCapabilities::new);

        var rascalFile = new FileOperationPattern("**/*.rsc");
        rascalFile.setMatches(FileOperationPatternKind.File);
        var projectFolder = new FileOperationPattern("**/*");
        projectFolder.setMatches(FileOperationPatternKind.Folder);
        var whichFiles = new FileOperationOptions(Stream.of(rascalFile, projectFolder).map(FileOperationFilter::new).collect(Collectors.toList()));

        if (Nullables.has(clientCap.getWorkspace(), WorkspaceClientCapabilities::getFileOperations, FileOperationsWorkspaceCapabilities::getDidCreate)) {
            fileOperationCapabilities.setDidCreate(whichFiles);
        }
        if (Nullables.has(clientCap.getWorkspace(), WorkspaceClientCapabilities::getFileOperations, FileOperationsWorkspaceCapabilities::getDidRename)) {
            fileOperationCapabilities.setDidRename(whichFiles);
        }
        if (Nullables.has(clientCap.getWorkspace(), WorkspaceClientCapabilities::getFileOperations, FileOperationsWorkspaceCapabilities::getDidDelete)) {
            fileOperationCapabilities.setDidDelete(whichFiles);
        }
        if (Nullables.has(clientCap.getWorkspace(), WorkspaceClientCapabilities::getTextDocumentContent, TextDocumentContentCapabilities::getDynamicRegistration)) {
            supportsTextDocumentContent = true;
        }
    }

    @Override
    public void initialized() {
        super.initialized();

        if (supportsTextDocumentContent) {
            registerAvailableSchemes();
        }
    }

    @Override
    protected void projectRemoved(ISourceLocation loc) {
        ((RascalTextDocumentService) availableDocumentService()).projectRemoved(loc);
    }

    private void registerAvailableSchemes() {
        var client = availableClient();
        for (var scheme : calculatePossibleSchemes()) {
            logger.trace("Trying to register scheme in the client: {}", scheme);
            client.registerCapability(
                new RegistrationParams(
                    List.of(
                        new Registration(
                            UUID.randomUUID().toString(),
                            "workspace/textDocumentContent",
                            new TextDocumentContentRegistrationOptions(List.of(scheme))
                        )
                    )
                )
            ).exceptionally(ex -> {
                logger.error("Could not register textDocumentContent for scheme: {}", scheme, ex);
                return null;
            });
        }

    }

    private static final Set<String> GENERIC_SCHEMES = Set.of("file", "http", "https", "ftp", "tmp" /* VS Code has this one for itself */);
    private static final Set<String> CONTAINER_SCHEMES = Set.of("jar", "zip", "compressed");
    private static final Set<String> IGNORED_SCHEMES = Set.of(URIUtil.unknownLocation().getScheme(), "memory", "lsp");

    private final Stream<String> allReadableSchemes() {
        return Stream.concat(
                REG.getRegisteredInputSchemes().stream(),
                REG.getRegisteredLogicalSchemes().stream()
            ).filter(Predicate.not(IGNORED_SCHEMES::contains));
    }

    private List<String> calculatePossibleSchemes() {
        return Stream.concat(
            // rascal specific schemes
            allReadableSchemes().filter(Predicate.not(GENERIC_SCHEMES::contains)),
            // nested schemes like jar+file etc
            allReadableSchemes().filter(Predicate.not(CONTAINER_SCHEMES::contains))
                .flatMap(s -> CONTAINER_SCHEMES.stream().map(c -> c + "+" + s))
        ).collect(Collectors.toList());
    }


    @Override
    public CompletableFuture<TextDocumentContentResult> textDocumentContent(TextDocumentContentParams params) {
        logger.trace("textDocumentContent for {}", params.getUri());
        return CompletableFuture.supplyAsync(() -> {
            try {
                try (var contents = REG.getCharacterReader(Locations.toClientLocation(URIUtil.assumeCorrectLocation(params.getUri())))) {
                    return new TextDocumentContentResult(Prelude.consumeInputStream(contents));
                }
            } catch (IOException e) {
                var message = e.getMessage();
                if (message == null || message.isEmpty()) {
                    message = e.getClass().getName();
                }
                throw new ResponseErrorException(new ResponseError(-100, "Could not read " + params.getUri() + "due to: " + message, e));
            }
        }, getExecutor());
    }



}
