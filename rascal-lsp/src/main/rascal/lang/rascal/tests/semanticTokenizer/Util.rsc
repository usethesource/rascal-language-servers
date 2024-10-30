@license{
Copyright (c) 2018-2023, NWO-I CWI and Swat.engineering
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice,
this list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
POSSIBILITY OF SUCH DAMAGE.
}
module lang::rascal::tests::semanticTokenizer::Util

import IO;
import List;
import ParseTree;
import String;

bool testTokenizer(type[&T<:Tree] begin, str input, Expect expects...,
        bool printActuals = false, bool applyRascalCategoryPatch = false) {

    Tree tree = parse(begin, input);
    list[SemanticToken] tokens = toTokens(tree, applyRascalCategoryPatch);
    list[loc] locations = toLocations(input, tokens);
    list[str] strings = toStrings(input, locations);

    bool less(Actual a1, Actual a2) = a1.l.offset < a2.l.offset;
    list[Actual] actuals = (zip3(tokens, locations, strings));

    if (printActuals) {
        iprintln(actuals);
    }

    for (expect <- expects) {
        compare(actuals, expect);
    }

    return true;
}

private list[loc] toLocations(str input, list[SemanticToken] tokens) {
    list[str] lines = split("\n", input);

    int line = 0;
    int column = 0;

    list[loc] locations = [];
    locations = for (token <- tokens) {
        line = line + token.deltaLine;
        column = (line == 0 || token.deltaLine == 0) ? column + token.deltaStart : 0;

        int offset = (0 | it + size(lines[i]) + 1 | i <- [0..line]) + column;
        int length = token.length;
        append |unknown:///|(offset, length);
    };

    return locations;
}

private list[str] toStrings(str input, list[loc] locations) {
    list[str] strings = [];

    strings = for (l <- locations) {
        int begin = l.offset;
        int end = begin + l.length;
        append substring(input, begin, end);
    }

    return strings;
}

//
// Semantic tokens
//

alias SemanticToken = tuple[
    int deltaLine,
    int deltaStart,
    int length,
    str tokenType,
    str tokenModifier];

@javaClass{org.rascalmpl.vscode.lsp.util.SemanticTokenizerTester}
java list[SemanticToken] toTokens(Tree _, bool applyRascalCategoryPatch);

//
// Actuals and expects
//

alias Actual = tuple[
    SemanticToken token,
    loc l,
    str s
];

data Expect
    = firstOccurrenceOf(str string, str tokenType)
    | lastOccurrenceOf(str string, str tokenType)
    | eachOccurrenceOf(str string, str tokenType);

void compare(list[Actual] actuals, firstOccurrenceOf(string, tokenType)) {
    if (<SemanticToken token, _, s> <- actuals, contains(s, string)) {
        assert token.tokenType == tokenType : "Expected token type (of \"<string>\"): <tokenType>. Actual: <token.tokenType>.";
    } else {
        assert false : "Unexpected string: <string>";
    }
}

void compare(list[Actual] actuals, lastOccurrenceOf(string, tokenType)) {
    if (<SemanticToken token, _, s> <- reverse(actuals), contains(s, string)) {
        assert token.tokenType == tokenType : "Expected token type (of \"<string>\"): `<tokenType>`. Actual: `<token.tokenType>`.";
    } else {
        assert false : "Unexpected string: <string>";
    }
}

void compare(list[Actual] actuals, eachOccurrenceOf(string, tokenType)) {
    for (<SemanticToken token, _, s> <- actuals, contains(s, string)) {
        assert token.tokenType == tokenType : "Expected token type (of \"<string>\"): `<tokenType>`. Actual: `<token.tokenType>`.";
    }
}
