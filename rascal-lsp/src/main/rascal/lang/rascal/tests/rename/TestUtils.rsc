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
module lang::rascal::tests::rename::TestUtils

import lang::rascal::lsp::refactor::Rename; // Module under test

import util::Util;
import util::refactor::TextEdits;

import IO;
import List;
import Location;
import ParseTree;
import Set;
import String;

import lang::rascal::\syntax::Rascal; // `Name`

import lang::rascalcore::check::Checker;
import lang::rascalcore::check::BasicRascalConfig;
import lang::rascalcore::check::RascalConfig;
import lang::rascalcore::compile::util::Names;

import analysis::diff::edits::ExecuteTextEdits;

import util::FileSystem;
import util::Math;
import util::Maybe;
import util::Reflective;


//// Fixtures and utility functions
data TestModule = byText(str name, str body, set[int] nameOccs, str newName = name, set[int] skipCursors = {})
                | byLoc(str name, loc file, set[int] nameOccs, str newName = name, set[int] skipCursors = {});

private list[DocumentEdit] sortEdits(list[DocumentEdit] edits) = [sortChanges(e) | e <- edits];

private DocumentEdit sortChanges(changed(loc l, list[TextEdit] edits)) = changed(l, sort(edits, bool(TextEdit e1, TextEdit e2) {
    return e1.range.offset < e2.range.offset;
}));
private default DocumentEdit sortChanges(DocumentEdit e) = e;

private void verifyTypeCorrectRenaming(loc root, Edits edits, PathConfig pcfg) {
    list[loc] editLocs = [l | /replace(l, _) := edits<0>];
    assert size(editLocs) == size(toSet(editLocs)) : "Duplicate locations in suggested edits - VS Code cannot handle this";

    // Back-up sources
    loc backupLoc = |memory://tests/backup|;
    remove(backupLoc, recursive = true);
    copy(root, backupLoc, recursive = true);

    executeDocumentEdits(sortEdits(edits<0>));
    remove(pcfg.resources);
    RascalCompilerConfig ccfg = rascalCompilerConfig(pcfg)[verbose = false][logPathConfig = false];
    throwAnyErrors(checkAll(root, ccfg));

    // Restore back-up
    remove(root, recursive = true);
    move(backupLoc, root, overwrite = true);
}

bool expectEq(&T expected, &T actual, str epilogue = "") {
    if (expected != actual) {
        if (epilogue != "") println(epilogue);

        print("EXPECTED: ");
        iprintln(expected);
        println();

        print("ACTUAL:   ");
        iprintln(actual);
        println();
        return false;
    }
    return true;
}

bool testRenameOccurrences(set[TestModule] modules, str oldName = "foo", str newName = "bar") {
    bool success = true;

    bool moduleExistsOnDisk = any(mmm <- modules, mmm is byLoc);
    for (mm <- modules, cursorOcc <- (mm.nameOccs - mm.skipCursors)) {
        loc testDir = |unknown:///|;
        if (moduleExistsOnDisk){
            testDir = cover([m.file.parent | m <- modules, m is byLoc]).parent;
        } else {
            // If none of the modules refers to an existing file, clear the test directory before writing files.
            str testName = "Test_<mm.name>_<cursorOcc>";
            testDir = |memory://tests/rename/<testName>|;
            remove(testDir);
        }

        pcfg = getTestPathConfig(testDir);
        modulesByLocation = {mByLoc | m <- modules, mByLoc := (m is byLoc ? m : byLoc(m.name, storeTestModule(testDir, m.name, m.body), m.nameOccs, newName = m.newName, skipCursors = m.skipCursors))};

        for (m <- modulesByLocation) {
            try {
                parseModuleWithSpaces(m.file);
            } catch _: {
                throw "Parse error in test module <ml>";
            }
        }

        cursorT = findCursor([m.file | m <- modulesByLocation, m.name == mm.name][0], oldName, cursorOcc);

        println("Renaming \'<oldName>\' from <cursorT.src>");
        edits = rascalRenameSymbol(cursorT, newName, toSet(pcfg.srcs), PathConfig(loc _) { return pcfg; });

        renamesPerModule = (
            beforeRename: afterRename
            | renamed(oldLoc, newLoc) <- edits<0>
            , beforeRename := getModuleName(oldLoc, pcfg)
            , afterRename := getModuleName(newLoc, pcfg)
        );

        replacesPerModule = (
            name: occs
            | changed(file, changes) <- edits<0>
            , name := getModuleName(file, pcfg)
            , locs := {c.range | c <- changes}
            , occs := locsToOccs(parseModuleWithSpaces(file), oldName, locs)
        );

        editsPerModule = (
            name : <occs, nameAfterRename>
            | srcDir <- pcfg.srcs
            , file <- find(srcDir, "rsc")
            , name := getModuleName(file, pcfg)
            , occs := replacesPerModule[name] ? {}
            , nameAfterRename := renamesPerModule[name] ? name
        );

        expectedEditsPerModule = (name: <m.nameOccs, m.newName> | m <- modulesByLocation, name := getModuleName(m.file, pcfg));

        if (!expectEq(expectedEditsPerModule, editsPerModule, epilogue = "Rename from cursor <cursorT.src> failed:")) {
            success = false;
        }

        if (success) {
            verifyTypeCorrectRenaming(testDir, edits, pcfg);
        }

        if (!moduleExistsOnDisk) {
            remove(testDir);
        }
    }

    return success;
}

