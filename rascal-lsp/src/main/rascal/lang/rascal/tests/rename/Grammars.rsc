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

test bool productionFromDef() = expectEq({0, 1, 2, 3}, testRenameOccurrences("
    'Foo func(Foo f) = f.child;
", decls = "syntax Foo = Foo child;"
, oldName = "Foo", newName = "Bar"));

test bool productionFromTypeUse() = expectEq({0, 1, 2, 3}, testRenameOccurrences("
    'Foo func(Foo f) = f.child;
", decls = "syntax Foo = Foo child;"
, cursorAtOldNameOccurrence = 1, oldName = "Foo", newName = "Bar"));

test bool productionFromConcreteUse() = expectEq({0, 1, 2, 3, 4}, testRenameOccurrences("
    'Foo func((Foo) `\<Foo child\>`) = child;
", decls = "syntax Foo = Foo child;"
, cursorAtOldNameOccurrence = 1, oldName = "Foo", newName = "Bar"));

test bool productionFromConcreteFieldUse() = expectEq({0, 1, 2, 3, 4}, testRenameOccurrences("
    'Foo func((Foo) `\<Foo child\>`) = child;
", decls = "syntax Foo = Foo child;"
, cursorAtOldNameOccurrence = 2, oldName = "Foo", newName = "Bar"));

test bool productionFromPatternUse() = expectEq({0, 1, 2}, testRenameOccurrences("
    'value t = \"tree\";
    'if (/Foo f := t) x = f;
", decls = "syntax Foo = Foo child;"
, cursorAtOldNameOccurrence = 2, oldName = "Foo", newName = "Bar"));

