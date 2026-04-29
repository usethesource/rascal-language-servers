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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.function.TriConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.KeyFor;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.util.locations.ColumnMaps;
import org.rascalmpl.util.locations.LineColumnOffsetMap;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.vscode.lsp.rascal.conversion.Diagnostics;
import org.rascalmpl.vscode.lsp.util.Versioned;
import org.rascalmpl.vscode.lsp.util.locations.Locations;

import io.usethesource.vallang.ISourceLocation;

/**
 * Management of {{@link TextDocumentState}} associated with locations.
 *
 *
 */
public class TextDocumentStateManager {

    private static final Logger logger = LogManager.getLogger(TextDocumentStateManager.class);

    private final Map<ISourceLocation, TextDocumentState> files = new ConcurrentHashMap<>();
    private final ColumnMaps columns;

    public TextDocumentStateManager() {
        this.columns = new ColumnMaps(l -> getContents(l));
    }

    public String getContents(@UnknownInitialization TextDocumentStateManager this, ISourceLocation file) {
        file = file.top();
        var ideState = files.get(file);
        if (ideState != null) {
            return ideState.getCurrentContent().get();
        }
        if (!URIResolverRegistry.getInstance().isFile(file)) {
            logger.error("Trying to get the contents of a directory: {}", file);
            return "";
        }
        try (Reader src = URIResolverRegistry.getInstance().getCharacterReader(file)) {
            return IOUtils.toString(src);
        }
        catch (IOException e) {
            logger.error("Error opening file {} to get contents", file, e);
            return "";
        }
    }

    public ColumnMaps getColumnMaps() {
        return columns;
    }

    public boolean isManagingFile(ISourceLocation loc) {
        return files.containsKey(loc.top());
    }

    public @Nullable TextDocumentState getDocumentState(ISourceLocation file) {
        return files.get(file.top());
    }

    public LineColumnOffsetMap getColumnMap(ISourceLocation loc) {
        return columns.get(loc.top());
    }

    protected TextDocumentState getFile(@UnknownInitialization TextDocumentStateManager this, ISourceLocation loc) throws FileNotFoundException {
        loc = loc.top();
        TextDocumentState file = files.get(loc);
        if (file == null) {
            throw new FileNotFoundException(String.format("Unknown file: {}", loc));
        }
        return file;
    }

    protected TextDocumentState openFile(TextDocumentItem doc, BiFunction<ISourceLocation, String, CompletableFuture<ITree>> parser, long timestamp, ExecutorService exec)  {
        return files.computeIfAbsent(Locations.toLoc(doc),
            l -> new TextDocumentState(parser, l, doc.getVersion(), doc.getText(), timestamp, exec));
    }

    protected void updateFile(ISourceLocation loc) {
        columns.clear(loc.top());
    }

    protected boolean removeFile(ISourceLocation loc) {
        updateFile(loc);
        return files.remove(loc.top()) != null;
    }

    protected @Nullable TextDocumentState updateFileState(ISourceLocation f, BiFunction<ISourceLocation, String, CompletableFuture<ITree>> parser) {
        f = f.top();
        logger.trace("Updating state: {}", f);

        // Since we cannot know what happened to this file before we were called, we need to be careful about races.
        // It might have been closed in the meantime, so we compute the new value if the key still exists, based on the current value.
        var state = files.computeIfPresent(f, (loc, currentState) -> currentState.changeParser(parser));
        if (state == null) {
            logger.debug("Updating the parser of {} failed, since it was closed.", f);
        }
        return state;
    }

    protected Set<@KeyFor("this.files") ISourceLocation> getOpenFiles() {
        return files.keySet();
    }

    protected static ResponseError unknownFileError(ISourceLocation loc, @Nullable Object data) {
        return new ResponseError(ResponseErrorCode.RequestFailed, "Unknown file: " + loc, data);
    }

    protected static ResponseError unknownFileError(VersionedTextDocumentIdentifier doc, @Nullable Object data) {
        return unknownFileError(Locations.toLoc(doc), data);
    }

    protected void updateContents(VersionedTextDocumentIdentifier doc, String newContents, long timestamp, TriConsumer<ISourceLocation, Versioned<List<Diagnostics.Template>>, List<Diagnostic>> reportDiagnostics) throws FileNotFoundException {
        logger.trace("New contents for {}", doc);
        TextDocumentState file = getFile(Locations.toLoc(doc));
        updateFile(file.getLocation());
        handleParsingErrors(file, file.update(doc.getVersion(), newContents, timestamp), reportDiagnostics);
    }

    protected void handleParsingErrors(TextDocumentState file, CompletableFuture<Versioned<List<Diagnostics.Template>>> diagnosticsAsync, TriConsumer<ISourceLocation, Versioned<List<Diagnostics.Template>>, List<Diagnostic>> reportDiagnostics) {
        diagnosticsAsync.thenAccept(diagnostics -> {
            List<Diagnostic> parseErrors = diagnostics.get().stream()
                .map(diagnostic -> diagnostic.instantiate(getColumnMaps()))
                .collect(Collectors.toList());

            logger.trace("Finished parsing tree, reporting new parse errors: {} for: {}", parseErrors, file.getLocation());
            reportDiagnostics.accept(file.getLocation(), diagnostics, parseErrors);
        });
    }

}
