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
module lang::rascal::tests::rename::TestUtils

import lang::rascal::lsp::refactor::Rename; // Module under test

import IO;
import Map;
import Message;
import Set;
import String;

import lang::rascal::\syntax::Rascal; // `Name`

import lang::rascalcore::check::Checker;
import lang::rascalcore::check::RascalConfig;

import analysis::diff::edits::TextEdits;

import util::Math;
import util::Reflective;


//// Fixtures and utility functions
alias TestModule = tuple[str body, set[int] expectedRenameOccs];
bool testRenameOccurrences(map[str, TestModule] modules, tuple[str moduleName, str id, int occ] cursor, str newName = "bar") {
    str testName = "Test<abs(arbInt())>";
    loc testDir = |memory://tests/rename/<testName>|;
    remove(testDir);

    PathConfig pcfg = getMultiModuleConfig(testDir);

    map[loc file, set[int] expectedRenameOccs] modulesByLocation = (l: m.expectedRenameOccs | name <- modules, m := modules[name], l := storeTestModule(testDir, name, m.body));

    res = testRenameOccurrences(modulesByLocation, cursor, newName=newName, pcfg=pcfg);
    remove(testDir);
    return res;
}

bool testRenameOccurrences(map[loc file, set[int] expectedRenameOccs] modules, tuple[str moduleName, str id, int occ] cursor, str newName = "bar", PathConfig pcfg = getTestWorkspaceConfig()) {
    checkNoErrors(check(toList(domain(modules)), rascalCompilerConfig(pcfg)));

    Tree cursorT = findCursor([l | l <- modules, getModuleName(l, pcfg) == cursor.moduleName][0], cursor.id, cursor.occ);

    edits = renameRascalSymbol(cursorT, {d| d <- pcfg.srcs}, pcfg, newName);

    editsPerModule = (name: locsToOccs(m, cursor.id, locs) | changed(f, textedits) <- edits
                                , start[Module] m := parseModuleWithSpaces(f)
                                , str name := getModuleName(f, pcfg)
                                , set[loc] locs := {l | replace(l, _) <- textedits});
    expectedEditsPerModule = (name: modules[l] | l <- modules, name := getModuleName(l, pcfg));

    return editsPerModule == expectedEditsPerModule;
}

set[int] testRenameOccurrences(str stmtsStr, int cursorAtOldNameOccurrence = 0, str oldName = "foo", str newName = "bar", str decls = "") {
    <edits, moduleFileName> = getEditsAndModule(stmtsStr, cursorAtOldNameOccurrence, oldName, newName, decls=decls);
    occs = extractRenameOccurrences(moduleFileName, edits, oldName);
    remove(moduleFileName);
    return occs;
}

list[DocumentEdit] getEdits(str stmtsStr, int cursorAtOldNameOccurrence, str oldName, str newName, str decls = "") {
    <edits, l> = getEditsAndModule(stmtsStr, cursorAtOldNameOccurrence, oldName, newName, decls=decls);
    remove(l);
    return edits;
}

// Test renames that are expected to throw an exception
bool testRename(str stmtsStr, int cursorAtOldNameOccurrence = 0, str oldName = "foo", str newName = "bar", str decls = "") {
    edits = getEdits(stmtsStr, cursorAtOldNameOccurrence, oldName, newName, decls=decls);

    print("UNEXPECTED EDITS: ");
    iprintln(edits);

    return false;
}


