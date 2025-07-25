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
module lang::rascal::tests::rename::Performance

import lang::rascal::tests::rename::TestUtils;

import lang::rascalcore::check::Checker;
import lang::rascalcore::check::RascalConfig;

import IO;
import List;
import util::Reflective;

int LARGE_TEST_SIZE = 200;
test bool largeTest() = testRenameOccurrences(({0} | it + {foos + 3, foos + 4, foos + 5} | i <- [0..LARGE_TEST_SIZE], foos := 5 * i), (
    "int foo = 8;"
    | "<it>
      'int f<i>(int foo) = foo;
      'foo = foo + foo;"
    | i <- [0..LARGE_TEST_SIZE])
, skipCursors = toSet([1..LARGE_TEST_SIZE * 5]));

@expected{illegalRename}
test bool failOnError() = testRename("int foo = x + y;");

@expected{illegalRename}
test bool failOnErrorInImport() = testRenameOccurrences({
    byText("Foo", "int foo = x + y;", {0}, skipCursors = {0}),
    byText("Main", "import Foo;
                   'int baz = Foo::foo;", {0})
});

test bool doNotFailOnUnrelatedError() = testRenameOccurrences({
    byText("Unrelated", "int x = \"notanumber\";", {}),
    byText("Main", "int foo = 8;", {0})
});

test bool incrementalTypeCheck() {
    procLoc = |memory://tests/incremental|;
    pcfg = getTestPathConfig(procLoc);
    procSrc = pcfg.srcs[0];

    modName = "A";
    moduleLoc = procSrc + "<modName>.rsc";
    writeFile(moduleLoc, "module <modName>
        'int foo() = 1;
        'void main() { x = foo(); }
    ");

    ms = rascalTModelForNames([modName], rascalCompilerConfig(pcfg), dummy_compile1);
    res = testRenameOccurrences({byLoc(modName, moduleLoc, {0, 1})});
    remove(procLoc);
    return res;
}

test bool ignoredModule() = testRenameOccurrences({
    byText("Ignored", "
        'import Main;
        'int quz() = foo();", {}, annotations = "@ignoreCompiler{For test purposes.}"),
    byText("Main", "int foo() = 8;", {0})
});
