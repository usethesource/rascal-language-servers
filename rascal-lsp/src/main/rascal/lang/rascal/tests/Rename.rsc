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
module lang::rascal::tests::Rename

import lang::rascal::lsp::Rename;

import Exception;
import IO;

import lang::rascal::\syntax::Rascal;
import lang::rascalcore::check::Checker;
import analysis::diff::edits::TextEdits;

import util::Math;
import util::Reflective;

test bool freshName() = [] != renameTest("
    'int foo = 8;
    'int qux = 10;
");

test bool shadowVariableInInnerScope() = [] != renameTest("
    'int foo = 8;
    '{
    '   int bar = 9;
    '}
");

test bool shadowParameter() = [] != renameTest("
    'int foo = 8;
    'int f(int bar) {
    '   return bar;
    '}
");

@expected{IllegalRename}
test bool implicitVariableDeclarationInSameScopeBecomesUse() = [] != renameTest("
    'int foo = 8;
    'bar = 9;
");

@expected{IllegalRename}
test bool implicitVariableDeclarationInInnerScopeBecomesUse() = [] != renameTest("
    'int foo = 8;
    '{
    '   bar = 9;
    '}
");

@expected{IllegalRename}
test bool doubleVariableDeclaration() = [] != renameTest("
    'int foo = 8;
    'int bar = 9;
");

@expected{IllegalRename}
test bool doubleVariableAndParameterDeclaration() = [] != renameTest("
    'int f(int foo) {
    '   int bar = 9;
    '   return foo + bar;
    '}
");

test bool adjacentScopes() = [] != renameTest("
    '{
    '   int foo = 8;
    '}
    '{
    '   int bar = 9;
    '}
");

@expected{IllegalRename}
test bool implicitPatterVariableInSameScopeBecomesUse() = [] != renameTest("
    'int foo = 8;
    'bar := 9;
");

@expected{IllegalRename}
test bool implicitNestedPatterVariableInSameScopeBecomesUse() = [] != renameTest("
    'int foo = 8;
    '\<bar, _\> := \<9, 99\>;
");

@expected{IllegalRename}
test bool implicitPatterVariableInInnerScopeBecomesUse() = [] != renameTest("
    'int foo = 8;
    'if (bar := 9) {
    '   temp = 2 * bar;
    '}
");

@expected{IllegalRename}
test bool shadowDeclaration() = [] != renameTest("
    'int foo = 8;
    'if (int bar := 9) {
    '   foo = 2 * bar;
    '}
");

// Although this is fine statically, it will cause runtime errors when `bar` is called
// > A value of type int is not something you can call like a function, a constructor or a closure.
@expected{IllegalRename}
test bool doubleVariableAndFunctionDeclaration() = [] != renameTest("
    'int foo = 8;
    'void bar() {}
");

test bool renameToReservedName() {
    edits = renameTest("int foo = 8;", newName = "int");

    newNames = {name | e <- edits, changed(_, replaces) := e
                     , r <- replaces, replace(_, name) := r};

    return newNames == {"\\int"};
}

@expected{IllegalRename}
test bool renameToUsedReservedName() = [] != renameTest("
    'int \\int = 0;
    'int foo = 8;
", newName = "int");

@expected{IllegalRename}
test bool newNameIsNonAlphaNumeric() = [] != renameTest("int foo = 8;", newName = "b@r");

@expected{IllegalRename}
test bool newNameIsNumber() = [] != renameTest("int foo = 8;", newName = "8");

@expected{IllegalRename}
test bool newNameHasNumericPrefix() = [] != renameTest("int foo = 8;", newName = "8abc");

@expected{IllegalRename}
test bool newNameIsEscapedInvalid() = [] != renameTest("int foo = 8;", newName = "\\8int");



//// Fixtures and utility functions

private PathConfig testPathConfig = pathConfig(
        bin=|target://test-project|,
        libs=[
            |lib://rascal|,
            |target://test-lib|],
        srcs=[resolveLocation(|project://rascal-vscode-extension/test-workspace/test-project/src/main/rascal|)]);

// Test renaming given the location of a module and rename parameters
list[DocumentEdit] renameTest(loc singleModule, int cursorAtOldNameOccurrence, str oldName, str newName) {
    loc f = resolveLocation(singleModule);

    list[ModuleMessages] modMsgs = checkAll(f, testPathConfig);
    // TODO Check if there are no errors

    return renameTest(parseModuleWithSpaces(f), cursorAtOldNameOccurrence, oldName, newName);
}

// Test renaming given a module Tree and rename parameters
list[DocumentEdit] renameTest(start[Module] m, int cursorAtOldNameOccurrence, str oldName, str newName) {
    Tree cursor = [n | /FunctionBody b := m.top, /Name n := b, "<n>" == oldName][cursorAtOldNameOccurrence];
    return renameRascalSymbol(m, cursor, {}, testPathConfig, newName);
}

// Test renaming given a module as a string and rename parameters
list[DocumentEdit] renameTest(str stmtsStr, int cursorAtOldNameOccurrence = 0, str oldName = "foo", str newName = "bar") {
    str moduleName = "TestModule<abs(arbInt())>";
    str moduleStr =
    "module <moduleName>
    'void main() {
    '<stmtsStr>
    '}";

    // Write the file to disk (and clean up later) to easily emulate typical editor behaviour
    loc moduleFileName = |project://rascal-vscode-extension/test-workspace/test-project/src/main/rascal/<moduleName>.rsc|;
    writeFile(moduleFileName, moduleStr);
    list[DocumentEdit] edits = [];
    try {
        edits = renameTest(moduleFileName, cursorAtOldNameOccurrence, oldName, newName);
    } catch bool _: {
        // A catch block is mandatory, but we do not want to catch anything.
        // We just want to use `finally` here to clean up regardless if the rename call fails.
        fail;
    } finally {
        remove(moduleFileName);
    }

    return edits;
}

void main() {
    edits = renameTest(
        |project://rascal-vscode-extension/test-workspace/test-project/src/main/rascal/SingleModuleRenameTest.rsc|,
        0, "x", "y"
    );
    iprintln(edits);
}
