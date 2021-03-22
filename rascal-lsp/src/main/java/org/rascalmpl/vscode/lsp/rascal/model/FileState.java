package org.rascalmpl.vscode.lsp.rascal.model;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.rascalmpl.values.parsetrees.ITree;

import io.usethesource.vallang.ISourceLocation;

public class FileState {
    private final BiFunction<ISourceLocation, String, CompletableFuture<ITree>> parser;

    private final ISourceLocation file;
    private volatile String currentContent;
    @SuppressWarnings("java:S3077") // we are use volatile correctly
    private volatile @MonotonicNonNull ITree lastFullTree;
    @SuppressWarnings("java:S3077") // we are use volatile correctly
    private volatile CompletableFuture<ITree> currentTree;

    public FileState(BiFunction<ISourceLocation, String, CompletableFuture<ITree>> parser, ISourceLocation file, String content) {
        this.parser = parser;
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
        return parser.apply(file, contents)
            .whenComplete((r, t) -> { 
                if (r != null) { 
                    lastFullTree = r; 
                } 
            });
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
