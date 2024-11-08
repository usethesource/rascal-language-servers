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
module lang::rascal::tests::semanticTokenizer::NestedCategories

import lang::rascal::tests::semanticTokenizer::Util;

// -------
// Grammar

syntax Type
    = TypeParameter
    | @category="type" "str"
    | @category="type" "map" "[" Type "," Type "]"
    ;

lexical TypeParameter
    = @category="typeParameter" "&" Alnum+;

lexical String
    = @category="string" "\"" (Alnum | ("\<" Variable "\>"))* "\"";

lexical Variable
    = @category="variable" Alnum+;

lexical Interface
    = @category="interface" "I" Class;

lexical InterfaceOuterOverInner
    = @categoryNesting="outerOverInner" Interface;

lexical Class
    = @category="class" Upper Alnum*;

lexical Alnum = [0-9 A-Z a-z];
lexical Upper = [A-Z];

// -----
// Tests

test bool testType() = testTokenizer(#Type,

    "map[str,&T0]",

    expectFirst("map[str,", "type"),
    expectFirst("&T0", "typeParameter"), // Inner over outer
    expectFirst("]", "type")
);

test bool testString() = testTokenizer(#String,

    "\"foo\<bar\>\"",

    expectFirst("\"foo\<", "string"),
    expectFirst("bar", "variable"), // Inner over outer
    expectFirst("\>\"", "string")
);

test bool testInterface() = testTokenizer(#Interface,

    "IFoo",

    expectFirst("I", "interface"),
    expectFirst("Foo", "class"),
    expectEachNot("IFoo", "interface") // *Not* outer over inner

    // This test demonstrates that, sometimes, arguably the "natural" way to
    // write a grammar (e.g., "An interface name is just any class name, but
    // prefixed with an I") requires outer-over-inner semantic tokenization.
    // This is currently not supported (i.e., the only "solution" right now is
    // to rewrite the grammar). Further reading:
    // https://github.com/usethesource/rascal-language-servers/issues/456
);
