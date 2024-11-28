/*
 * Copyright (c) 2018-2023, NWO-I CWI and Swat.engineering
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
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.FileOperationFilter;
import org.eclipse.lsp4j.FileOperationOptions;
import org.eclipse.lsp4j.FileOperationPattern;
import org.eclipse.lsp4j.FileRename;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.RenameFilesParams;
import org.eclipse.lsp4j.ResourceOperation;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.rascalmpl.library.util.PathConfig;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.values.parsetrees.ProductionAdapter;
import org.rascalmpl.values.parsetrees.TreeAdapter;
import org.rascalmpl.values.parsetrees.visitors.IdentityTreeVisitor;
import org.rascalmpl.vscode.lsp.BaseWorkspaceService;
import org.rascalmpl.vscode.lsp.IBaseTextDocumentService;
import org.rascalmpl.vscode.lsp.TextDocumentState;
import org.rascalmpl.vscode.lsp.util.Versioned;
import org.rascalmpl.vscode.lsp.util.locations.Locations;

import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValue;

public class RascalWorkspaceService extends BaseWorkspaceService {
    private static final Logger logger = LogManager.getLogger(RascalWorkspaceService.class);

    private final RascalTextDocumentService rascalDocService;

    RascalWorkspaceService(IBaseTextDocumentService documentService) {
        super(documentService);
        rascalDocService = (RascalTextDocumentService) documentService;
    }

    @Override
    public void initialize(ClientCapabilities clientCap, @Nullable List<WorkspaceFolder> currentWorkspaceFolders,
            ServerCapabilities capabilities) {
        super.initialize(clientCap, currentWorkspaceFolders, capabilities);

        capabilities.getWorkspace().getFileOperations().setWillRename(new FileOperationOptions(List.of(new FileOperationFilter(new FileOperationPattern("**/*.rsc")))));
    }

    private Pair<String, Range> processQNamePrefix(ITree qn) {
        ISourceLocation fileLoc = TreeAdapter.getLocation(qn).top();
        List<ITree> nameSegments = TreeAdapter.getListASTArgs(TreeAdapter.getArg(qn, "names")).stream().map(ITree.class::cast).collect(Collectors.toList());
        String fullName = TreeAdapter.yield(qn);

        // Check if prefix is present
        if (nameSegments.size() <= 1) return Pair.of("", null);

        ITree firstPrefName = nameSegments.get(0);
        ITree lastPrefname = nameSegments.get(nameSegments.size() - 2);
        Position start = Locations.toPosition(TreeAdapter.getLocation(firstPrefName), rascalDocService.getColumnMap(fileLoc)    , false);
        Position end = Locations.toPosition(TreeAdapter.getLocation(lastPrefname), rascalDocService.getColumnMap(fileLoc), true);

        int prefixEndIdx = fullName.lastIndexOf("::");
        String prefix = fullName.substring(0, prefixEndIdx);

        return Pair.of(prefix, new Range(start, end));
    }

    private Pair<String, Range> processQName(ITree qn) {
        ISourceLocation fileLoc = TreeAdapter.getLocation(qn).top();
        return Pair.of(TreeAdapter.yield(qn), Locations.toRange(TreeAdapter.getLocation(qn), rascalDocService.getColumnMap(fileLoc)));
    }

    private ISourceLocation sourceLocationFromUri(String uri) {
        try {
            return URIUtil.createFromURI(uri);
        } catch (URISyntaxException e) {
            throw new ResponseErrorException(new ResponseError(ResponseErrorCode.RequestFailed, e.getMessage(), null));
        }
    }

    private Map<String, String> qualfiedNameChangesFromRenames(List<FileRename> renames, List<ISourceLocation> wsFolders) {
        return renames.stream()
            .map(rename -> {
                ISourceLocation currentLoc = sourceLocationFromUri(rename.getOldUri());
                ISourceLocation newLoc = sourceLocationFromUri(rename.getNewUri());

                ISourceLocation currentWsFolder = wsFolders.stream()
                    .filter(folderLoc -> URIUtil.isParentOf(folderLoc, currentLoc))
                    .findFirst()
                    .orElseThrow(() -> new ResponseErrorException(new ResponseError(ResponseErrorCode.InvalidParams,
                        String.format("Cannot move %s, since that location is outside the current workspace", currentLoc), null)));;

                ISourceLocation newWsFolder = wsFolders.stream()
                    .filter(folderLoc -> URIUtil.isParentOf(folderLoc, newLoc))
                    .findFirst()
                    .orElseThrow(() -> new ResponseErrorException(new ResponseError(ResponseErrorCode.InvalidParams,
                        String.format("Cannot move file to %s, since that location is outside the current workspace", newLoc), null)));

                if (!currentWsFolder.equals(newWsFolder)) {
                    String commonProjPrefix = StringUtils.getCommonPrefix(currentWsFolder.toString(), newWsFolder.toString());
                    String currentProject = StringUtils.removeStart(currentWsFolder.toString(), commonProjPrefix);
                    String newProject = StringUtils.removeStart(newWsFolder.toString(), commonProjPrefix);

                    throw new ResponseErrorException(new ResponseError(ResponseErrorCode.RequestFailed,
                        String.format("Moving files between projects (from %s to %s) is not supported", currentProject, newProject), null));
                }

                PathConfig pcfg = rascalDocService.facts.getPathConfig(currentWsFolder);
                try {
                    String currentName = pcfg.getModuleName(currentLoc);
                    String newName = pcfg.getModuleName(newLoc);

                    return Pair.of(currentName, newName);
                } catch (IOException e) {
                    throw new ResponseErrorException(new ResponseError(ResponseErrorCode.RequestFailed, e.getMessage(), null));
                }
            })
            .collect(Collectors.toConcurrentMap(Pair::getLeft, Pair::getRight));
    }

    private boolean checkAndAddCandidate(final Map<String, String> nameChanges, Pair<String, Range> info, Map<Range, String> changeLocations) {
        if (nameChanges.containsKey(info.getKey())) {
            changeLocations.put(info.getValue(), nameChanges.get(info.getKey()));
            return true;
        }
        return false;
    }

    private TextDocumentEdit collectChanges(final Map<String, String> nameChanges, ITree tree) {
        Map<Range, String> changeLocations = new HashMap<>();
        tree.accept(new IdentityTreeVisitor<RuntimeException>() {
            @Override
            public ITree visitTreeAppl(ITree arg) throws RuntimeException  {
                if ("QualifiedName".equals(ProductionAdapter.getSortName(TreeAdapter.getProduction(arg)))
                    && !checkAndAddCandidate(nameChanges, processQName(arg), changeLocations)) {
                    checkAndAddCandidate(nameChanges, processQNamePrefix(arg), changeLocations);
                }

                for (IValue child : TreeAdapter.getArgs(arg)) {
                    child.accept(this);
                }
                return null;
            }
        });

        List<TextEdit> edits = changeLocations.entrySet().stream()
            .map(entry -> new TextEdit(entry.getKey(), entry.getValue()))
            .collect(Collectors.toList());

        return new TextDocumentEdit(new VersionedTextDocumentIdentifier(TreeAdapter.getLocation(tree).top().getURI().toString(), null), edits);
    }

    @Override
    public CompletableFuture<WorkspaceEdit> willRenameFiles(RenameFilesParams params) {
        logger.debug("workspace/willRenameFiles: {}", params.getFiles());

        return CompletableFuture.supplyAsync(() -> {
            List<ISourceLocation> wsFolders = workspaceFolders().stream()
                .sorted(Comparator.comparing(WorkspaceFolder::getUri))
                .map(wsFolder -> sourceLocationFromUri(wsFolder.getUri()))
                .collect(Collectors.toList());

            final Map<String, String> qualifiedNameChanges = qualfiedNameChangesFromRenames(params.getFiles(), wsFolders);

            List<TextDocumentEdit> docChanges = wsFolders.stream()
                .flatMap(folder -> rascalDocService.getFolderContents(folder).stream())
                .parallel()
                .map(fileLoc -> {
                    TextDocumentState file = rascalDocService.getFile(fileLoc);
                    return file.getCurrentTreeAsync()
                        .thenApply(Versioned::get)
                        .handle((t, r) -> (t == null ? file.getMostRecentTree().get() : t))
                        .thenApply(tree -> collectChanges(qualifiedNameChanges, tree));
                })
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

            List<Either<TextDocumentEdit, ResourceOperation>> eithers = new ArrayList<>();
            docChanges.forEach(change -> eithers.add(Either.forLeft(change)));

            return new WorkspaceEdit(eithers);
        });
    }
}
