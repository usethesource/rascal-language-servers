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
module lang::rascal::tests::rename::ValidNames

import lang::rascal::tests::rename::TestUtils;
import analysis::diff::edits::TextEdits;

test bool renameToReservedName() {
    edits = getEdits("int foo = 8;", 0, "foo", "int", "", "");
    newNames = {name | e <- edits<0>
                     , r <- e.edits
                     , name := r.replacement};

    return newNames == {"\\int"};
}

test bool renameFromMinusName() = testRenameOccurrences({0}, "int \\foo-bar = 0;", oldName = "foo-bar");

test bool renameToMinusName() = testRenameOccurrences({0}, "int foo = 0;", newName = "foo-bar");

test bool renameToUnescapedQualifiedName() = testRenameOccurrences({
    byText("FooSyntax", "syntax S = \"s\";", {0}, newName = "syntax::Foo"),
    byText("Main", "
        'import FooSyntax;
        'import ParseTree;
        'void main() {
        '   s = parse(#FooSyntax::S, \"s\");
        '}
    ", {0, 1}, skipCursors = {1})
}, oldName = "FooSyntax", newName = "syntax::Foo");

test bool renameToEscapedQualifiedName() = testRenameOccurrences({
    byText("FooSyntax", "syntax S = \"s\";", {0}, newName = "syntax::Foo"),
    byText("Main", "
        'import FooSyntax;
        'import ParseTree;
        'void main() {
        '   s = parse(#FooSyntax::S, \"s\");
        '}
    ", {0, 1}, skipCursors = {1})
}, oldName = "FooSyntax", newName = "\\syntax::Foo");

@expected{illegalRename}
test bool renameToUsedReservedName() = testRename("
    'int \\int = 0;
    'int foo = 8;
", newName = "int");

@expected{illegalRename}
test bool newNameIsNonAlphaNumeric() = testRename("int foo = 8;", newName = "b@r");

@expected{illegalRename}
test bool newNameIsNumber() = testRename("int foo = 8;", newName = "8");

@expected{illegalRename}
test bool newNameHasNumericPrefix() = testRename("int foo = 8;", newName = "8abc");

@expected{illegalRename}
test bool newNameIsEscapedInvalid() = testRename("int foo = 8;", newName = "\\8int");

@expected{illegalRename}
test bool qualifiedNameWhereNameExpected() = testRename("int foo = 8;", newName = "Foo::foo");
