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

test bool freshName() = {0} == testRenameOccurrences("
    'int foo = 8;
    'int qux = 10;
");

test bool shadowVariableInInnerScope() = {0} == testRenameOccurrences("
    'int foo = 8;
    '{
    '   int bar = 9;
    '}
");

test bool parameterShadowsVariable() = {0} == testRenameOccurrences("
    'int foo = 8;
    'int f(int bar) {
    '   return bar;
    '}
");

@expected{illegalRename}
test bool implicitVariableDeclarationInSameScopeBecomesUse() = testRename("
    'int foo = 8;
    'bar = 9;
");

@expected{illegalRename}
test bool implicitVariableDeclarationInInnerScopeBecomesUse() = testRename("
    'int foo = 8;
    '{
    '   bar = 9;
    '}
");

@expected{illegalRename}
test bool doubleVariableDeclaration() = testRename("
    'int foo = 8;
    'int bar = 9;
");

@expected{illegalRename}
test bool doubleVariableAndParameterDeclaration() = testRename("
    'int f(int foo) {
    '   int bar = 9;
    '   return foo + bar;
    '}
");

test bool adjacentScopes() = {0} == testRenameOccurrences("
    '{
    '   int foo = 8;
    '}
    '{
    '   int bar = 9;
    '}
");

@expected{illegalRename}
test bool implicitPatterVariableInSameScopeBecomesUse() = testRename("
    'int foo = 8;
    'bar := 9;
");

@expected{illegalRename}
test bool implicitNestedPatterVariableInSameScopeBecomesUse() = testRename("
    'int foo = 8;
    '\<bar, _\> := \<9, 99\>;
");

@expected{illegalRename}
test bool implicitPatterVariableInInnerScopeBecomesUse() = testRename("
    'int foo = 8;
    'if (bar := 9) {
    '   temp = 2 * bar;
    '}
");

test bool explicitPatternVariableInInnerScope() = {0} == testRenameOccurrences("
    'int foo = 8;
    'if (int bar := 9) {
    '   bar = 2 * bar;
    '}
");

test bool becomesPatternInInnerScope() = {0} == testRenameOccurrences("
    'int foo = 8;
    'if (bar : int _ := 9) {
    '   bar = 2 * bar;
    '}
");

@expected{illegalRename}
test bool implicitPatternVariableBecomesInInnerScope() = testRename("
    'int foo = 8;
    'if (bar : _ := 9) {
    '   bar = 2 * foo;
    '}
");

@expected{illegalRename}
test bool explicitPatternVariableBecomesInInnerScope() = testRename("
    'int foo = 8;
    'if (bar : int _ := 9) {
    '   bar = 2 * foo;
    '}
");

@expected{illegalRename}
test bool shadowDeclaration() = testRename("
    'int foo = 8;
    'if (int bar := 9) {
    '   foo = 2 * bar;
    '}
");

// Although this is fine statically, it will cause runtime errors when `bar` is called
// > A value of type int is not something you can call like a function, a constructor or a closure.
@expected{illegalRename}
test bool doubleVariableAndFunctionDeclaration() = testRename("
    'int foo = 8;
    'void bar() {}
");

// Although this is fine statically, it will cause runtime errors when `bar` is called
// > A value of type int is not something you can call like a function, a constructor or a closure.
@expected{illegalRename}
test bool doubleFunctionAndVariableDeclaration() = testRename("
    'void bar() {}
    'foo = 8;
");

@expected{illegalRename}
test bool doubleFunctionAndNestedVariableDeclaration() = testRename("
    'bool bar() = true;
    'void f() {
    '   int foo = 0;
    '}
");

@expected{illegalRename}
test bool captureFunctionParameter() = testRename("
    'int f(int foo) {
    '   int bar = 9;
    '   return foo + bar;
    '}
");

test bool paremeterShadowsParameter1() = {0, 3} == testRenameOccurrences("
    'int f1(int foo) {
    '   int f2(int foo) {
    '       int baz = 9;
    '       return foo + baz;
    '   }
    '   return f2(foo);
    '}
");

test bool paremeterShadowsParameter2() = {1, 2} == testRenameOccurrences("
    'int f1(int foo) {
    '   int f2(int foo) {
    '       int baz = 9;
    '       return foo + baz;
    '   }
    '   return f2(foo);
    '}
", cursorAtOldNameOccurrence = 1);

@expected{illegalRename}
test bool paremeterShadowsParameter3() = testRename("
    'int f(int bar) {
    '   int g(int baz) {
    '       int h(int foo) {
    '           return bar;
    '       }
    '       return h(baz);
    '   }
    '   return g(bar);
    '}
");

test bool nestedFunctionParameter() = {0, 1} == testRenameOccurrences("
    'int f(int foo, int baz) {
    '   return foo;
    '}
");

test bool nestedRecursiveFunctionName() = {0, 1, 2, 3} == testRenameOccurrences("
    'int fib(int n) {
    '   switch (n) {
    '       case 0: {
    '           return 1;
    '       }
    '       case 1: {
    '           return 1;
    '       }
    '       default: {
    '           return fib(n - 1) + fib(n - 2);
    '       }
    '   }
    '}
    '
    'fib(7);
", oldName = "fib", newName = "fibonacci", cursorAtOldNameOccurrence = -1);

@expected{unsupportedRename}
test bool recursiveFunctionName() = {0, 1, 2, 3} == testRenameOccurrences("fib(7);", decls = "
    'int fib(int n) {
    '   switch (n) {
    '       case 0: {
    '           return 1;
    '       }
    '       case 1: {
    '           return 1;
    '       }
    '       default: {
    '           return fib(n - 1) + fib(n - 2);
    '       }
    '   }
    '}
", oldName = "fib", newName = "fibonacci", cursorAtOldNameOccurrence = -1);

test bool nestedPublicFunction() = {0, 1} == testRenameOccurrences("
    'public int foo(int f) {
    '   return f;
    '}
    'foo(1);
");

test bool nestedDefaultFunction() = {0, 1} == testRenameOccurrences("
    'int foo(int f) {
    '   return f;
    '}
    'foo(1);
");

test bool nestedPrivateFunction() = {0, 1} == testRenameOccurrences("
    'private int foo(int f) {
    '   return f;
    '}
    'foo(1);
");

test bool outerNestedFunctionParameter() = {0, 3} == testRenameOccurrences("
    'int f(int foo) {
    '   int g(int foo) {
    '       return foo;
    '   }
    '   return f(foo);
    '}
");

test bool innerNestedFunctionParameter() = {1, 2} == testRenameOccurrences("
    'int f(int foo) {
    '   int g(int foo) {
    '       return foo;
    '   }
    '   return f(foo);
    '}
", cursorAtOldNameOccurrence = 1);

@expected{unsupportedRename}
test bool publicFunction() = testRename("foo(1);", decls = "
    'public int foo(int f) {
    '   return f;
    '}
");

@expected{unsupportedRename}
test bool defaultFunction() = testRename("", decls = "
    'int foo(int f) {
    '   return f;
    '}
");

@expected{unsupportedRename}
test bool privateFunction() = testRename("", decls = "
    'private int foo(int f) {
    '   return f;
    '}
");

test bool publicFunctionParameter() = {0, 1} == testRenameOccurrences("", decls = "
    'public int f(int foo) {
    '   return foo;
    '}
");

test bool defaultFunctionParameter() = {0, 1} == testRenameOccurrences("", decls = "
    'int f(int foo) {
    '   return foo;
    '}
");

test bool privateFunctionParameter() = {0, 1} == testRenameOccurrences("", decls = "
    'private int f(int foo) {
    '   return foo;
    '}
");

@expected{illegalRename} test bool doubleParameterDeclaration1() = testRename("int f(int foo, int bar) = 1;");
@expected{illegalRename} test bool doubleParameterDeclaration2() = testRename("int f(int bar, int foo) = 1;");

@expected{illegalRename} test bool doubleNormalAndKeywordParameterDeclaration1() = testRename("int f(int foo, int bar = 9) = 1;");
@expected{illegalRename} test bool doubleNormalAndKeywordParameterDeclaration2() = testRename("int f(int bar, int foo = 8) = 1;");

@expected{illegalRename} test bool doubleKeywordParameterDeclaration1() = testRename("int f(int foo = 8, int bar = 9) = 1;");
@expected{illegalRename} test bool doubleKeywordParameterDeclaration2() = testRename("int f(int bar = 9, int foo = 8) = 1;");

test bool renameParamToConstructorName() = {0, 1} == testRenameOccurrences(
    "int f(int foo) = foo;",
    decls = "data Bar = bar();"
);

@expected{illegalRename}
test bool renameParamToUsedConstructorName() = testRename(
    "Bar f(int foo) = bar(foo);",
    decls = "data Bar = bar(int x);"
);

test bool renameToReservedName() {
    edits = getEdits("int foo = 8;", 0, "foo", "int");

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



//// Fixtures and utility functions

private PathConfig testPathConfig = pathConfig(
        bin=|memory://tests/rename/bin|,
        libs=[|lib://rascal|],
        srcs=[|memory://tests/rename/src|]);

// Test renaming given the location of a module and rename parameters
list[DocumentEdit] getEdits(loc singleModule, int cursorAtOldNameOccurrence, str oldName, str newName, PathConfig pcfg = testPathConfig) {
    void checkNoErrors(list[ModuleMessages] msgs) {
        if (errors := {p | p:program(_, pmsgs) <- msgs, m <- pmsgs, m is error}, errors != {})
            throw errors;
    }

    loc f = resolveLocation(singleModule);

    checkNoErrors(checkAll(f, pcfg));

    return getEdits(parseModuleWithSpaces(f), cursorAtOldNameOccurrence, oldName, newName, pcfg=pcfg);
}

// Test renaming given a module Tree and rename parameters
list[DocumentEdit] getEdits(start[Module] m, int cursorAtOldNameOccurrence, str oldName, str newName, PathConfig pcfg = testPathConfig) {
    Tree cursor = [n | /Name n := m.top, "<n>" == oldName][cursorAtOldNameOccurrence];
    return renameRascalSymbol(m, cursor, {}, pcfg, newName);
}

tuple[list[DocumentEdit], loc] getEditsAndModule(str stmtsStr, int cursorAtOldNameOccurrence, str oldName, str newName, str decls = "", str imports = "") {
    str moduleName = "TestModule<abs(arbInt())>";
    str moduleStr =
    "module <moduleName>
    '<decls>
    '
    'void main() {
    '<stmtsStr>
    '}";

    // Write the file to disk (and clean up later) to easily emulate typical editor behaviour
    loc moduleFileName = |memory://tests/rename/src/<moduleName>.rsc|;
    writeFile(moduleFileName, moduleStr);
    list[DocumentEdit] edits = getEdits(moduleFileName, cursorAtOldNameOccurrence, oldName, newName);

    return <edits, moduleFileName>;
}

list[DocumentEdit] getEdits(str stmtsStr, int cursorAtOldNameOccurrence, str oldName, str newName, str decls = "") {
    <edits, l> = getEditsAndModule(stmtsStr, cursorAtOldNameOccurrence, oldName, newName, decls=decls);
    remove(l);
    return edits;
}

set[int] testRenameOccurrences(str stmtsStr, int cursorAtOldNameOccurrence = 0, str oldName = "foo", str newName = "bar", str decls = "") {
    <edits, moduleFileName> = getEditsAndModule(stmtsStr, cursorAtOldNameOccurrence, oldName, newName, decls=decls);
    start[Module] m = parseModuleWithSpaces(moduleFileName);
    occs = extractRenameOccurrences(m, edits, oldName);
    remove(moduleFileName);
    return occs;
}

// Test renames that are expected to throw an exception
bool testRename(str stmtsStr, int cursorAtOldNameOccurrence = 0, str oldName = "foo", str newName = "bar", str decls = "") {
    getEdits(stmtsStr, cursorAtOldNameOccurrence, oldName, newName decls=decls);
    return false;
}

set[int] extractRenameOccurrences(start[Module] m, list[DocumentEdit] edits, str name) {
    list[loc] oldNameOccurrences = [];
    for (/Name n := m, "<n>" == name) {
        oldNameOccurrences += n.src;
    }

    if ([changed(_, replaces)] := edits) {
        idx = {indexOf(oldNameOccurrences, l) | replace(l, _) <- replaces};
        return idx;
    } else {
        throw "Unexpected changes: <edits>";
    }
}

list[DocumentEdit] multiModuleTest() {
    PathConfig pcfg = pathConfig(
        bin=|memory://tests/rename/bin|,
        libs=[|lib://rascal|
            , resolveLocation(|project://rascal-vscode-extension/test-workspace/test-lib/src/main/rascal/|)],
        srcs=[resolveLocation(|project://rascal-vscode-extension/test-workspace/test-project/src/main/rascal/|)
            , resolveLocation(|project://rascal-vscode-extension/test-workspace/test-lib/src/main/rascal/|)]
    );

    return getEdits(resolveLocation(|project://rascal-vscode-extension/test-workspace/test-project/src/main/rascal/LibCall.rsc|)
                  , 0, "fib", "fibonacci", pcfg=pcfg);
}
