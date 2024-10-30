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

import lang::rascal::\syntax::Rascal;
import lang::rascal::tests::semanticTokenizer::Util;

test bool testTypesAndValues() = testTokenizer(#FunctionDeclaration,

   "void f() {
        bool b = true;
        int  i = 3;
        real r = 3.14;
        str  s = \"foo\<bar\>\";
        loc  l = |unknown:///|;
        tuple[int, int] \\tuple = \<3, 14\>;
    }",

    // Expectation: Types
    firstOccurrenceOf("void", "keyword"),
    firstOccurrenceOf("bool", "keyword"),
    firstOccurrenceOf("int", "keyword"),
    firstOccurrenceOf("real", "keyword"),
    firstOccurrenceOf("loc", "keyword"),
    firstOccurrenceOf("str", "keyword"),
    firstOccurrenceOf("tuple", "keyword"),

    // Expectations: Values
    firstOccurrenceOf("f", "uncategorized"),
    firstOccurrenceOf("true", "keyword"),
    //firstOccurrenceOf("3", "number"), -- https://github.com/usethesource/rascal-language-servers/issues/456
    firstOccurrenceOf("3.14", "number"),
    firstOccurrenceOf("foo", "string"),
    firstOccurrenceOf("\<", "string"),
    firstOccurrenceOf("bar", "uncategorized"),
    firstOccurrenceOf("\>", "string"),
    //firstOccurrenceOf("|unknown:///|", "string") -- https://github.com/usethesource/rascal-language-servers/issues/456
    lastOccurrenceOf("\<", "uncategorized"),
    lastOccurrenceOf("\>", "uncategorized"),

    // Configuration
    printActuals = false,
    applyRascalCategoryPatch = true
);

test bool testComments() = testTokenizer(#FunctionDeclaration,

   "void f() {
        /* Block comment */
        /* Multi-line 1
           Multi-line 2 */
        // Line comment
    }",

    // Expectation
    firstOccurrenceOf("Block comment", "comment"),
    firstOccurrenceOf("Multi-line 1", "comment"),
    firstOccurrenceOf("Multi-line 2", "comment"),
    firstOccurrenceOf("Line comment", "comment"),

    // Configuration
    printActuals = false,
    applyRascalCategoryPatch = true
);

test bool testTags() = testTokenizer(#Declaration,

   "@synopsis{Foo}
    @category=\"bar\"
    @memo
    int i = 0;",

    // Expectation
    firstOccurrenceOf("@synopsis{Foo}", "comment"),
    firstOccurrenceOf("@category=", "comment"),
    firstOccurrenceOf("\"bar\"", "string"),
    firstOccurrenceOf("@memo", "comment"),

    // Configuration
    printActuals = false,
    applyRascalCategoryPatch = true
);

// https://github.com/usethesource/rascal-language-servers/issues/90
test bool testTokenLastLine() = testTokenizer(#Literal,
    "3.14",
    firstOccurrenceOf("3.14", "number"),
    applyRascalCategoryPatch = true);
