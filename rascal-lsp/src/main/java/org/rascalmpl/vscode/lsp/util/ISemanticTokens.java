package org.rascalmpl.vscode.lsp.util;

import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensCapabilities;
import org.eclipse.lsp4j.SemanticTokensDelta;
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.rascalmpl.values.parsetrees.ITree;

public interface ISemanticTokens {
	SemanticTokensCapabilities capabilities();
	SemanticTokensWithRegistrationOptions options();
    SemanticTokens semanticTokensFull(ITree tree);
	Either<SemanticTokens, SemanticTokensDelta> semanticTokensFullDelta(String previousDelta, ITree tree);
	SemanticTokens semanticTokensRange(Range range, ITree tree);
}