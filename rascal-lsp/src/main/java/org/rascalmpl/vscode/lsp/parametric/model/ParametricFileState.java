package org.rascalmpl.vscode.lsp.parametric.model;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.vscode.lsp.parametric.ILanguageContributions;

import io.usethesource.vallang.ISourceLocation;

public class ParametricFileState {
    private final Executor javaScheduler;

    private final ISourceLocation file;
    private volatile String currentContent;
    @SuppressWarnings("java:S3077") // we are use volatile correctly
    private volatile @MonotonicNonNull ITree lastFullTree;
    @SuppressWarnings("java:S3077") // we are use volatile correctly
    private volatile CompletableFuture<ITree> currentTree;

    private final ILanguageContributions contributions;

    public ParametricFileState(ILanguageContributions contributions, Executor javaSchedular, ISourceLocation file, String content) {
        this.javaScheduler = javaSchedular;
        this.contributions = contributions;

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
        return CompletableFuture.supplyAsync(() -> contributions.parseSourceFile(file, contents), javaScheduler)
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
