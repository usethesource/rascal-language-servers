package org.rascalmpl.vscode.lsp;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.LanguageParameter;

import io.usethesource.vallang.ISourceLocation;

public interface IBaseTextDocumentService extends TextDocumentService {
    void initializeServerCapabilities(ServerCapabilities result);
    void shutdown();
    void connect(LanguageClient client);
    void registerLanguage(LanguageParameter lang);

    /**
     * FileState encapsulates the current contents of every open file editor, 
     * and the corresponding latest parse tree that belongs to it.
     * It is parametrized by the parser that must be used to map the string
     * contents to a tree. All other TextDocumentServices depend on this information. 
     */
    public static class TextDocumentState {
        private final BiFunction<ISourceLocation, String, CompletableFuture<ITree>> parser;
    
        private final ISourceLocation file;
        private volatile String currentContent;
        @SuppressWarnings("java:S3077") // we are use volatile correctly
        private volatile @MonotonicNonNull ITree lastFullTree;
        @SuppressWarnings("java:S3077") // we are use volatile correctly
        private volatile CompletableFuture<ITree> currentTree;
    
        public TextDocumentState(BiFunction<ISourceLocation, String, CompletableFuture<ITree>> parser, ISourceLocation file, String content) {
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
}
