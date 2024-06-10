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

test bool shadowVariableInInnerScope() = renameTest("
    'int foo = 8;
    '{
    '    int bar = 9;
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
