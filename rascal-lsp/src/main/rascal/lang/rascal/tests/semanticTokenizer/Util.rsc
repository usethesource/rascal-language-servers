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

// This is the main utility function to test the semantic tokenizer. All other
// functions in this module are auxiliary (and private).
bool testTokenizer(type[&T<:Tree] begin, str input, Expect expects...,
        bool printActuals = false,
        bool useLegacyHighlighting = false,
        bool applyRascalCategoryPatch = false) {

    // First, compute the tokens by calling the semantic tokenizer (in Java)
    Tree tree = parse(begin, input);
    list[SemanticToken] tokens = toTokens(tree, useLegacyHighlighting, applyRascalCategoryPatch);

    // Next, compute the absolute location of each token (i.e., the position of
    // each token in `tokens` is represented *relative to* its predecessor)
    list[loc] locations = toAbsoluteLocations(input, tokens);

    // Next, compute the string of each token
    list[str] strings = [substring(input, l.offset, l.offset + l.length) | l <- locations];

    // Last, test the expectations
    list[Actual] actuals = sort(zip3(tokens, locations, strings), less);

    if (printActuals) {
        iprintln(actuals);
    }

    return (true | it && check(actuals, expect) | expect <- expects);
}

private list[loc] toAbsoluteLocations(str input, list[SemanticToken] tokens) {
    list[str] lines = split("\n", input);

    // Initialize the "cursor"
    int line   = 0;
    int column = 0;

    return for (token <- tokens) {
        column = token.deltaLine > 0 ? 0 : column; // Reset column?

        // Advance the "cursor"
        line   += token.deltaLine;
        column += toLength32(token.deltaStart, substring(lines[line], column));

        // Compute the absolute location of `token`
        int offset = (0 | it + size(lines[i]) + 1 | i <- [0..line]) + column;
        int length = toLength32(token.length, substring(lines[line], column));
        append |unknown:///|(offset, length);
    }
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
java list[SemanticToken] toTokens(Tree _, bool _, bool _);

//
// Length conversion: Characters are counted as 16-bit units in LSP, whereas
// they are counted as 32-bit units in Rascal, so lengths need to be converted.
// The difference manifests only in the presence of surrogate pairs.
//

alias Length32 = int;
alias Length16 = int;

private Length32 toLength32(Length16 n16, str input) {
    int n32 = 0;
    for (c <- chars(input)) {
        if (n16 <= 0) break;
        n16 -= isSupplementaryCodePoint(c) ? 2 : 1;
        n32 += 1;
    }
    return n32;
}

private bool isSupplementaryCodePoint(int c)
    = MIN_SUPPLEMENTARY_CODEPOINT <= c && c <= MAX_CODE_POINT;

private int MIN_SUPPLEMENTARY_CODEPOINT = 0x010000;
private int MAX_CODE_POINT = 0x10FFFF;

//
// Actuals
//

alias Actual = tuple[SemanticToken token, loc l, str s];

private bool less(Actual a1, Actual a2)
    = a1.l.offset < a2.l.offset;

private list[Actual] filterByTokenType(list[Actual] actuals, str tokenType)
    = [a | Actual a: <<_, _, _, tokenType, _>, _, _> <- actuals];

private list[Actual] filterByString(list[Actual] actuals, str string)
    = [a | a <- actuals, contains(a.s, string)];

//
// Expects
//

data Expect // Represents...

    // ...the expectation that the `n`-th/first/last occurrence of `string` is
    // in a token of type `tokenType`
    = expectNth(int n, str string, str tokenType)
    | expectFirst(str string, str tokenType)
    | expectLast(str string, str tokenType)

    // ...the expectation that each occurrence of `string` is not in a token of
    // type `tokenType`
    | expectEachNot(str string, str tokenType);

private bool check(list[Actual] actuals, expectNth(n, string, tokenType)) {
    actuals = filterByString(actuals, string);
    assert [] != actuals[n..(n + 1)] : "Unexpected string: \"<string>\"";
    assert n >= 0 : "Expected `n` to be non-negative. Actual: `<n>`.";
    assert <<_, _, _, tokenType, _>, _, _> := actuals[n] : "Expected token type of \"<string>\": <tokenType>. Actual: <actuals[n].token.tokenType>.";
    return true;
}

private bool check(list[Actual] actuals, expectFirst(string, tokenType)) {
    return check(actuals, expectNth(0, string, tokenType));
}

private bool check(list[Actual] actuals, expectLast(string, tokenType)) {
    actuals = filterByString(actuals, string);
    return check(actuals, expectNth(size(actuals) - 1, string, tokenType));
}

private bool check(list[Actual] actuals, expectEachNot(string, tokenType)) {
    actuals = filterByString(actuals, string);
    actuals = filterByTokenType(actuals, tokenType);
    assert [] == actuals : "Not-expected token type of \"<string>\": `<tokenType>`. Actual: `<tokenType>`.";;
    return true;
}
