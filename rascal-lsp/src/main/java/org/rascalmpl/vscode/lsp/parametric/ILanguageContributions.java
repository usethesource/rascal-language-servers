package org.rascalmpl.vscode.lsp.parametric;

import java.util.concurrent.CompletableFuture;

import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.vscode.lsp.util.concurrent.InterruptibleFuture;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.ISourceLocation;

public interface ILanguageContributions {
    public CompletableFuture<ITree> parseSourceFile(ISourceLocation loc, String input);
    public CompletableFuture<IList> outline(ITree input);
    public InterruptibleFuture<IConstructor> summarize(ISourceLocation loc, ITree input);
}
