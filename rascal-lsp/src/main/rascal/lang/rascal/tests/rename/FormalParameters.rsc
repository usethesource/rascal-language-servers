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
module lang::rascal::tests::rename::FormalParameters

import lang::rascal::tests::rename::TestUtils;

test bool outerNestedFunctionParameter() = testRenameOccurrences({0, 3}, "
    'int f(int foo) {
    '   int g(int foo) {
    '       return foo;
    '   }
    '   return f(foo);
    '}
");

test bool innerNestedFunctionParameter() = testRenameOccurrences({1, 2}, "
    'int f(int foo) {
    '   int g(int foo) {
    '       return foo;
    '   }
    '   return f(foo);
    '}
");

test bool publicFunctionParameter() = testRenameOccurrences({0, 1}, "", decls = "
    'public int f(int foo) {
    '   return foo;
    '}
");

test bool defaultFunctionParameter() = testRenameOccurrences({0, 1}, "", decls = "
    'int f(int foo) {
    '   return foo;
    '}
");

test bool privateFunctionParameter() = testRenameOccurrences({0, 1}, "", decls = "
    'private int f(int foo) {
    '   return foo;
    '}
");

test bool nestedKeywordParameter() = testRenameOccurrences({0, 1, 2}, "
    'int f(int foo = 8) = foo;
    'int x = f(foo = 10);
", skipCursors = {2});

test bool keywordParameter() = testRenameOccurrences({0, 1, 2},
    "int x = f(foo = 10);"
    , decls="int f(int foo = 8) = foo;"
    , skipCursors = {2}
);

test bool functionIsNotConstructor() = testRenameOccurrences({0, 1, 3},
    "int x = f(foo = 10);"
    , decls="int f(int foo = 8) = foo;
            'data F = g(int foo = 8);"
    , skipCursors = {3}
);

@expected{illegalRename} test bool doubleParameterDeclaration1() = testRename("int f(int foo, int bar) = 1;");
@expected{illegalRename} test bool doubleParameterDeclaration2() = testRename("int f(int bar, int foo) = 1;");

@expected{illegalRename} test bool doubleNormalAndKeywordParameterDeclaration1() = testRename("int f(int foo, int bar = 9) = 1;");
@expected{illegalRename} test bool doubleNormalAndKeywordParameterDeclaration2() = testRename("int f(int bar, int foo = 8) = 1;");

@expected{illegalRename} test bool doubleKeywordParameterDeclaration1() = testRename("int f(int foo = 8, int bar = 9) = 1;");
@expected{illegalRename} test bool doubleKeywordParameterDeclaration2() = testRename("int f(int bar = 9, int foo = 8) = 1;");

@expected{illegalRename} // TODO Support this?
test bool renameParamToConstructorName() = testRenameOccurrences({0, 1},
    "int f(int foo) = foo;",
    decls = "data Bar = bar();"
);

@expected{illegalRename}
test bool renameParamToUsedConstructorName() = testRename(
    "Bar f(int foo) = bar(foo);",
    decls = "data Bar = bar(int x);"
);

test bool parameterShadowsParameter1() = testRenameOccurrences({0, 3}, "
    'int f1(int foo) {
    '   int f2(int foo) {
    '       int baz = 9;
    '       return foo + baz;
    '   }
    '   return f2(foo);
    '}
");

test bool parameterShadowsParameter2() = testRenameOccurrences({1, 2}, "
    'int f1(int foo) {
    '   int f2(int foo) {
    '       int baz = 9;
    '       return foo + baz;
    '   }
    '   return f2(foo);
    '}
");

@expected{illegalRename}
test bool parameterShadowsParameter3() = testRename("
    'int f(int bar) {
    '   int g(int baz) {
    '       int h(int foo) {
    '           return bar;
    '       }
    '       return h(baz);
    '   }
    '   return g(bar);
    '}
");

@expected{illegalRename}
test bool captureFunctionParameter() = testRename("
    'int f(int foo) {
    '   int bar = 9;
    '   return foo + bar;
    '}
");

@expected{illegalRename}
test bool doubleVariableAndParameterDeclaration() = testRename("
    'int f(int foo) {
    '   int bar = 9;
    '   return foo + bar;
    '}
");
