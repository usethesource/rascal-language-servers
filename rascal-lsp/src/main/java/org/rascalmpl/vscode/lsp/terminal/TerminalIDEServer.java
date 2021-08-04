/*
 * Copyright (c) 2018-2021, NWO-I CWI and Swat.engineering
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

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.CreateFile;
import org.eclipse.lsp4j.DeleteFile;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.RenameFile;
import org.eclipse.lsp4j.ResourceOperation;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.vscode.lsp.IBaseLanguageClient;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.IValue;

/**
 * This server forwards IDE services requests by a Rascal terminal
 * directly to the LSP language client.
 */
public class TerminalIDEServer implements ITerminalIDEServer {
    private static final Logger logger = LogManager.getLogger(TerminalIDEServer.class);

    private final IBaseLanguageClient languageClient;

    public TerminalIDEServer(IBaseLanguageClient client) {
        this.languageClient = client;
    }

    @Override
    public CompletableFuture<Void> browse(BrowseParameter uri) {
        logger.trace("browse({})", uri);
        languageClient.showContent(uri);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> edit(EditParameter edit) {
        logger.trace("edit({})", edit);
        languageClient.showMessage(new MessageParams(MessageType.Info, "trying to edit: " + edit.getModule()));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<SourceLocationParameter> resolveProjectLocation(SourceLocationParameter loc) {
        logger.trace("resolveProjectLocation({})", loc);
        try {
            ISourceLocation input = loc.getLocation();

            for (WorkspaceFolder folder : languageClient.workspaceFolders().get()) {
                // TODO check if everything goes ok encoding-wise
                if (folder.getName().equals(input.getAuthority())) {
                    ISourceLocation root = URIUtil.createFromURI(folder.getUri());
                    ISourceLocation newLoc = URIUtil.getChildLocation(root, input.getPath());
                    return CompletableFuture.completedFuture(new SourceLocationParameter(newLoc));
                }
            }

            return CompletableFuture.completedFuture(loc);
        }
        catch (URISyntaxException | InterruptedException | ExecutionException e) {
            logger.error(e);
            return CompletableFuture.completedFuture(loc);
        }
    }

    @Override
    public CompletableFuture<Void> receiveRegisterLanguage(LanguageParameter lang) {
        // we forward the request from the terminal to register a language
        // straight into the client:
        languageClient.receiveRegisterLanguage(lang);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> applyDocumentEdits(DocumentEditsParameter edits) {
        IList list = edits.getEdits();

        // TODO propagate failure reasons back to caller?
        languageClient.applyEdit(new ApplyWorkspaceEditParams(new WorkspaceEdit(translateDocumentChanges(list))));
        return CompletableFuture.completedFuture(null);
    }

    private List<Either<TextDocumentEdit, ResourceOperation>> translateDocumentChanges(IList list) {
        List<Either<TextDocumentEdit, ResourceOperation>> result = new ArrayList<>(list.size());

        for (IValue elem : list) {
            IConstructor edit = (IConstructor) elem;

            switch (edit.getName()) {
                case "removed":
                    result.add(Either.forRight(new DeleteFile(getFileURI(edit, "file"))));
                    break;
                case "created":
                    result.add(Either.forRight(new CreateFile(getFileURI(edit, "file"))));
                    break;
                case "renamed":
                    result.add(Either.forRight(new RenameFile(getFileURI(edit, "from"), getFileURI(edit, "to"))));
                    break;
                case "changed":
                    // TODO: file document identifier version is unknown here. that may be problematic
                    // have to extend the entire/all LSP API with this information _per_ file?
                    result.add(Either.forLeft(
                        new TextDocumentEdit(new VersionedTextDocumentIdentifier(getFileURI(edit, "file"), null),
                            translateTextEdits((IList) edit.get("edits")))));
                    break;
            }
        }

        return result;
    }

    private List<TextEdit> translateTextEdits(IList edits) {
        return edits.stream()
            .map(e -> (IConstructor) e)
            .map(c -> new TextEdit(locationToRange((ISourceLocation) c.get("range")), ((IString) c.get("replacement")).getValue()))
            .collect(Collectors.toList());
    }

    private Range locationToRange(ISourceLocation loc) {
        // TODO: this is not right for UTF8 characters. we need to implement ColumnMaps
        // but here it is unclear whether the content comes from an editor or from disk.
        // need to study on a good solution. Perhaps IBaseLanguageClient should have
        // a method to convert locs to ranges?
        return new Range(new Position(loc.getBeginLine(), loc.getBeginColumn()),
                         new Position(loc.getEndLine(), loc.getEndColumn()));
    }

    private static String getFileURI(IConstructor edit, String label) {
        return ((ISourceLocation) edit.get(label)).getURI().toString();
    }
}
