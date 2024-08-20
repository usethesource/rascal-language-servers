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
module lang::rascal::tests::rename::Modules

import lang::rascal::tests::rename::TestUtils;

test bool deepModule() = testRenameOccurrences({
    byText("some::path::to::Foo", "
        'data Bool = t() | f();
        'Bool and(Bool l, Bool r) = r is t ? l : f;
        ", {0}, newName = "some::path::to::Bar"),
    byText("Main", "
        'import some::path::to::Foo;
        'void main() {
        '   some::path::to::Foo::Bool b = and(t(), f());
        '}
    ", {0, 1})
}, oldName = "Foo", newName = "Bar");

test bool shadowedModuleWithVar() = testRenameOccurrences({
    byText("Foo", "
        'data Bool = t() | f();
        'Bool and(Bool l, Bool r) = r is t ? l : f;
        ", {0}, newName = "Bar"),
    byText("shadow::Foo", "", {}),
    byText("Main", "
        'import Foo;
        'void main() {
        '   Foo::Bool b = and(t(), f());
        '}
    ", {0, 1})
}, oldName = "Foo", newName = "Bar");

test bool shadowedModuleWithFunc() = testRenameOccurrences({
    byText("Foo", "
        'void f() { fail; }
        ", {0}, newName = "Bar"),
    byText("shadow::Foo", "", {}),
    byText("Main", "
        'import Foo;
        'void main() {
        '   Foo::f();
        '}
    ", {0, 1})
}, oldName = "Foo", newName = "Bar");

test bool singleModule() = testRenameOccurrences({
    byText("util::Foo", "
        'data Bool = t() | f();
        'Bool and(Bool l, Bool r) = r is t ? l : f;
        ", {0}, newName = "util::Bar"),
    byText("Main", "
        'import util::Foo;
        'void main() {
        '   util::Foo::Bool b = and(t(), f());
        '}
    ", {0, 1})
}, oldName = "Foo", newName = "Bar");
