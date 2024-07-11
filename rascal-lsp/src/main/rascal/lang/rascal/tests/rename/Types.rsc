module lang::rascal::tests::rename::Types

import lang::rascal::tests::rename::TestUtils;

test bool globalAliasFromDef() = {0, 1, 2, 3} == testRenameOccurrences("
    'Foo f(Foo x) = x;
    'Foo foo = 8;
    'y = f(foo);
", decls = "alias Foo = int;", oldName = "Foo", newName = "Bar");

test bool globalAliasFromUse() = {0, 1, 2, 3} == testRenameOccurrences("
    'Foo f(Foo x) = x;
    'Foo foo = 8;
    'y = f(foo);
", decls = "alias Foo = int;", oldName = "Foo", newName = "Bar", cursorAtOldNameOccurrence = 1);

test bool multiModuleAlias() = testRenameOccurrences((
    "alg::Fib": <"alias Foo = int;
            'Foo fib(int n) {
            '   if (n \< 2) {
            '       return 1;
            '   }
            '   return fib(n - 1) + fib(n -2);
            '}"
            , {0, 1}>
    , "Main": <"import alg::Fib;
               '
               'int main() {
               '   Foo result = fib(8);
               '   return 0;
               '}"
               , {0}>
    ), <"Main", "Foo", 0>, newName = "Bar");

test bool globalData() = {0, 1, 2, 3} == testRenameOccurrences("
    'Foo f(Foo x) = x;
    'Foo x = foo();
    'y = f(x);
", decls = "data Foo = foo();", oldName = "Foo", newName = "Bar");

test bool multiModuleData() = testRenameOccurrences((
    "values::Bool": <"
            'data Bool = t() | f();
            '
            'Bool and(Bool l, Bool r) = r is t ? l : f;
            '"
            , {1, 2, 3, 4}>
    , "Main": <"import values::Bool;
               '
               'void main() {
               '   Bool b = and(t(), f());
               '}"
               , {1}>
    ), <"Main", "Bool", 1>, newName = "Boolean");
