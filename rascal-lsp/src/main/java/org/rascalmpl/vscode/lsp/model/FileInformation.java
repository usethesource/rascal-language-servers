package org.rascalmpl.vscode.lsp.model;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.rascalmpl.library.util.PathConfig;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.vscode.lsp.RascalLanguageServices;
import org.rascalmpl.vscode.lsp.RascalTextDocumentService;
import org.rascalmpl.vscode.lsp.util.FileState;
import org.rascalmpl.vscode.lsp.util.Outline;

import io.usethesource.vallang.ISourceLocation;

public class FileInformation {
    private final FileState state;
    private final Executor exec;
    private final ISourceLocation file;
    private final PathConfig pcfg;
    private final RascalLanguageServices services;
    private volatile SoftReference<CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>>>
        currentOutline = new SoftReference<>(null);
    private volatile SoftReference<CompletableFuture<Summary>> currentSummary
        = new SoftReference<>(null);

    public FileInformation(RascalLanguageServices services, RascalTextDocumentService tds, Executor exec, ISourceLocation file, String contents) {
        this.state = new FileState(services, tds, exec, file, contents);
        this.exec = exec;
        this.services = services;
        this.file = file;

        try {
            this.pcfg = PathConfig.fromSourceProjectMemberRascalManifest(file);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void update(String text) {
        state.update(text);
        currentOutline.clear();
        currentSummary.clear();
    }

    public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> getOutline() {
        CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> result = currentOutline.get();
        if (result == null) {
            result = state.getCurrentTreeAsync()
                .handle((t, r) -> (t == null ? (state.getMostRecentTree()) : t))
                .thenApplyAsync(services::getOutline, exec)
                .thenApply(Outline::buildOutlineTree)
                ;
            currentOutline = new SoftReference<>(result);
        }
        return result;
    }

    public CompletableFuture<Summary> getSummary() {
        CompletableFuture<Summary> result = currentSummary.get();
        if (result == null) {
            result = CompletableFuture.supplyAsync(() -> new Summary(services.getSummary(file, pcfg)), exec);
            currentSummary = new SoftReference<>(result);
        }
        return result;
    }


    public CompletableFuture<ITree> getCurrentTreeAsync() {
        return state.getCurrentTreeAsync();
    }

    public @MonotonicNonNull ITree getMostRecentTree() {
        return state.getMostRecentTree();
    }



}
