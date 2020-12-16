package org.rascalmpl.vscode.lsp.util;

import org.rascalmpl.parser.gtd.exception.ParseError;

import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISourceLocation;

public interface ErrorReporter {
    void clearReports(ISourceLocation file);
    void report(ISet msgs);
    void report(ISourceLocation file, ISet messages);
	void report(ParseError e);
}
