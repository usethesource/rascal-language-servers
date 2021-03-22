package org.rascalmpl.vscode.lsp.parametric.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.services.LanguageClient;
import org.rascalmpl.values.parsetrees.ITree;

import io.usethesource.vallang.ISourceLocation;

public class ParametricFileFacts {
    private static final Logger logger = LogManager.getLogger(ParametricFileFacts.class);
    private final Executor exec;
    private volatile @MonotonicNonNull LanguageClient client;
    private final Map<ISourceLocation, FileFact> files = new ConcurrentHashMap<>();

    public ParametricFileFacts(Executor exec) {
        this.exec = exec;
    }

    public void setClient(LanguageClient client) {
        this.client = client;
    }

    public void reportParseErrors(ISourceLocation file,  CompletableFuture<ITree> tree, List<Diagnostic> msgs) {
        getFile(file, tree).reportParseErrors(msgs);
    }

    private FileFact getFile(ISourceLocation l, CompletableFuture<ITree> tree) {
        return files.computeIfAbsent(
            l, 
            l1 -> new FileFact(l1, tree, exec)
        );
    }

    private class FileFact {
        private final ISourceLocation file;
        private final CompletableFuture<ITree> tree;
        private volatile List<Diagnostic> parseMessages = Collections.emptyList();

        public FileFact(ISourceLocation file, CompletableFuture<ITree> tree, Executor exec) {
            this.file = file;
            this.tree = tree;
        }

        public void reportParseErrors(List<Diagnostic> msgs) {
            parseMessages = msgs;
            sendDiagnostics();
        }
        
        private void sendDiagnostics() {
            if (client == null) {
                logger.debug("Cannot send diagnostics since the client hasn't been registered yet");
                return;
            }
            logger.trace("Sending diagnostics for: {}", file);
            client.publishDiagnostics(new PublishDiagnosticsParams(
                file.getURI().toString(),
                parseMessages));
        }
    }
}
