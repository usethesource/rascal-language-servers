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
@bootstrapParser
module lang::rascal::tests::rename::TestUtils

import lang::rascal::lsp::refactor::Rename; // Module under test

import IO;
import List;
import Location;
import Set;
import String;

import lang::rascal::\syntax::Rascal; // `Name`

import lang::rascalcore::check::RascalConfig;
import lang::rascalcore::compile::util::Names;

import analysis::diff::edits::TextEdits;

import util::FileSystem;
import util::Math;
import util::Reflective;


//// Fixtures and utility functions
data TestModule = byText(str name, str body, set[int] expectedRenameOccs, str newName = name)
                | byLoc(loc file, set[int] expectedRenameOccs, str newName = name);

bool testRenameOccurrences(set[TestModule] modules, tuple[str moduleName, str id, int occ] cursor, str newName = "bar") {
    str testName = "Test<abs(arbInt())>";
    loc testDir = |memory://tests/rename/<testName>|;

    if(any(m <- modules, m is byLoc)) {
        testDir = cover([m.file | m <- modules, m is byLoc]);
    } else {
        // If none of the modules refers to an existing file, clear the test directory before writing files.
        remove(testDir);
    }

    pcfg = getTestPathConfig(testDir);
    modulesByLocation = {mByLoc | m <- modules, mByLoc := (m is byLoc ? m : byLoc(storeTestModule(testDir, m.name, m.body), m.expectedRenameOccs, newName = m.newName))};
    cursorT = findCursor([m.file | m <- modulesByLocation, getModuleName(m.file, pcfg) == cursor.moduleName][0], cursor.id, cursor.occ);

    edits = renameRascalSymbol(cursorT, toSet(pcfg.srcs), newName, PathConfig(loc _) { return pcfg; });

    renamesPerModule = (
        beforeRename: afterRename
        | renamed(oldLoc, newLoc) <- edits
        , beforeRename := getModuleName(oldLoc, pcfg)
        , afterRename := getModuleName(newLoc, pcfg)
    );

    replacesPerModule = (
        name: occs
        | changed(file, changes) <- edits
        , name := getModuleName(file, pcfg)
        , locs := {l | replace(l, _) <- changes}
        , occs := locsToOccs(parseModuleWithSpaces(file), cursor.id, locs)
    );

    editsPerModule = (
        name : <occs, nameAfterRename>
        | srcDir <- pcfg.srcs
        , file <- find(srcDir, "rsc")
        , name := getModuleName(file, pcfg)
        , occs := replacesPerModule[name] ? {}
        , nameAfterRename := renamesPerModule[name] ? name
    );

    expectedEditsPerModule = (name: <m.expectedRenameOccs, m.newName> | m <- modulesByLocation, name := getModuleName(m.file, pcfg));

    if (editsPerModule != expectedEditsPerModule) {
        print("EXPECTED: ");
        iprintln(expectedEditsPerModule);
        println();

        print("ACTUAL:   ");
        iprintln(editsPerModule);
        println();
        return false;
    }
    return true;
}

set[int] testRenameOccurrences(str stmtsStr, int cursorAtOldNameOccurrence = 0, str oldName = "foo", str newName = "bar", str decls = "", str imports = "") {
    <edits, moduleFileName> = getEditsAndModule(stmtsStr, cursorAtOldNameOccurrence, oldName, newName, decls, imports);
    occs = extractRenameOccurrences(moduleFileName, edits, oldName);
    return occs;
}

// Test renames that are expected to throw an exception
bool testRename(str stmtsStr, int cursorAtOldNameOccurrence = 0, str oldName = "foo", str newName = "bar", str decls = "", str imports = "") {
    edits = getEdits(stmtsStr, cursorAtOldNameOccurrence, oldName, newName, decls, imports);

    print("UNEXPECTED EDITS: ");
    iprintln(edits);

    return false;
}

private PathConfig getTestPathConfig(loc testDir) {
    return pathConfig(
        bin=testDir + "bin",
        libs=[|lib://rascal|],
        srcs=[testDir + "rascal"],
        resources=testDir + "resources",
        generatedSources=testDir + "generated-sources"
    );
}

// private PathConfig getTestWorkspaceConfig() {
//     return pathConfig(
//         bin=|project://rascal-vscode-extension/test-workspace/test-project/target|,
//         libs=[|lib://rascal|],
//         srcs=[|project://rascal-vscode-extension/test-workspace/test-project/src/main/rascal|
//             , |project://rascal-vscode-extension/test-workspace/test-lib/src/main/rascal|],
//         resources=testDir + "resources",
//         generatedSources=testDir + "generated-sources"
//     );
// }

list[DocumentEdit] getEdits(loc singleModule, loc projectDir, int cursorAtOldNameOccurrence, str oldName, str newName, PathConfig pcfg = getTestPathConfig(projectDir)) {
    loc f = resolveLocation(singleModule);
    m = parseModuleWithSpaces(f);

    Tree cursor = [n | /Name n := m.top, "<n>" == oldName][cursorAtOldNameOccurrence];
    return renameRascalSymbol(cursor, toSet(pcfg.srcs), newName, PathConfig(loc _) { return pcfg; });
}

list[DocumentEdit] getEdits(str stmtsStr, int cursorAtOldNameOccurrence, str oldName, str newName, str decls, str imports) {
    <edits, _> = getEditsAndModule(stmtsStr, cursorAtOldNameOccurrence, oldName, newName, decls, imports);
    return edits;
}

private tuple[list[DocumentEdit], loc] getEditsAndModule(str stmtsStr, int cursorAtOldNameOccurrence, str oldName, str newName, str decls, str imports, str moduleName = "TestModule<abs(arbInt())>") {
    str moduleStr =
    "module <moduleName>
    '<trim(imports)>
    '<trim(decls)>
    'void main() {
    '<trim(stmtsStr)>
    '}";

    // Write the file to disk (and clean up later) to easily emulate typical editor behaviour
    loc testDir = |memory://tests/rename/<moduleName>|;
    loc moduleFileName = testDir + "rascal" + "<moduleName>.rsc";
    writeFile(moduleFileName, moduleStr);

    edits = getEdits(moduleFileName, testDir, cursorAtOldNameOccurrence, oldName, newName);
    return <edits, moduleFileName>;
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

private Tree findCursor(loc f, str id, int occ) {
    m = parseModuleWithSpaces(f);
    return [n | /Name n := m.top, "<n>" == id][occ];
}

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

// Workaround to be able to pattern match on the emulated `src` field
data Tree (loc src = |unknown:///|(0,0,<0,0>,<0,0>));