test bool productionFromReifiedType() = expectEq({0, 1, 2}, testRenameOccurrences("
    't = parse(#Foo, \"foo(aaa)\");
    't = implode(#Foo, t);
", decls = "
    'lexical A = \"a\"+;
    'syntax Foo = s: \"foo\" \"(\" A a \")\";
    '
    'import ParseTree;
", oldName = "Foo", newName = "Bar", cursorAtOldNameOccurrence = 1));

test bool productionParameterFromDef() = expectEq({0, 1}, testRenameOccurrences("",
decls = "
    'lexical L = \"l\"+;
    'syntax S[&Foo] = s: &Foo foo;
", oldName = "Foo", newName = "Bar"));

test bool productionParameterFromUse() = expectEq({0, 1}, testRenameOccurrences("",
decls = "
    'lexical L = \"l\"+;
    'syntax S[&Foo] = s: &Foo foo;
", cursorAtOldNameOccurrence = 1, oldName = "Foo", newName = "Bar"));

test bool parameterizedProductionFromDef() = expectEq({0, 1}, testRenameOccurrences(""
, decls = "syntax Foo[&T] = Foo[&T] child &T t;"
, oldName = "Foo", newName = "Bar"));

test bool parameterizedProductionFromDef() = expectEq({0, 1}, testRenameOccurrences(""
, decls = "syntax Foo[&T] = Foo[&T] child &T t;"
, cursorAtOldNameOccurrence = 1, oldName = "Foo", newName = "Bar"));

test bool startPoductionFromDef() = expectEq({0, 1}, testRenameOccurrences(""
, decls = "start syntax Foo = start[Foo] child;"
, oldName = "Foo", newName = "Bar"));

test bool startPoductionFromUse() = expectEq({0, 1}, testRenameOccurrences(""
, decls = "start syntax Foo = start[Foo] child;"
, cursorAtOldNameOccurrence = 1, oldName = "Foo", newName = "Bar"));

test bool constructorFromDef() = expectEq({0, 1}, testRenameOccurrences("
    'S getChild(foo(child)) = child;
", decls = "syntax S = foo: S child;"));

test bool constructorFromUse() = expectEq({0, 1}, testRenameOccurrences("
    'S getChild(foo(child)) = child;
", decls = "syntax S = foo: S child;"
, cursorAtOldNameOccurrence = 1));

test bool exceptedConstructorFromDef() = expectEq({0, 1, 2}, testRenameOccurrences("
    'S getChild(foo(child)) = child;
", decls = "
    'syntax S
    '  = foo: \"foo\" S s
    '  | baz: \"baz\"
    '  | notFoo: S s!foo!notFoo
    '  ;
"));

test bool exceptedConstructorFromUse() = expectEq({0, 1, 2}, testRenameOccurrences("
    'S getChild(foo(child)) = child;
", decls = "
    'syntax S
    '  = foo: \"foo\" S s
    '  | baz: \"baz\"
    '  | notFoo: S s!foo!notFoo
    '  ;
", cursorAtOldNameOccurrence = -1));

@expected{unsupportedRename}
test bool exceptedConstructorFromExcept() = expectEq({0, 1, 2}, testRenameOccurrences("
    'S getChild(foo(child)) = child;
", decls = "
    'syntax S
    '  = foo: \"foo\" S s
    '  | baz: \"baz\"
    '  | notFoo: S s!foo!notFoo
    '  ;
", cursorAtOldNameOccurrence = 1));

test bool exceptedConstructorMultiple() = expectEq({0, 1, 2}, testRenameOccurrences("", decls = "
    'syntax S
    '  = foo: \"foo\" S s
    '  | baz: \"baz\"
    '  | notFoo: S s!foo!notFoo
    '  | probablyBaz: S s!foo!notFoo!probablyBaz
    '  ;
"));

test bool exceptedDuplicateConstructor1() = expectEq({0, 1, 2}, testRenameOccurrences("", decls = "
    'syntax S
    '  = foo: \"foo\" S s
    '  | baz: \"baz\"
    '  | notFoo: S s!notFoo!foo
    '  ;
    'syntax T = foo: \"Tfoo\";
"));

test bool exceptedDuplicateConstructor2() = expectEq({0, 1, 2}, testRenameOccurrences("", decls = "
    'syntax S
    '  = foo: \"foo\" S s
    '  | baz: \"baz\"
    '  | notFoo: S s!foo!notFoo
    '  ;
    'syntax S = foo: \"Tfoo\";
"));

test bool fieldFromDef() = expectEq({0, 1}, testRenameOccurrences("
    'S getChild(S x) = x.foo;
", decls = "syntax S = S foo;"));

test bool fieldFromUse() = expectEq({0, 1}, testRenameOccurrences("
    'S getChild(S x) = x.foo;
", decls = "syntax S = S foo;"
, cursorAtOldNameOccurrence = 1));

test bool referencedConstructorFromDef() = expectEq({0, 1}, testRenameOccurrences("", decls = "
    'lexical L = \"l\"+;
    'syntax S = foo: L l;
    'syntax T =: foo;
"));

test bool referencedConstructorFromDef() = expectEq({0, 1}, testRenameOccurrences("", decls = "
    'lexical L = \"l\"+;
    'syntax S = foo: L l;
    'syntax T =: foo;
", cursorAtOldNameOccurrence = 1));

test bool lexicalFromDef() = expectEq({0, 1}, testRenameOccurrences("
    'if (f := [Foo] \"foo\") g = f;
", decls = "lexical Foo = \"foo\"+;"
, oldName = "Foo", newName = "Bar"));

test bool lexicalFromUse() = expectEq({0, 1}, testRenameOccurrences("
    'if (f := [Foo] \"foo\") g = f;
", decls = "lexical Foo = \"foo\"+;"
, oldName = "Foo", newName = "Bar"
, cursorAtOldNameOccurrence = 1));

test bool lexicalFromParameter() = expectEq({0, 1}, testRenameOccurrences("
    'x = [S[Foo]] \"foo\";
", decls = "
    'lexical Foo = \"foo\"+;
    'syntax S[&L] = s: &L l;
", oldName = "Foo", newName = "Bar"
, cursorAtOldNameOccurrence = 1));
