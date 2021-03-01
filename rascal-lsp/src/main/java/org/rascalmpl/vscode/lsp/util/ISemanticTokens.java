package org.rascalmpl.vscode.lsp.util;

import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensCapabilities;
import org.eclipse.lsp4j.SemanticTokensDelta;
import org.eclipse.lsp4j.SemanticTokensDeltaParams;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.SemanticTokensRangeParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.rascalmpl.values.parsetrees.ITree;

public interface ISemanticTokens {
	SemanticTokensCapabilities semanticTokenCapabilities();
    SemanticTokens semanticTokensFull(SemanticTokensParams params, ITree tree);
	Either<SemanticTokens, SemanticTokensDelta> semanticTokensFullDelta(SemanticTokensDeltaParams params, ITree tree);
	SemanticTokens semanticTokensRange(SemanticTokensRangeParams params, ITree tree);
}