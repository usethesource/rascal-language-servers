package org.rascalmpl.vscode.lsp.util;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.rascalmpl.library.util.PathConfig;
import org.rascalmpl.parser.gtd.exception.ParseError;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.vscode.lsp.RascalLanguageServices;
import org.rascalmpl.vscode.lsp.RascalTextDocumentService;
import org.rascalmpl.vscode.lsp.model.Summary;

import io.usethesource.vallang.INode;
import io.usethesource.vallang.ISourceLocation;

public class FileState {
    private final ExecutorService javaScheduler;
    private final RascalLanguageServices services;
    private final RascalTextDocumentService parent;

    private final ISourceLocation file;
    private final PathConfig pcfg;
    private volatile @MonotonicNonNull ITree lastFullTree;
    private volatile CompletableFuture<ITree> currentTree;
    private volatile @Nullable CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> currentOutline;

    public FileState(RascalLanguageServices services, RascalTextDocumentService tds, ExecutorService javaSchedular, ISourceLocation file, String content) {
        this.services = services;
        this.javaScheduler = javaSchedular;
        this.parent = tds;

        this.file = file;
        currentTree = newContents(content);
        currentOutline = null;

        try {
            this.pcfg = PathConfig.fromSourceProjectMemberRascalManifest(file);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void update(String text) {
        currentTree = newContents(text);
        currentOutline = null;
    }

    private CompletableFuture<ITree> newContents(String contents) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ITree result = services.parseSourceFile(file, contents);
                parent.clearReports(file);
                lastFullTree = result;
                return result;
            } catch (ParseError e) {
                parent.report(e);
                throw new CompletionException(e);
            } catch (Throwable e) {
                throw new CompletionException(e);
            }
        }, javaScheduler);
    }

    public CompletableFuture<ITree> getCurrentTreeAsync() {
        return currentTree;
    }

    public @MonotonicNonNull ITree getMostRecentTree() {
        return lastFullTree;
    }

    public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> getCurrentOutline() {
        CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> result = currentOutline;
        if (result == null) {
            currentOutline = result = currentTree
                .thenApplyAsync(services::getOutline, javaScheduler)
                .thenApply(Outline::buildOutlineTree)
                ;
        }
        return result;
    }

    public CompletableFuture<Summary> getSummary() {
        return CompletableFuture.supplyAsync(() -> new Summary(services.getSummary(file, pcfg)), javaScheduler);
    }
}
