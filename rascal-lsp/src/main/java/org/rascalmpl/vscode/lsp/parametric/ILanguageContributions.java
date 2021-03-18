package org.rascalmpl.vscode.lsp.parametric;

import org.rascalmpl.values.parsetrees.ITree;

import io.usethesource.vallang.IList;
import io.usethesource.vallang.ISourceLocation;

public interface ILanguageContributions {
    public ITree parseSourceFile(ISourceLocation loc, String input);
    public IList outline(ITree input);
}
