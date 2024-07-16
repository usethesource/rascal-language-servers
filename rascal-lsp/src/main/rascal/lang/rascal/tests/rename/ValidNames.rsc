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
module lang::rascal::tests::rename::ValidNames

import lang::rascal::tests::rename::TestUtils;
import lang::rascal::lsp::refactor::Exception;

test bool renameToReservedName() {
    edits = getEdits("int foo = 8;", 0, "foo", "int", "", "");
    newNames = {name | e <- edits, changed(_, replaces) := e
                     , r <- replaces, replace(_, name) := r};

    return newNames == {"\\int"};
}

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
