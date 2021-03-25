package org.rascalmpl.vscode.lsp.parametric;

import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.vscode.lsp.util.concurrent.InterruptibleFuture;
import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.ISourceLocation;

public interface ILanguageContributions {
    public InterruptibleFuture<ITree> parseSourceFile(ISourceLocation loc, String input);
    public InterruptibleFuture<IList> outline(ITree input);
    public InterruptibleFuture<IConstructor> summarize(ISourceLocation loc, ITree input);
}
