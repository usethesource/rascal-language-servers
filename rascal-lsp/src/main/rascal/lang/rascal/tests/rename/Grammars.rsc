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
module lang::rascal::tests::rename::Grammars

import lang::rascal::tests::rename::TestUtils;

test bool productionType() = testRenameOccurrences({0, 1, 2, 3}, "
    'Foo func(Foo f) = f.child;
", decls = "syntax Foo = Foo child;"
, oldName = "Foo", newName = "Bar");

test bool productionConcreteType() = testRenameOccurrences({0, 1, 2, 3, 4}, "
    'Foo func((Foo) `\<Foo child\>`) = child;
", decls = "syntax Foo = Foo child;"
, oldName = "Foo", newName = "Bar");

test bool productionPattern() = testRenameOccurrences({0, 1, 2}, "
    'Tree t;
    'if (/Foo f := t) x = f;
", decls =
    "syntax Foo = Foo child;
    'data Tree;"
, oldName = "Foo", newName = "Bar");

test bool productionReifiedType() = testRenameOccurrences({0, 1, 2}, "
    't = parse(#Foo, \"foo(aaa)\");
    't = implode(#Foo, t);
", decls = "
    'lexical A = \"a\"+;
    'syntax Foo = s: \"foo\" \"(\" A a \")\";
", imports = "import ParseTree;
", oldName = "Foo", newName = "Bar");

test bool productionParameter() = testRenameOccurrences({0, 1}, "",
decls = "
    'lexical L = \"l\"+;
    'syntax S[&Foo] = s: &Foo foo;
", oldName = "Foo", newName = "Bar");

test bool parameterizedProduction() = testRenameOccurrences({0, 1}, ""
, decls = "syntax Foo[&T] = Foo[&T] child &T t;"
, oldName = "Foo", newName = "Bar");

test bool startPoduction() = testRenameOccurrences({0, 1}, ""
, decls = "start syntax Foo = start[Foo] child;"
, oldName = "Foo", newName = "Bar");

test bool constructor() = testRenameOccurrences({0, 1}, "
    'S getChild(foo(child)) = child;
", decls = "syntax S = foo: S child;");

test bool exceptedConstructor() = testRenameOccurrences({0, 1, 2, 3, 4}, "",
decls = "
    'syntax S
    '  = foo: \"foo\" S s
    '  | baz: \"baz\"
    '  | atStart: S s!foo!baz
    '  | atEnd: S s!baz!foo
    '  | sandwiched: S s!baz!foo!atEnd
    '  | single: S s!foo
    '  ;
");

test bool exceptConstructorDifferentNonterminal() = testRenameOccurrences({0, 2}, "",
decls = "
    'syntax S
    '   = anotherBut: T t!foo
    '   | foo: \"do not rename me!\"
    '   ;

    'syntax T
    '   = foo: \"foo\"
    '   | bar: \"bar\"
    '   ;
");

test bool exceptedDuplicateConstructorAtEnd() = testRenameOccurrences({0, 1}, "", decls = "
    'syntax S
    '  = foo: \"foo\" S s
    '  | baz: \"baz\"
    '  | notFoo: S s!notFoo!foo
    '  ;
    'syntax T = foo: \"Tfoo\";
");

test bool exceptedDuplicateConstructorAtStart() = testRenameOccurrences({0, 1}, "", decls = "
    'syntax S
    '  = foo: \"foo\" S s
    '  | baz: \"baz\"
    '  | notFoo: S s!foo!notFoo
    '  ;
    'syntax T = foo: \"Tfoo\";
");

test bool syntaxConstructorField() = testRenameOccurrences({0, 1}, "
    'S getChild(S x) = x.foo;
", decls = "syntax S = S foo;");

test bool referencedConstructor() = testRenameOccurrences({0, 1}, "", decls = "
    'lexical L = \"l\"+;
    'syntax S = foo: L l;
    'syntax T =: foo;
");

@expected{illegalRename}
test bool nonterminalInvalidName() = testRename("", decls ="
    'syntax Foo
    '   = f: \"foo\"
    '   ;
", oldName = "Foo", newName = "foo");

@expected{illegalRename}
test bool constructorInvalidName() = testRename("", decls ="
    'syntax S
    '   = foo: \"foo\"
    '   ;
", newName = "Foo");

@expected{illegalRename}
test bool constructorFieldInvalidName() = testRename("", decls ="
    'syntax S
    '   = s: S foo
    '   ;
", newName = "Foo");

test bool lexicalProduction() = testRenameOccurrences({0, 1}, "
    'if (f := [Foo] \"foo\") g = f;
", decls = "lexical Foo = \"foo\"+;"
, oldName = "Foo", newName = "Bar");

test bool lexicalAsParameter() = testRenameOccurrences({0, 1}, "
    'x = [S[Foo]] \"foo\";
", decls = "
    'lexical Foo = \"foo\"+;
    'syntax S[&L] = s: &L l;
", oldName = "Foo", newName = "Bar");

test bool metaVariable() = testRenameOccurrences({0, 1},
    "void f (Tree pt) { if ((S)`\<S foo\>` := pt) x = foo; }"
, decls =
    "syntax S = s: S child;
    'data Tree;"
);

test bool nonterminalsInVModuleStructure() = testRenameOccurrences({
    byText("Left", "syntax Foo = f: \"f\";", {0}), byText("Right", "syntax Foo = g: \"g\";", {0})
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
test bool nonterminalsInWModuleStructureWithoutMerge() = testRenameOccurrences({
    byText("A", "syntax Foo = f: \"f\";", {0}), byText("B", "", {}), byText("C", "syntax Foo = h: \"h\";", {})
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
test bool nonterminalsInWModuleStructureWithMerge() = testRenameOccurrences({
    byText("A", "syntax Foo = f: \"f\";", {0}), byText("B", "syntax Foo = g: \"g\";", {0}), byText("C", "syntax Foo = h: \"h\";", {0})
                    , byText("D",
                        "import A;
                        'import B;
                        'bool func(Foo foo) = foo == f();
                        ", {0}),    byText("E",
                                        "import B;
                                        'import C;
                                        'bool func(Foo foo) = foo == h();", {0})
}, oldName = "Foo", newName = "Bar");

test bool nonterminalsInYModuleStructure() = testRenameOccurrences({
    byText("Left", "syntax Foo = f: \"f\";", {0}), byText("Right", "syntax Foo = g: \"g\";", {0})
                    , byText("Merger",
                        "extend Left;
                        'extend Right;
                        ", {})
                    , byText("User",
                        "import Merger;
                        'bool f(Foo foo) = (foo == f() || foo == g());
                        ", {0})
}, oldName = "Foo", newName = "Bar");

test bool nonterminalsInInvertedVModuleStructure() = testRenameOccurrences({
            byText("Definer", "syntax Foo = f: \"f\" | g: \"g\";", {0}),
    byText("Left", "import Definer;
                   'bool isF(Foo foo) = foo == f();", {0}), byText("Right", "import Definer;
                                                                            'bool isG(Foo foo) = foo == g();", {0})
}, oldName = "Foo", newName = "Bar");

test bool nonterminalsInDiamondModuleStructure() = testRenameOccurrences({
            byText("Definer", "syntax Foo = f: \"f\" | g: \"g\";", {0}),
    byText("Left", "extend Definer;", {}), byText("Right", "extend Definer;", {}),
            byText("User", "import Left;
                           'import Right;
                           'bool isF(Foo foo) = foo == f();
                           'bool isG(Foo foo) = foo == g();", {0, 1})
}, oldName = "Foo", newName = "Bar");

@synopsis{
    Two disjunct module trees. Both trees define `syntax Foo`. Since the trees are disjunct,
    we expect a renaming triggered from the left side leaves the right side untouched.
}
test bool nonterminalsInIIModuleStructure() = testRenameOccurrences({
    byText("LeftDefiner", "syntax Foo = f: \"f\";", {0}),              byText("RightDefiner", "syntax Foo = g: \"g\";", {}),
    byText("LeftExtender", "extend LeftDefiner;", {}),          byText("RightExtender", "extend RightDefiner;", {}),
    byText("LeftUser", "import LeftExtender;
                       'bool func(Foo foo) = foo == f();", {0}),       byText("RightUser", "import RightExtender;
                                                                        'bool func(Foo foo) = foo == g();", {})
}, oldName = "Foo", newName = "Bar");
