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
module lang::rascal::tests::rename::Fields

import lang::rascal::tests::rename::TestUtils;
import util::refactor::Exception;

test bool constructorField() = testRenameOccurrences({0, 1, 2}, "
    'D oneTwo = d(1, 2);
    'x = oneTwo.foo;
    'b = oneTwo has foo;
    ", decls = "data D = d(int foo, int baz);"
);

test bool constructorKeywordField() = testRenameOccurrences({0, 1, 2, 3}, "
    'D dd = d(foo=1, baz=2);
    'x = dd.foo;
    'b = dd has foo;
    ", decls="data D = d(int foo = 0, int baz = 0);"
);

test bool commonKeywordField() = testRenameOccurrences({0, 1, 2, 3}, "
    'D oneTwo = d(foo=1, baz=2);
    'x = oneTwo.foo;
    'b = oneTwo has foo;
    ", decls = "data D(int foo = 0, int baz = 0) = d();"
);

test bool multipleConstructorField() = testRenameOccurrences({0, 1, 2, 3}, "
    'x = d(1, 2);
    'y = x.foo;
    'b = x has foo;
    ", decls = "data D = d(int foo) | d(int foo, int baz);"
);

@expected{illegalRename}
test bool duplicateConstructorField() = testRename("", decls =
    "data D = d(int foo, int bar);"
);

@expected{illegalRename}
test bool differentTypeAcrossConstructorField() = testRename("", decls =
    "data D = d0(int foo) | d1(str bar);"
);

test bool sameTypeFields() = testRenameOccurrences({0, 1},
    "x = d({}, {});
    'xx = x.foo;
    'xy = x.baz;
    ",
    decls = "data D = d(set[loc] foo, set[loc] baz);"
);

test bool commonKeywordFieldsSameType() = testRenameOccurrences({0, 1},
    "x = d();
    'xx = x.foo;
    'xy = x.baz;
    ",
    decls = "data D (set[loc] foo = {}, set[loc] baz = {})= d();"
);

