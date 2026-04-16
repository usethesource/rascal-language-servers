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

import java.io.IOException;
import java.io.Reader;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.BiFunction;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.KeyFor;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.util.locations.ColumnMaps;
import org.rascalmpl.util.locations.LineColumnOffsetMap;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.vscode.lsp.TextDocumentState;
import org.rascalmpl.vscode.lsp.util.locations.Locations;

import io.usethesource.vallang.ISourceLocation;

public class TextDocumentStateManager {

    private static final Logger logger = LogManager.getLogger(TextDocumentStateManager.class);

    private final Map<ISourceLocation, TextDocumentState> files = new ConcurrentHashMap<>();
    private final ColumnMaps columns;

    public TextDocumentStateManager() {
        this.columns = new ColumnMaps(l -> getContents(l));
    }

    public String getContents(@UnknownInitialization TextDocumentStateManager this, ISourceLocation file) {
        file = file.top();
        TextDocumentState ideState = getFile(file);
        if (ideState != null) {
            return ideState.getCurrentContent().get();
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

    TextDocumentState getFile(@UnknownInitialization TextDocumentStateManager this, ISourceLocation loc) {
        loc = loc.top();
        TextDocumentState file = files.get(loc);
        if (file == null) {
            throw new ResponseErrorException(unknownFileError(loc, loc));
        }
        return file;
    }

    TextDocumentState openFile(TextDocumentItem doc, BiFunction<ISourceLocation, String, CompletableFuture<ITree>> parser, long timestamp, ExecutorService exec)  {
        return files.computeIfAbsent(Locations.toLoc(doc),
            l -> new TextDocumentState(parser, l, doc.getVersion(), doc.getText(), timestamp, exec));
    }

    void updateFile(ISourceLocation loc) {
        columns.clear(loc.top());
    }

    boolean removeFile(ISourceLocation loc) {
        updateFile(loc);
        return files.remove(loc.top()) == null;
    }

    @Nullable TextDocumentState updateFileState(ISourceLocation f, BiFunction<ISourceLocation, String, CompletableFuture<ITree>> parser) {
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

    Set<@KeyFor("this.files") ISourceLocation> getOpenFiles() {
        return files.keySet();
    }

    static ResponseError unknownFileError(ISourceLocation loc, Object data) {
        return new ResponseError(ResponseErrorCode.RequestFailed, "Unknown file: " + loc, data);
    }
}
