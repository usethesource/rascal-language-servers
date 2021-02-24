package org.rascalmpl.vscode.lsp.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.rascalmpl.parser.gtd.exception.ParseError;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.vscode.lsp.RascalLanguageServices;
import org.rascalmpl.vscode.lsp.RascalTextDocumentService;

import io.usethesource.vallang.ISourceLocation;

public class FileState {
    private final Executor javaScheduler;
    private final RascalLanguageServices services;
    private final RascalTextDocumentService parent;

    private final ISourceLocation file;
    private volatile @MonotonicNonNull ITree lastFullTree;
    private volatile CompletableFuture<ITree> currentTree;

    public FileState(RascalLanguageServices services, RascalTextDocumentService tds, Executor javaSchedular, ISourceLocation file, String content) {
        this.services = services;
        this.javaScheduler = javaSchedular;
        this.parent = tds;

        this.file = file;
        currentTree = newContents(content);
    }

    public void update(String text) {
        currentTree = newContents(text);
    }

    private CompletableFuture<ITree> newContents(String contents) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ITree result = services.parseSourceFile(file, contents);
                lastFullTree = result;
                parent.clearReports(file);
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

}
