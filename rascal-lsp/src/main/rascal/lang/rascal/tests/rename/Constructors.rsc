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
module lang::rascal::tests::rename::Constructors

import lang::rascal::tests::rename::TestUtils;

test bool extendedConstructor() = testRenameOccurrences({
    byText("Definer", "data Foo = foo(int i);", {0})
  , byText("Extender",
           "extend Definer;
           'data Foo = foo(int i, int j);
           'void main() {
           '   Foo f = foo(8);
           '}", {0, 1})
});

test bool disjunctConstructor() = testRenameOccurrences({
    byText("Definer", "data Foo = foo(int i);", {0})
  , byText("Unrelated",
           "data Foo = foo();", {})
});

test bool functionOverloadsConstructor() = testRenameOccurrences({
    byText("ConsDefiner", "data Foo = foo(int i);", {0}),
    byText("FuncOverload", "import ConsDefiner;
                           'Foo foo(int i, int j) = foo(i + j);", {0, 1}),
    byText("Main", "import ConsDefiner;
                   'Foo f = foo(8);", {0})
});

test bool differentADTsDuplicateConstructorNames() = testRenameOccurrences({
    byText("A", "data Bar = foo();", {0})
  , byText("B",
           "extend A;
           'data Foo = foo(int i);
           'Bar f = foo();", {1})
});

test bool constructorNameUsedAsVar() = testRenameOccurrences({
    byText("Constructor", "data Foo = foo();", {0})
  , byText("OtherType",
           "import Constructor;
           'int foo = 8;", {})
});

test bool constructorInPattern() = testRenameOccurrences({0, 1, 2, 3}, "
    'Foo f = foo(8);
    'if (foo(_) := f) {
    '   int x = match(f);
    '}
", decls = "
    'data Foo = foo(int i);
    'int match(foo(int i)) = i;
");

test bool constructorIsCheck() = testRenameOccurrences({0, 1, 2, 3}, "
    'Foo f = foo(8);
    'isFoo(f);
", decls = "
    'data Foo
    '   = foo(int i)
    '   | foo(int i, int j)
    '   | baz();
    bool isFoo(Foo f) = f is foo;
");

test bool constructorsAndTypesInVModuleStructure() = testRenameOccurrences({
    byText("Left", "data Foo = foo();", {0}), byText("Right", "data Foo = foo(int i);", {0})
                    , byText("Merger",
                        "import Left;
                        'import Right;
                        'bool f(Foo f) = (f == foo() || f == foo(1));
                        ", {0, 1})
});

test bool constructorsVModuleStructure() = testRenameOccurrences({
    byText("Left", "data Foo = foo();", {0}), byText("Right", "data Foo = foo(int i);", {0})
                    , byText("Merger",
                        "import Left;
                        'import Right;
                        'bool f(foo()) = true;
                        ", {0})
});

@synopsis{
    (defs)
    /    \
    A  B  C
     \/ \/
      D E
     /   \
     (uses)
}
test bool constructorsAndTypesInWModuleStructureWithoutMerge() = testRenameOccurrences({
    byText("A", "data Foo = foo();", {0}), byText("B", "", {}), byText("C", "data Foo = foo(int i, int j);", {})
                    , byText("D",
                        "import A;
                        'import B;
                        'bool func(Foo f) = f == foo();
                        ", {0}),    byText("E",
                                        "import B;
                                        'import C;
                                        'bool func(Foo f) = f == foo(8, 54);", {})
});

@synopsis{
    (defs)
    /    \
    A  B  C
     \/ \/
      D E
     /   \
     (uses)
}
test bool constructorsInWModuleStructureWithoutMerge() = testRenameOccurrences({
    byText("A", "data Foo = foo();", {0}), byText("B", "", {}), byText("C", "data Foo = foo(int i, int j);", {})
                    , byText("D",
                        "import A;
                        'import B;
                        'bool func(foo()) = true;
                        ", {0}),    byText("E",
                                        "import B;
                                        'import C;
                                        'bool func(foo(8, 54)) = true;", {})
});

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
test bool constructorsAndTypesInWModuleStructureWithMerge() = testRenameOccurrences({
    byText("A", "data Foo = foo();", {0}), byText("B", "data Foo = foo(int i);", {0}), byText("C", "data Foo = foo(int i, int j);", {0})
                    , byText("D",
                        "import A;
                        'import B;
                        'bool func(Foo f) = f == foo();
                        ", {0}),    byText("E",
                                        "import B;
                                        'import C;
                                        'bool func(Foo f) = f == foo(8, 54);", {0})
});

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
test bool constructorsInWModuleStructureWithMerge() = testRenameOccurrences({
    byText("A", "data Foo = foo();", {0}), byText("B", "data Foo = foo(int i);", {0}), byText("C", "data Foo = foo(int i, int j);", {0})
                    , byText("D",
                        "import A;
                        'import B;
                        'bool func(foo()) = true;
                        ", {0}),    byText("E",
                                        "import B;
                                        'import C;
                                        'bool func(foo(8, 54)) = true;", {0})
});

test bool constructorsAndTypesInYModuleStructure() = testRenameOccurrences({
    byText("Left", "data Foo = foo();", {0}), byText("Right", "data Foo = foo(int i);", {0})
                    , byText("Merger",
                        "extend Left;
                        'extend Right;
                        ", {})
                    , byText("User",
                        "import Merger;
                        'bool f(Foo f) = (f == foo() || f == foo(1));
                        ", {0, 1})
});

test bool constructorsInYModuleStructure() = testRenameOccurrences({
    byText("Left", "data Foo = foo();", {0}), byText("Right", "data Foo = foo(int i);", {0})
                    , byText("Merger",
                        "extend Left;
                        'extend Right;
                        ", {})
                    , byText("User",
                        "import Merger;
                        'bool f(foo()) = true;
                        'bool f(foo(1)) = true;
                        ", {0, 1})
});

test bool constructorsAndTypesInInvertedVModuleStructure() = testRenameOccurrences({
            byText("Definer", "data Foo = foo() | foo(int i);", {0, 1}),
    byText("Left", "import Definer;
                   'bool isF(Foo f) = f == foo();", {0}), byText("Right", "import Definer;
                                                                          'bool isG(Foo f) = f == foo(1);", {0})
});

test bool constructorsInInvertedVModuleStructure() = testRenameOccurrences({
            byText("Definer", "data Foo = foo() | foo(int i);", {0, 1}),
    byText("Left", "import Definer;
                   'bool isF(foo()) = true;", {0}), byText("Right", "import Definer;
                                                                    'bool isG(foo(1)) = true;", {0})
});

test bool constructorsAndTypesInDiamondModuleStructure() = testRenameOccurrences({
            byText("Definer", "data Foo = foo() | foo(int i);", {0, 1}),
    byText("Left", "extend Definer;", {}), byText("Right", "extend Definer;", {}),
            byText("User", "import Left;
                           'import Right;
                           'bool isF(Foo f) = f == foo();
                           'bool isG(Foo f) = f == foo(8);", {0, 1})
});

test bool constructorsInDiamondModuleStructure() = testRenameOccurrences({
            byText("Definer", "data Foo = foo() | foo(int i);", {0, 1}),
    byText("Left", "extend Definer;", {}), byText("Right", "extend Definer;", {}),
            byText("User", "import Left;
                           'import Right;
                           'bool isF(foo()) = true;
                           'bool isG(foo(8)) = true;", {0, 1})
});

@synopsis{
    Two disjunct module trees. Both trees define `data Foo`. Since the trees are disjunct,
    we expect a renaming triggered from the left side leaves the right side untouched.
}
test bool constructorsAndTypesInIIModuleStructure() = testRenameOccurrences({
    byText("LeftDefiner", "data Foo = foo();", {0}),              byText("RightDefiner", "data Foo = foo(int i);", {}),
    byText("LeftExtender", "extend LeftDefiner;", {}),            byText("RightExtender", "extend RightDefiner;", {}),
    byText("LeftUser", "import LeftExtender;
                       'bool func(Foo f) = f == foo();", {0}),       byText("RightUser", "import RightExtender;
                                                                            'bool func(Foo f) = f == foo(8);", {})
});

@synopsis{
    Two disjunct module trees. Both trees define `data Foo`. Since the trees are disjunct,
    we expect a renaming triggered from the left side leaves the right side untouched.
}
test bool constructorsInIIModuleStructure() = testRenameOccurrences({
    byText("LeftDefiner", "data Foo = foo();", {0}),              byText("RightDefiner", "data Foo = foo(int i);", {}),
    byText("LeftExtender", "extend LeftDefiner;", {}),            byText("RightExtender", "extend RightDefiner;", {}),
    byText("LeftUser", "import LeftExtender;
                       'bool func(foo()) = true;", {0}),       byText("RightUser", "import RightExtender;
                                                                      'bool func(foo(8)) = true;", {})
});