bool testRenameOccurrences(set[int] oldNameOccurrences, str stmtsStr, str oldName = "foo", str newName = "bar", str decls = "", str imports = "", set[int] skipCursors = {}) {
    bool success = true;
    map[int, set[int]] results = ();
    for (cursor <- oldNameOccurrences - skipCursors) {
        <_, renamedOccs> = getEditsAndModule(stmtsStr, cursor, oldName, newName, decls, imports);
        results[cursor] = renamedOccs;
        if (renamedOccs != oldNameOccurrences) success = false;
    }

    if (!success) {
        println("Test returned unexpected renames for some possible cursors (expected: <oldNameOccurrences>):");
        iprintln(results);
    }

    return success;
}

// Test renames that are expected to throw an exception
bool testRename(str stmtsStr, int cursorAtOldNameOccurrence = 0, str oldName = "foo", str newName = "bar", str decls = "", str imports = "") {
    edits = getEdits(stmtsStr, cursorAtOldNameOccurrence, oldName, newName, decls, imports);

    print("UNEXPECTED EDITS: ");
    iprintln(edits);

    return false;
}

public PathConfig getTestPathConfig(loc testDir) {
    return pathConfig(
        bin=testDir + "bin",
        libs=[|lib://rascal|],
        srcs=[testDir + "rascal"],
        resources=testDir + "bin",
        generatedSources=testDir + "generated-sources"
    );
}

PathConfig getRascalCorePathConfig(loc rascalCoreProject) {
   return pathConfig(
        srcs = [rascalCoreProject + "src/org/rascalmpl/core/library"],
        bin = rascalCoreProject + "target/test-classes",
        generatedSources = rascalCoreProject + "target/generated-test-sources",
        resources = rascalCoreProject + "target/generated-test-resources",
        libs = [|lib://typepal|, |lib://rascal|]
    );
}

PathConfig resolveLocations(PathConfig pcfg) {
    return visit(pcfg) {
        case loc l => resolveLocation(l)
    };
}

PathConfig getPathConfig(loc project) {
    println("Getting path config for <project.file> (<project>)");

    if (project.file == "rascal-core") {
        pcfg = getRascalCorePathConfig(|home:///swat/projects/Rascal/rascal-core|);
        return pcfg;
    }

    pcfg = getProjectPathConfig(project);
    return pcfg;
}

Edits getEdits(loc singleModule, set[loc] projectDirs, int cursorAtOldNameOccurrence, str oldName, str newName, PathConfig(loc) getPathConfig) {
    Tree cursor = findCursor(singleModule, oldName, cursorAtOldNameOccurrence);
    return rascalRenameSymbol(cursor, newName, projectDirs, getPathConfig);
}

tuple[Edits, set[int]] getEditsAndOccurrences(loc singleModule, loc projectDir, int cursorAtOldNameOccurrence, str oldName, str newName, PathConfig pcfg = getTestPathConfig(projectDir)) {
    edits = getEdits(singleModule, {projectDir}, cursorAtOldNameOccurrence, oldName, newName, PathConfig(loc _) { return pcfg; });
    occs = extractRenameOccurrences(singleModule, edits, oldName);

    for (src <- pcfg.srcs) {
        verifyTypeCorrectRenaming(src, edits, pcfg);
    }

    return <edits, occs>;
}

Edits getEdits(str stmtsStr, int cursorAtOldNameOccurrence, str oldName, str newName, str decls, str imports) {
    <edits, _> = getEditsAndModule(stmtsStr, cursorAtOldNameOccurrence, oldName, newName, decls, imports);
    return edits;
}

private tuple[Edits, set[int]] getEditsAndModule(str stmtsStr, int cursorAtOldNameOccurrence, str oldName, str newName, str decls, str imports, str moduleName = "TestModule") {
    str moduleStr =
    "module <moduleName>
    '<trim(imports)>
    '<trim(decls)>
    'void main() {
    '<trim(stmtsStr)>
    '}";

    // Write the file to disk (and clean up later) to easily emulate typical editor behaviour
    loc testDir = |memory://tests/rename/<moduleName>|;
    remove(testDir);
    loc moduleFileName = testDir + "rascal" + "<moduleName>.rsc";
    writeFile(moduleFileName, moduleStr);

    <edits, occs> = getEditsAndOccurrences(moduleFileName, testDir, cursorAtOldNameOccurrence, oldName, newName);
    return <edits, occs>;
}

private lrel[int, loc, Maybe[Tree]] collectNameTrees(start[Module] m, str name) {
    lrel[loc, Maybe[Tree]] names = [];
        top-down-break visit (m) {
        case QualifiedName qn: {
            if ("<qn>" == name) {
                names += <qn.src, just(qn)>;
            }
            else {
                modPrefix = prefix([n | n <- qn.names]);
                if (intercalate("::", ["<n>" | n <- modPrefix]) == name) {
                    names += <cover([n.src | n <- modPrefix]), nothing()>;
                } else {
                    fail;
                }
            }
        }
        // 'Normal' names
        case Name n:
            if ("<n>" == name) names += <n.src, just(n)>;
        // Nonterminals (grammars)
        case Nonterminal s:
            if ("<s>" == name) names += <s.src, just(s)>;
        // Labels for nonterminals (grammars)
        case NonterminalLabel label:
            if ("<label>" == name) names += <label.src, just(label)>;
    }

    return [<i, l, mt> | <i, <l, mt>> <- zip2(index(names), names)];
}

private set[int] extractRenameOccurrences(loc moduleFileName, Edits edits, str name) {
    start[Module] m = parseModuleWithSpaces(moduleFileName);
    list[loc] oldNameOccurrences = [l | <_, l, _> <- collectNameTrees(m, name)];

    if ([changed(_, replaces)] := edits<0>) {
        set[int] occs = {};
        set[loc] nonOldNameLocs = {};
        for (r <- replaces) {
            if (idx := indexOf(oldNameOccurrences, r.range), idx >= 0) {
                occs += idx;
            } else {
                nonOldNameLocs += r.range;
            }
        }

        if (nonOldNameLocs != {}) {
            throw "Test produced some invalid (i.e. not pointing to `oldName`) locations: <nonOldNameLocs>";
        }

        return occs;
    } else {
        print("Unexpected changes: ");
        iprintln(edits);
        throw "Unexpected changes: <edits>";
    }
}

private str moduleNameToPath(str name) = replaceAll(name, "::", "/");
private str modulePathToName(str path) = replaceAll(path, "/", "::");

private Tree findCursor(loc f, str id, int occ) {
    m = parseModuleWithSpaces(f);
    names = collectNameTrees(m, id);
    if (occ >= size(names) || occ < 0) throw "Found <size(names)> occurrences of \'<id>\'; cannot use occurrence at position <occ> as cursor";
    maybeCursor = names[occ];

    if (<i, l, nothing()> := maybeCursor) {
        throw "Cannot use <i>th occurrence of \'<id>\' at <l> as cursor";
    } else {
        return (maybeCursor<2>).val;
    }
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

private set[int] locsToOccs(start[Module] m, str name, set[loc] occs) =
    toSet((collectNameTrees(m, name)<1, 0>)[occs]);