private PathConfig testPathConfig = pathConfig(
        bin=|memory://tests/rename/bin|,
        libs=[|lib://rascal|],
        srcs=[|memory://tests/rename/src|],
        resources=|memory://tests/rename/resources|,
        generatedSources=|memory://tests/rename/generated-sources|);

private PathConfig getMultiModuleConfig(loc testDir) {
    return pathConfig(
        bin=testDir + "bin",
        libs=[|lib://rascal|],
        srcs=[testDir + "rascal"],
        resources=testDir + "resources",
        generatedSources=testDir + "generated-sources"
    );
}

private PathConfig getTestWorkspaceConfig() {
    return pathConfig(
        bin=|project://rascal-vscode-extension/test-workspace/test-project/target|,
        libs=[|lib://rascal|],
        srcs=[|project://rascal-vscode-extension/test-workspace/test-project/src/main|
            , |project://rascal-vscode-extension/test-workspace/test-lib/src/main|],
        resources=|memory://tests/rename/resources|,
        generatedSources=|memory://tests/rename/generatedSources|
    );
}

// Test renaming given the location of a module and rename parameters
list[DocumentEdit] getEdits(loc singleModule, int cursorAtOldNameOccurrence, str oldName, str newName, PathConfig pcfg = testPathConfig) {
    void checkNoErrors(list[ModuleMessages] msgs) {
        if (errors := {p | p:program(_, pmsgs) <- msgs, m <- pmsgs, m is error}, errors != {})
            throw errors;
    }

    loc f = resolveLocation(singleModule);

    checkNoErrors(checkAll(f, getRascalCoreCompilerConfig(pcfg)));

    return getEdits(parseModuleWithSpaces(f), cursorAtOldNameOccurrence, oldName, newName, pcfg=pcfg);
}

// Test renaming given a module Tree and rename parameters
list[DocumentEdit] getEdits(start[Module] m, int cursorAtOldNameOccurrence, str oldName, str newName, PathConfig pcfg = testPathConfig) {
    Tree cursor = [n | /Name n := m.top, "<n>" == oldName][cursorAtOldNameOccurrence];
    return renameRascalSymbol(cursor, {src | src <- pcfg.srcs}, pcfg, newName);
}

private tuple[list[DocumentEdit], loc] getEditsAndModule(str stmtsStr, int cursorAtOldNameOccurrence, str oldName, str newName, str decls = "", str imports = "") {
    str moduleName = "TestModule<abs(arbInt())>";
    str moduleStr =
    "module <moduleName>
    '<trim(decls)>
    'void main() {
    '<trim(stmtsStr)>
    '}";

    // Write the file to disk (and clean up later) to easily emulate typical editor behaviour
    loc moduleFileName = |memory://tests/rename/src/<moduleName>.rsc|;
    writeFile(moduleFileName, moduleStr);
    list[DocumentEdit] edits = [];
    try {
        edits = getEdits(moduleFileName, cursorAtOldNameOccurrence, oldName, newName);
    } catch e: {
        remove(moduleFileName);
        throw e;
    }

    return <edits, moduleFileName>;
}


private set[int] extractRenameOccurrences(loc moduleFileName, list[DocumentEdit] edits, str name) {
    start[Module] m = parseModuleWithSpaces(moduleFileName);
    list[loc] oldNameOccurrences = [];
    for (/Name n := m, "<n>" == name) {
        oldNameOccurrences += n.src;
    }

    if ([changed(_, replaces)] := edits) {
        repls = {l | replace(l, _) <- replaces};
        return {i | i <- [0..size(oldNameOccurrences)], oldNameOccurrences[i] in repls};;
    } else {
        throw "Unexpected changes: <edits>";
    }
}

private set[int] extractRenameOccurrences(loc moduleFileName, list[DocumentEdit] edits, str name) {
    start[Module] m = parseModuleWithSpaces(moduleFileName);
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

private str moduleNameToPath(str name) = replaceAll(name, "::", "/");
private str modulePathToName(str path) = replaceAll(path, "/", "::");

private Tree findCursor(loc f, str id, int occ) = [n | m := parseModuleWithSpaces(f), /Name n := m.top, "<n>" == id][occ];

private loc storeTestModule(loc dir, str name, str body) {
    str moduleStr = "
    'module <name>
    '<body>
    ";

    loc moduleFile = dir + "rascal" + (moduleNameToPath(name) + ".rsc");
    writeFile(moduleFile, moduleStr);

    return moduleFile;
}

private set[Tree] occsToTrees(start[Module] m, str name, set[int] occs) = {n | i <- occs, n := [n | /Name n := m.top, "<n>" == name][i]};
private set[loc] occsToLocs(start[Module] m, str name, set[int] occs) = {t.src | t <- occsToTrees(m, name, occs)};
private set[int] locsToOccs(start[Module] m, str name, set[loc] occs) = {indexOf(names, occ) | names := [n.src | /Name n := m.top, "<n>" == name], occ <- occs};

private void checkNoErrors(list[ModuleMessages] msgs) {
    if (errors := {p | p:program(_, pmsgs) <- msgs, m <- pmsgs, m is error}, errors != {})
        throw errors;
}
