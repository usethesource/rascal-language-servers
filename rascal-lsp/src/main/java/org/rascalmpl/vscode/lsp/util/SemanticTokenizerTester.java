package org.rascalmpl.vscode.lsp.util;

import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.values.parsetrees.ITree;

import io.usethesource.vallang.IBool;
import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IValue;

public class SemanticTokenizerTester {

    public IValue toTokens(IConstructor tree, IBool applyRascalCategoryPatch) {
        var tokenizer = new SemanticTokenizer(applyRascalCategoryPatch.getValue());
        var encoded = tokenizer.semanticTokensFull((ITree) tree).getData();

        var values = IRascalValueFactory.getInstance();
        var tokens = new IValue[encoded.size() / 5];
        var tokenTypes = tokenizer.capabilities().getTokenTypes();

        for (int i = 0; i < encoded.size(); i += 5) {
            var deltaLine     = values.integer(encoded.get(i));
            var deltaStart    = values.integer(encoded.get(i + 1));
            var length        = values.integer(encoded.get(i + 2));
            var tokenType     = values.string(tokenTypes.get(encoded.get(i + 3)));
            var tokenModifier = values.string(""); // Token modifiers aren't supported yet
            tokens[i / 5] = values.tuple(deltaLine, deltaStart, length, tokenType, tokenModifier);
        }

        return values.list(tokens);
    }
}
