/*
 * Copyright (c) 2018-2023, NWO-I CWI and Swat.engineering
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
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
