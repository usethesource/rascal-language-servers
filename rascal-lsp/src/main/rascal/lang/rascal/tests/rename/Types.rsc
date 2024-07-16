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

test bool multiModuleAlias() = testRenameOccurrences({
    byText("alg::Fib", "alias Foo = int;
            'Foo fib(int n) {
            '   if (n \< 2) {
            '       return 1;
            '   }
            '   return fib(n - 1) + fib(n -2);
            '}"
            , {0, 1})
    , byText("Main", "import alg::Fib;
               '
               'int main() {
               '   Foo result = fib(8);
               '   return 0;
               '}"
               , {0})
    }, <"Main", "Foo", 0>, newName = "Bar");

test bool globalData() = {0, 1, 2, 3} == testRenameOccurrences("
    'Foo f(Foo x) = x;
    'Foo x = foo();
    'y = f(x);
", decls = "data Foo = foo();", oldName = "Foo", newName = "Bar");

test bool multiModuleData() = testRenameOccurrences({
    byText("values::Bool", "
            'data Bool = t() | f();
            '
            'Bool and(Bool l, Bool r) = r is t ? l : f;
            '"
            , {1, 2, 3, 4})
    , byText("Main", "import values::Bool;
               '
               'void main() {
               '   Bool b = and(t(), f());
               '}"
               , {1})
    }, <"Main", "Bool", 1>, newName = "Boolean");
