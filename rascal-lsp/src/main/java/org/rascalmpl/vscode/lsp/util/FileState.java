package org.rascalmpl.vscode.lsp.util;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.rascalmpl.library.util.PathConfig;
import org.rascalmpl.parser.gtd.exception.ParseError;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.vscode.lsp.RascalLanguageServices;
import org.rascalmpl.vscode.lsp.RascalTextDocumentService;

import io.usethesource.vallang.ISourceLocation;

public class FileState {
    private static final Logger logger = LogManager.getLogger(FileState.class);
    private final Executor javaScheduler;
    private final RascalLanguageServices services;
    private final RascalTextDocumentService parent;

    private final ISourceLocation file;
    private final PathConfig pcfg;
    @SuppressWarnings("java:S3077") // we are use volatile correctly
    private volatile @MonotonicNonNull ITree lastFullTree;
    @SuppressWarnings("java:S3077") // we are use volatile correctly
    private volatile CompletableFuture<ITree> currentTree;

    public FileState(RascalLanguageServices services, RascalTextDocumentService tds, Executor javaSchedular,
        ISourceLocation file, String content) {
        this.services = services;
        this.javaScheduler = javaSchedular;
        this.parent = tds;

        this.file = file;
        try {
            this.pcfg = PathConfig.fromSourceProjectMemberRascalManifest(file);
            logger.trace("PathConfig for file: {} is {}", file, pcfg);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        currentTree = newContents(content);
    }

    public CompletableFuture<ITree> update(String text) {
        return currentTree = newContents(text);
    }

    @SuppressWarnings("java:S1181") // we want to catch all Java exceptions from the parser
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

    public PathConfig getPathConfig() {
        return pcfg;
    }

    public ISourceLocation getLocation() {
        return file;
    }

}
