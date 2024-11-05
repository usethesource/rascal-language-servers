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
module lang::rascal::tests::semanticTokenizer::Rascal

import lang::rascal::tests::semanticTokenizer::Util;

// -------
// Grammar

import lang::rascal::\syntax::Rascal;

// -----
// Tests

test bool testTypesAndValues() = testTokenizer(#Declaration,

   "void f() {
        bool b = true;
        int  i = 3;
        real r = 3.14;
        str  s = \"foo\<bar\>\";
        loc  l = |unknown:///|;
        tuple[int, int] \\tuple = \<3, 14\>;
    }",

    expectFirst("void", "keyword"),
    expectFirst("bool", "keyword"),
    expectFirst("int", "keyword"),
    expectFirst("real", "keyword"),
    expectFirst("loc", "keyword"),
    expectFirst("str", "keyword"),
    expectFirst("tuple", "keyword"),

    expectFirst("f", "uncategorized"),
    expectFirst("true", "keyword"),
    expectFirst("3", "number"), // https://github.com/usethesource/rascal-language-servers/issues/456
    expectFirst("3.14", "number"),
    expectFirst("foo", "string"),
    expectFirst("\<", "string"),
    expectFirst("bar", "uncategorized"),
    expectFirst("\>", "string"),
    expectFirst("|unknown:///|", "string"), // https://github.com/usethesource/rascal-language-servers/issues/456
    expectLast("\<", "uncategorized"),
    expectLast("\>", "uncategorized"),

    applyRascalCategoryPatch = true
);

test bool testComments() = testTokenizer(#Declaration,

   "void f() {
        /* Block comment */
        /* Multi-line 1
           Multi-line 2 */
        // Line comment
    }",

    expectFirst("Block comment", "comment"),
    expectFirst("Multi-line 1", "comment"),
    expectFirst("Multi-line 2", "comment"), // https://github.com/usethesource/rascal-language-servers/issues/20
    expectFirst("Line comment", "comment"),

    applyRascalCategoryPatch = true
);

test bool testTags() = testTokenizer(#Declaration,

   "@synopsis{Foo}
    @category=\"bar\"
    @memo
    int i = 0;",

    expectFirst("@synopsis{Foo}", "comment"),
    expectFirst("@category=", "comment"),
    expectFirst("\"bar\"", "string"),
    expectFirst("@memo", "comment"),

    applyRascalCategoryPatch = true
);

test bool testUnicode() = testTokenizer(#Declaration,
   "void f() {
        str s = \"𝄞𝄞𝄞\";
    }",

    expectFirst("str", "keyword"),
    expectFirst(" s = ", "uncategorized"),
    expectFirst("\"𝄞𝄞𝄞\"", "string"), // https://github.com/usethesource/rascal-language-servers/issues/19
    expectFirst(";", "uncategorized"),
    applyRascalCategoryPatch = true
);

test bool testInnerOverOuterLegacy() = testTokenizer(#Declaration,

   "void f() {
        int i = 3;
        loc l = |unknown:///|;
    }",

    expectFirst("3", "uncategorized"), // Instead of `number`
    expectFirst("|unknown:///|", "uncategorized"), // Instead of `string`

    useLegacyHighlighting = true,
    applyRascalCategoryPatch = true
);