test bool sameNameFields() = testRenameOccurrences({0, 2, 3}, "
    'D x = d(8);
    'int i = x.foo;
    'bool b = x has foo;
", decls = "
    'data D = d(int foo);
    'data E = e(int foo);
");

test bool sameNameADTFields() = testRenameOccurrences({
    byText("Definer", "
        'data D = d(int foo);
        'bool hasFoo(D dd) = dd has foo;
        ", {0, 1})
  , byText("Unrelated", "data D = d(int foo);", {})
});

test bool sameNameFieldsDisconnectedModules() = testRenameOccurrences({
    byText("A", "
        'data D = d(int foo);
        'bool hasFoo(D dd) = dd has foo;
        ", {0, 1})
  , byText("B", "data E = e(int foo);", {})
});

test bool complexDataType() = testRenameOccurrences({0, 1},
    "WorkspaceInfo ws = workspaceInfo(
    '   ProjectFiles() { return {}; },
    '   ProjectFiles() { return {}; },
    '   set[TModel](ProjectFiles projectFiles) { return { tmodel() }; }
    ');
    'ws.projects += {};
    'ws.sourceFiles += {};
    ",
    decls = "
    'data TModel = tmodel();
    'data Define;
    'data AType;
    'alias ProjectFiles = rel[loc projectFolder, loc file];
    'data WorkspaceInfo (
    '   // Instance fields
    '   // Read-only
    '   rel[loc use, loc def] useDef = {},
    '   set[Define] defines = {},
    '   set[loc] sourceFiles = {},
    '   map[loc, Define] definitions = (),
    '   map[loc, AType] facts = (),
    '   set[loc] projects = {}
    ') = workspaceInfo(
    '   ProjectFiles() preloadFiles,
    '   ProjectFiles() allFiles,
    '   set[TModel](ProjectFiles) tmodelsForLocs
    ');"
, oldName = "sourceFiles", newName = "sources");

test bool crossModuleConstructorField() = testRenameOccurrences({
    byText("Foo", "data D = a(int foo) | b(int baz);", {0}),
    byText("Main", "
    'import Foo;
    'void main() {
    '   f = a(8);
    '   g = a.foo;
    '}
    ", {0})
});

test bool extendedConstructorField() = testRenameOccurrences({
    byText("Scratch1", "
        'data Foo = f(int foo);
        ", {0}),
    byText("Scratch2", "
        'extend Scratch1;
        'data Foo = g(int foo);
        'bool hasFoo(Foo ff) = ff has foo;
        ", {0, 1})
});

test bool dataTypeReusedName() = testRenameOccurrences({
    byText("Scratch1", "
        'data Foo = f();
        ", {0}),
    byText("Scratch2", "
        'data Foo = g();
        ", {})
}, oldName = "Foo", newName = "Bar");

test bool dataFieldReusedName() = testRenameOccurrences({
    byText("Scratch1", "
        'data Foo = f(int foo);
        ", {0}),
    byText("Scratch2", "
        'data Foo = f(int foo);
        ", {})
});

test bool dataKeywordFieldReusedName() = testRenameOccurrences({
    byText("Scratch1", "
        'data Foo = f(int foo = 0);
        ", {0}),
    byText("Scratch2", "
        'data Foo = f(int foo = 0);
        ", {})
});

test bool dataCommonKeywordFieldReusedName() = testRenameOccurrences({
    byText("Scratch1", "
        'data Foo(int foo = 0) = f();
        ", {0}),
    byText("Scratch2", "
        'data Foo(int foo = 0) = g();
        ", {})
});

test bool dataAsFormalField() = testRenameOccurrences({0, 1}, "
    'int getChild(D d) = d.foo;
", decls = "data D = x(int foo);");

test bool relField() = testRenameOccurrences({0, 1}, "
    'rel[str foo, str baz] r = {};
    'f = r.foo;
");

test bool lrelField() = testRenameOccurrences({0, 1}, "
    'lrel[str foo, str baz] r = [];
    'f = r.foo;
");

test bool relSubscript() = testRenameOccurrences({0, 1}, "
    'rel[str foo, str baz] r = {};
    'x = r\<foo\>;
");

test bool relSubscriptWithVar() = testRenameOccurrences({0, 2}, "
    'rel[str foo, str baz] r = {};
    'str foo = \"foo\";
    'x = r\<foo\>;
");

test bool tupleFieldSubscriptUpdate() = testRenameOccurrences({0, 1, 2}, "
    'tuple[str foo, int baz] t = \<\"one\", 1\>;
    'u = t[foo = \"two\"];
    'v = u.foo;
");

test bool tupleFieldAccessUpdate() = testRenameOccurrences({0, 1}, "
    'tuple[str foo, int baz] t = \<\"one\", 1\>;
    't.foo = \"two\";
");

test bool similarCollectionTypes() = testRenameOccurrences({0, 1, 2, 3, 4}, "
    'rel[str foo, int baz] r = {};
    'lrel[str foo, int baz] lr = [];
    'set[tuple[str foo, int baz]] st = {};
    'list[tuple[str foo, int baz]] lt = [];
    'tuple[str foo, int baz] t = \<\"\", 0\>;
");

test bool differentRelWithSameField() = testRenameOccurrences({0, 1}, "
    'rel[str foo, int baz] r1 = {};
    'foos1 = r1.foo;
    'rel[int n, str foo] r2 = {};
    'foos2 = r2.foo;
");

test bool tupleField() = testRenameOccurrences({0, 1}, "
    'tuple[int foo] t = \<8\>;
    'y = t.foo;
");

// We would prefer an illegalRename exception here
@expected{illegalRename}
test bool builtinFieldSimpleType() = testRename("
    'loc l = |unknown:///|;
    'f = l.top;
", oldName = "top", newName = "summit");
// We would prefer an illegalRename exception here

@expected{illegalRename}
test bool builtinFieldCollectionType() = testRename("
    'loc l = |unknown:///|;
    'f = l.ls;
", oldName = "ls", newName = "contents");
