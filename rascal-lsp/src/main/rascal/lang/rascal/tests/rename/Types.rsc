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

test bool globalAlias() = testRenameOccurrences({0, 1, 2, 3}, "
    'Foo f(Foo x) = x;
    'Foo foo = 8;
    'y = f(foo);
", decls = "alias Foo = int;", oldName = "Foo", newName = "Bar");

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
    }, oldName = "Foo", newName = "Bar");

test bool globalData() = testRenameOccurrences({0, 1, 2, 3}, "
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
    }, oldName = "Bool", newName = "Boolean");

test bool dataTypesInVModuleStructure() = testRenameOccurrences({
    byText("Left", "data Foo = f();", {0}), byText("Right", "data Foo = g();", {0})
                    , byText("Merger",
                        "import Left;
                        'import Right;
                        'bool f(Foo foo) = (foo == f() || foo == g());
                        ", {0})
}, oldName = "Foo", newName = "Bar");

@synopsis{
    (defs)
    /    \
    A  B  C
     \/ \/
      D E
     /   \
     (uses)
}
test bool dataTypesInWModuleStructureWithoutMerge() = testRenameOccurrences({
    byText("A", "data Foo = f();", {0}), byText("B", "", {}), byText("C", "data Foo = h();", {})
                    , byText("D",
                        "import A;
                        'import B;
                        'bool func(Foo foo) = foo == f();
                        ", {0}),    byText("E",
                                        "import B;
                                        'import C;
                                        'bool func(Foo foo) = foo == h();", {})
}, oldName = "Foo", newName = "Bar");

@synopsis{
    (defs)
    /  | \
    v  v  v
    A  B  C
     \/ \/
      D E
      ^ ^
     /   \
     (uses)
}
test bool dataTypesInWModuleStructureWithMerge() = testRenameOccurrences({
    byText("A", "data Foo = f();", {0}), byText("B", "data Foo = g();", {0}), byText("C", "data Foo = h();", {0})
                    , byText("D",
                        "import A;
                        'import B;
                        'bool func(Foo foo) = foo == f();
                        ", {0}),    byText("E",
                                        "import B;
                                        'import C;
                                        'bool func(Foo foo) = foo == h();", {0})
}, oldName = "Foo", newName = "Bar");

test bool dataTypesInYModuleStructure() = testRenameOccurrences({
    byText("Left", "data Foo = f();", {0}), byText("Right", "data Foo = g();", {0})
                    , byText("Merger",
                        "extend Left;
                        'extend Right;
                        ", {})
                    , byText("User",
                        "import Merger;
                        'bool f(Foo foo) = (foo == f() || foo == g());
                        ", {0})
}, oldName = "Foo", newName = "Bar");

test bool dataTypesInInvertedVModuleStructure() = testRenameOccurrences({
            byText("Definer", "data Foo = f() | g();", {0}),
    byText("Left", "import Definer;
                   'bool isF(Foo foo) = foo == f();", {0}), byText("Right", "import Definer;
                                                                            'bool isG(Foo foo) = foo == g();", {0})
}, oldName = "Foo", newName = "Bar");

test bool dataTypesInDiamondModuleStructure() = testRenameOccurrences({
            byText("Definer", "data Foo = f() | g();", {0}),
    byText("Left", "extend Definer;", {}), byText("Right", "extend Definer;", {}),
            byText("User", "import Left;
                           'import Right;
                           'bool isF(Foo foo) = foo == f();
                           'bool isG(Foo foo) = foo == g();", {0, 1})
}, oldName = "Foo", newName = "Bar");

@synopsis{
    Two disjunct module trees. Both trees define `data Foo`. Since the trees are disjunct,
    we expect a renaming triggered from the left side leaves the right side untouched.
}
test bool dataTypesInIIModuleStructure() = testRenameOccurrences({
    byText("LeftDefiner", "data Foo = f();", {0}),              byText("RightDefiner", "data Foo = g();", {}),
    byText("LeftExtender", "extend LeftDefiner;", {}),          byText("RightExtender", "extend RightDefiner;", {}),
    byText("LeftUser", "import LeftExtender;
                       'bool func(Foo foo) = foo == f();", {0}),       byText("RightUser", "import RightExtender;
                                                                        'bool func(Foo foo) = foo == g();", {})
}, oldName = "Foo", newName = "Bar");

test bool asType() = testRenameOccurrences({0, 1}, "
    'str s = \"text\";
    'if (t := [Foo] s) {
    '   str u = t;
    '}
", decls = "alias Foo = str;"
, oldName = "Foo", newName = "Bar");

test bool sameIdRoleOnly() = testRenameOccurrences({
    byText("A", "data foo = f();", {})
  , byText("B", "extend A;
                'data foo = g();", {})
  , byText("C", "extend B;
                'int foo = 8;",{0})
  , byText("D", "import C;
                'int baz = C::foo + 1;", {0})
});
