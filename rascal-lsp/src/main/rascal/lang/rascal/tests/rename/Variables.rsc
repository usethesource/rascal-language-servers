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
module lang::rascal::tests::rename::Variables

import lang::rascal::tests::rename::TestUtils;
import util::refactor::Exception;


//// Local

test bool freshName() = testRenameOccurrences({0}, "
    'int foo = 8;
    'int qux = 10;
");

test bool shadowVariableInInnerScope() = testRenameOccurrences({0}, "
    'int foo = 8;
    '{
    '   int bar = 9;
    '}
");

test bool parameterShadowsVariable() = testRenameOccurrences({0}, "
    'int foo = 8;
    'int f(int bar) {
    '   return bar;
    '}
");

@expected{illegalRename}
test bool implicitVariableDeclarationInSameScopeBecomesUse() = testRename("
    'int foo = 8;
    'bar = 9;
");

@expected{illegalRename}
test bool implicitVariableDeclarationInInnerScopeBecomesUse() = testRename("
    'int foo = 8;
    '{
    '   bar = 9;
    '}
");

@expected{illegalRename}
test bool doubleVariableDeclaration() = testRename("
    'int foo = 8;
    'int bar = 9;
");

test bool adjacentScopes() = testRenameOccurrences({0}, "
    '{
    '   int foo = 8;
    '}
    '{
    '   int bar = 9;
    '}
");

@expected{illegalRename}
test bool implicitPatterVariableInSameScopeBecomesUse() = testRename("
    'int foo = 8;
    'bar := 9;
");

@expected{illegalRename}
test bool implicitNestedPatterVariableInSameScopeBecomesUse() = testRename("
    'int foo = 8;
    '\<bar, _\> := \<9, 99\>;
");

@expected{illegalRename}
test bool implicitPatterVariableInInnerScopeBecomesUse() = testRename("
    'int foo = 8;
    'if (bar := 9) {
    '   temp = 2 * bar;
    '}
");

test bool explicitPatternVariableInInnerScope() = testRenameOccurrences({0}, "
    'int foo = 8;
    'if (int bar := 9) {
    '   bar = 2 * bar;
    '}
");

test bool becomesPatternInInnerScope() = testRenameOccurrences({0}, "
    'int foo = 8;
    'if (bar : int _ := 9) {
    '   bar = 2 * bar;
    '}
");

@expected{illegalRename}
test bool implicitPatternVariableBecomesInInnerScope() = testRename("
    'int foo = 8;
    'if (bar : _ := 9) {
    '   bar = 2 * foo;
    '}
");

@expected{illegalRename}
test bool explicitPatternVariableBecomesInInnerScope() = testRename("
    'int foo = 8;
    'if (bar : int _ := 9) {
    '   bar = 2 * foo;
    '}
");

@expected{illegalRename}
test bool shadowDeclaration() = testRename("
    'int foo = 8;
    'if (int bar := 9) {
    '   foo = 2 * bar;
    '}
");

// Although this is fine statically, it will cause runtime errors when `bar` is called
// > A value of type int is not something you can call like a function, a constructor or a closure.
@expected{illegalRename}
test bool doubleVariableAndFunctionDeclaration() = testRename("
    'int foo = 8;
    'void bar() {}
");

// Although this is fine statically, it will cause runtime errors when `bar` is called
// > A value of type int is not something you can call like a function, a constructor or a closure.
@expected{illegalRename}
test bool doubleFunctionAndVariableDeclaration() = testRename("
    'void bar() {}
    'foo = 8;
");

@expected{illegalRename}
test bool doubleFunctionAndNestedVariableDeclaration() = testRename("
    'bool bar() = true;
    'void f() {
    '   int foo = 0;
    '}
");

test bool tupleVariable() = testRenameOccurrences({0}, "\<foo, baz\> = \<0, 1\>;");

test bool tuplePatternVariable() = testRenameOccurrences({0, 1}, "
    'if (\<foo, baz\> := \<0, 1\>)
    '   qux = foo;
");


//// Global

test bool globalVar() = testRenameOccurrences({0, 3}, "
    'int f(int foo) = foo;
    'foo = 16;
", decls = "
    'int foo = 8;
");

test bool multiModuleVar() = testRenameOccurrences({
    byText("alg::Fib", "int foo = 8;
            '
            'int fib(int n) {
            '   if (n \< 2) {
            '       return 1;
            '   }
            '   return fib(n - 1) + fib(n -2);
            '}"
            , {0})
    , byText("Main", "import alg::Fib;
               '
               'int main() {
               '   fib(alg::Fib::foo);
               '   return 0;
               '}"
               , {0})
    });

test bool unrelatedVar() = testRenameOccurrences({
    byText("Module1", "int foo = 8;", {0})
  , byText("Module2", "import Module1;
                      'int foo = 2;
                      'int baz = foo;", {})
});
