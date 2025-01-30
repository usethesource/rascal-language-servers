@license{
Copyright (c) 2018-2025, NWO-I CWI and Swat.engineering
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
@bootstrapParser
module lang::rascal::tests::rename::Functions

import lang::rascal::tests::rename::TestUtils;
import util::refactor::Exception;

test bool nestedFunctionParameter() = testRenameOccurrences({0, 1}, "
    'int f(int foo, int baz) {
    '   return foo;
    '}
");

test bool nestedRecursiveFunctionName() = testRenameOccurrences({0, 1, 2, 3}, "
    'int fib(int n) {
    '   switch (n) {
    '       case 0: {
    '           return 1;
    '       }
    '       case 1: {
    '           return 1;
    '       }
    '       default: {
    '           return fib(n - 1) + fib(n - 2);
    '       }
    '   }
    '}
    '
    'fib(7);
", oldName = "fib", newName = "fibonacci");

test bool recursiveFunctionName() = testRenameOccurrences({0, 1, 2, 3}, "fib(7);", decls = "
    'int fib(int n) {
    '   switch (n) {
    '       case 0: {
    '           return 1;
    '       }
    '       case 1: {
    '           return 1;
    '       }
    '       default: {
    '           return fib(n - 1) + fib(n - 2);
    '       }
    '   }
    '}
", oldName = "fib", newName = "fibonacci");

test bool nestedPublicFunction() = testRenameOccurrences({0, 1}, "
    'public int foo(int f) {
    '   return f;
    '}
    'foo(1);
");

test bool nestedDefaultFunction() = testRenameOccurrences({0, 1}, "
    'int foo(int f) {
    '   return f;
    '}
    'foo(1);
");

test bool nestedPrivateFunction() = testRenameOccurrences({0, 1}, "
    'private int foo(int f) {
    '   return f;
    '}
    'foo(1);
");

test bool publicFunction() = testRenameOccurrences({0, 1}, "foo(1);", decls = "
    'public int foo(int f) {
    '   return f;
    '}
");

test bool defaultFunction() = testRenameOccurrences({0, 1}, "foo(1);", decls = "
    'int foo(int f) {
    '   return f;
    '}
");

test bool privateFunction() = testRenameOccurrences({0, 1}, "foo(1);", decls = "
    'private int foo(int f) {
    '   return f;
    '}
");

test bool backtrackOverload() = testRenameOccurrences({0, 1, 2}, "x = foo(3);", decls = "
    'int foo(int x) = x when x \< 2;
    'default int foo(int x) = x;
");

test bool patternOverload() = testRenameOccurrences({0, 1, 2, 3}, "x = size([1, 2]);", decls = "
    'int size(list[&T] _: []) = 0;
    'int size(list[&T] _: [_, *l]) = 1 + size(l);
", oldName = "size", newName = "sizeof");

test bool typeOverload() = testRenameOccurrences({0, 1, 2, 3, 4, 5, 6}, "x = size([1, 2]);", decls = "
    'int size(list[&T] _: []) = 0;
    'int size(list[&T] _: [_, *l]) = 1 + size(l);
    '
    'int size(set[&T] _: {}) = 0;
    'int size(set[&T] _: {_, *s}) = 1 + size(s);
", oldName = "size", newName = "sizeof");

test bool arityOverload() = testRenameOccurrences({0, 1, 2, 3, 4, 5}, "x = concat(\"foo\", \"bar\");", decls = "
    'str concat(str s) = s;
    'str concat(str s1, str s2) = s1 + concat(s2);
    'str concat(str s1, str s2, str s3) = s1 + concat(s2, s3);
", oldName = "concat", newName = "foo");

test bool overloadClash() = testRenameOccurrences({0}, "", decls = "
    'int foo = 0;
    'int foo(int x) = x when x \< 2;
");

test bool crossModuleOverload() = testRenameOccurrences({
    byText("Str", "
        'str concat(str s) = s;
        'str concat(str s1, str s2) = s1 + concat(s2);
    ", {0, 1, 2})
    , byText("Main", "
        'extend Str;
        'str concat(str s1, str s2, str s3) = s1 + concat(s2, s3);
    ", {0, 1})
}, oldName = "concat", newName = "conc");

test bool simpleTypeParams() = testRenameOccurrences({0, 1}, "
    '&T foo(&T l) = l;
    '&T bar(&T x, int y) = x;
", oldName = "T", newName = "U");

test bool typeParams() = testRenameOccurrences({0, 1, 2}, "
    '&T foo(&T l) {
    '   &T m = l;
    '   return m;
    '}
", oldName = "T", newName = "U");

test bool keywordTypeParamFromReturn() = testRenameOccurrences({0, 1, 2}, "
    '&T foo(&T \<: int l, &T \<: int kw = 1) = l + kw;
", oldName = "T", newName = "U");

@expected{illegalRename}
test bool typeParamsClash() = testRename("
    '&T foo(&T l, &U m) = l;
", oldName = "T", newName = "U", cursorAtOldNameOccurrence = 1);

test bool nestedTypeParams() = testRenameOccurrences({0, 1, 2, 3}, "
    '&T f(&T t) {
    '   &T g(&T t) = t;
    '   return g(t);
    '}
", oldName = "T", newName = "U");

@expected{illegalRename}
test bool nestedTypeParamClash() = testRename("
    'void f(&S s, &T t) {
    '   &S g(&S s) = s;
    '   &T g(&T t) = t;
    }
", oldName = "S", newName = "T", cursorAtOldNameOccurrence = 1);

test bool adjacentTypeParams() = testRenameOccurrences({0, 1}, "
    '&S f(&S s) = s;
    '&T f(&T t) = t;
", oldName = "S", newName = "T");

test bool typeParamsListReturn() = testRenameOccurrences({0, 1, 2}, "
    'list[&T] foo(&T l) {
    '   list[&T] m = [l, l];
    '   return m;
    '}
", oldName = "T", newName = "U");

test bool localOverloadedFunction() = testRenameOccurrences({0, 1, 2, 3}, "
    'bool foo(g()) = true;
    'bool foo(h()) = foo(g());
    '
    'foo(h());
", decls = "data D = g() | h();");

test bool functionsInVModuleStructure() = testRenameOccurrences({
    byText("Left", "str foo(str s) = s when s == \"x\";", {0}), byText("Right", "str foo(str s) = s when s == \"y\";", {0})
                    , byText("Merger",
                        "import Left;
                        'import Right;
                        'void main() { x = foo(\"foo\"); }
                        ", {0})
});

test bool functionsInYModuleStructure() = testRenameOccurrences({
    byText("Left", "str foo(str s) = s when s == \"x\";", {0}), byText("Right", "str foo(str s) = s when s == \"x\";", {0})
                    , byText("Merger",
                        "extend Left;
                        'extend Right;
                        ", {})
                    , byText("User",
                        "import Merger;
                        'void main() { foo(\"foo\"); }
                        ", {0})
});

test bool functionsInInvertedVModuleStructure() = testRenameOccurrences({
            byText("Definer", "str foo(str s) = s when s == \"x\";
                              'str foo(str s) = s when s == \"y\";", {0, 1}),
    byText("Left", "import Definer;
                   'void main() { foo(\"foo\"); }", {0}), byText("Right", "import Definer;
                                                                          'void main() { foo(\"fu\"); }", {0})
});

test bool functionsInDiamondModuleStructure() = testRenameOccurrences({
            byText("Definer", "str foo(str s) = s when s == \"x\";
                              'str foo(str s) = s when s == \"y\";", {0, 1}),
    byText("Left", "extend Definer;", {}), byText("Right", "extend Definer;", {}),
            byText("User", "import Left;
                           'import Right;
                           'void main() { foo(\"foo\"); }", {0})
});

test bool functionsInIIModuleStructure() = testRenameOccurrences({
    byText("LeftDefiner",  "str foo(str s) = s when s == \"x\";", {0}), byText("RightDefiner",  "str foo(str s) = s when s == \"y\";", {}),
    byText("LeftExtender", "extend LeftDefiner;", {}),                  byText("RightExtender", "extend RightDefiner;", {}),
    byText("LeftUser",     "import LeftExtender;
                           'void main() { foo(\"foo\"); }", {0}),       byText("RightUser",     "import RightExtender;
                                                                                                'void main() { foo(\"fu\"); }", {})
});
