package org.rascalmpl.vscode.lsp.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.vscode.lsp.RascalLanguageServices;
import io.usethesource.vallang.ISourceLocation;

public class FileState {
    private static final Logger logger = LogManager.getLogger(FileState.class);
    private final Executor javaScheduler;
    private final RascalLanguageServices services;

    private final ISourceLocation file;
    private volatile String currentContent;
    @SuppressWarnings("java:S3077") // we are use volatile correctly
    private volatile @MonotonicNonNull ITree lastFullTree;
    @SuppressWarnings("java:S3077") // we are use volatile correctly
    private volatile CompletableFuture<ITree> currentTree;

    public FileState(RascalLanguageServices services, Executor javaSchedular, ISourceLocation file, String content) {
        this.services = services;
        this.javaScheduler = javaSchedular;

        this.file = file;
        this.currentContent = content;
        currentTree = newContents(content);
    }

    public CompletableFuture<ITree> update(String text) {
        currentContent = text;
        currentTree = newContents(text);
        return currentTree;
    }

    @SuppressWarnings("java:S1181") // we want to catch all Java exceptions from the parser
    private CompletableFuture<ITree> newContents(String contents) {
        return CompletableFuture.supplyAsync(() -> services.parseSourceFile(file, contents), javaScheduler)
            .whenComplete((r, t) -> { if (r != null) { lastFullTree = r; } });
    }

    public CompletableFuture<ITree> getCurrentTreeAsync() {
        return currentTree;
    }

    public @MonotonicNonNull ITree getMostRecentTree() {
        return lastFullTree;
    }

    public ISourceLocation getLocation() {
        return file;
    }

    public String getCurrentContent() {
        return currentContent;
    }

}
