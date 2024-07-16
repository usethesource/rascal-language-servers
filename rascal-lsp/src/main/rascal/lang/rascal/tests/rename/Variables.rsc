module lang::rascal::tests::rename::Variables

import lang::rascal::tests::rename::TestUtils;
import lang::rascal::lsp::refactor::Exception;


//// Local

test bool freshName() = {0} == testRenameOccurrences("
    'int foo = 8;
    'int qux = 10;
");

test bool shadowVariableInInnerScope() = {0} == testRenameOccurrences("
    'int foo = 8;
    '{
    '   int bar = 9;
    '}
");

test bool parameterShadowsVariable() = {0} == testRenameOccurrences("
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

test bool adjacentScopes() = {0} == testRenameOccurrences("
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

test bool explicitPatternVariableInInnerScope() = {0} == testRenameOccurrences("
    'int foo = 8;
    'if (int bar := 9) {
    '   bar = 2 * bar;
    '}
");

test bool becomesPatternInInnerScope() = {0} == testRenameOccurrences("
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

test bool tupleVariable() = {0} == testRenameOccurrences("\<foo, baz\> = \<0, 1\>;");

test bool tuplePatternVariable() = {0, 1} == testRenameOccurrences("
    'if (\<foo, baz\> := \<0, 1\>)
    '   qux = foo;
");


//// Global

test bool globalVar() = {0, 3} == testRenameOccurrences("
    'int f(int foo) = foo;
    'foo = 16;
", decls = "
    'int foo = 8;
");

test bool multiModuleVar() = testRenameOccurrences((
    "alg::Fib": <"int foo = 8;
            '
            'int fib(int n) {
            '   if (n \< 2) {
            '       return 1;
            '   }
            '   return fib(n - 1) + fib(n -2);
            '}"
            , {0}>
    , "Main": <"import alg::Fib;
               '
               'int main() {
               '   fib(alg::Fib::foo);
               '   return 0;
               '}"
               , {0}>
    ), <"Main", "foo", 0>, newName = "Bar");
