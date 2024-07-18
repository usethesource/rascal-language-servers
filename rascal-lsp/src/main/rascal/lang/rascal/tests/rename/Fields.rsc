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
import lang::rascal::lsp::refactor::Exception;

test bool dataFieldAtDef() = {0, 1} == testRenameOccurrences("
    'D oneTwo = d(1, 2);
    'x = d.foo;
    ", decls = "data D = d(int foo, int bar);"
);

test bool dataFieldAtUse() = {0, 1} == testRenameOccurrences("
    'D oneTwo = d(1, 2);
    'x = d.foo;
    ", decls = "data D = d(int foo, int bar);"
, cursorAtOldNameOccurrence = 1);

test bool duplicateDataField() = {0, 1, 2} == testRenameOccurrences("
    'x = d(1, 2);
    'y = x.foo;
    ", decls = "data D = d(int foo) | d(int foo, int bar);"
, cursorAtOldNameOccurrence = 1);

test bool crossModuleDataFieldAtDef() = testRenameOccurrences({
    byText("Foo", "data D = a(int foo) | b(int bar);", {0}),
    byText("Main", "
    'import Foo;
    'void main() {
    '   f = a(8);
    '   g = a.foo;
    '}
    ", {0})
}, <"Foo", "foo", 0>);

test bool crossModuleDataFieldAtUse() = testRenameOccurrences({
    byText("Foo", "data D = a(int foo) | b(int bar);", {0}),
    byText("Main", "
    'import Foo;
    'void main() {
    '   f = a(8);
    '   g = a.foo;
    '}
    ", {0})
}, <"Main", "foo", 0>);

@expected{unsupportedRename}
test bool relFieldAtDef() = {0, 1} == testRenameOccurrences("
    'rel[str foo, str baz] r1 = {};
    'foos = r1.foo;
    'rel[str foo, str baz] r2 = {};
");

@expected{unsupportedRename}
test bool relFieldAtUse() = {0, 1} == testRenameOccurrences("
    'rel[str foo, str baz] r1 = {};
    'foos = r1.foo;
    'rel[str foo, str baz] r2 = {};
", cursorAtOldNameOccurrence = 1);

@expected{unsupportedRename}
test bool tupleFieldAtDef() = {0, 1} == testRenameOccurrences("
    'tuple[int foo] t = \<8\>;
    'y = t.foo;
");

@expected{unsupportedRename}
test bool tupleFieldAtUse() = {0, 1} == testRenameOccurrences("
    'tuple[int foo] t = \<8\>;
    'y = t.foo;
", cursorAtOldNameOccurrence = 1);

// We would prefer an illegalRename exception here
@expected{unsupportedRename}
test bool builtinField() = testRename("
    'loc l = |unknown:///|;
    'f = l.top;
", oldName = "top", newName = "summit");
