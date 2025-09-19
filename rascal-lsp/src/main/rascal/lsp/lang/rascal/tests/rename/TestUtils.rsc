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
import util::LanguageServer;
import util::Math;
import util::Maybe;
import util::Monitor;
import util::Reflective;
import util::Util;


//// Fixtures and utility functions
data TestModule = byText(str name, str body, set[int] nameOccs, str newName = name, set[int] skipCursors = {})
                | byLoc(str name, loc file, set[int] nameOccs, str newName = name, set[int] skipCursors = {});

data RenameException
    = illegalRename(set[Message] reasons)
    ;

private list[DocumentEdit] sortEdits(list[DocumentEdit] edits) = [sortChanges(e) | e <- edits];

private DocumentEdit sortChanges(changed(loc l, list[TextEdit] edits)) = changed(l, sort(edits, bool(TextEdit e1, TextEdit e2) {
    return e1.range.offset < e2.range.offset;
}));
private default DocumentEdit sortChanges(DocumentEdit e) = e;

private list[DocumentEdit] groupEditsByFile(list[DocumentEdit] _: [*pre, changed(f, e1), *mid, changed(f, e2), *post]) =
    groupEditsByFile([*pre, changed(f, [*e1, *e2]), *mid, *post]);
private default list[DocumentEdit] groupEditsByFile(list[DocumentEdit] edits) = edits;

private void verifyTypeCorrectRenaming(loc root, list[DocumentEdit] edits, PathConfig pcfg) {
    list[loc] editLocs = [l | /replace(l, _) := edits];
    assert size(editLocs) == size(toSet(editLocs)) : "Duplicate locations in suggested edits - VS Code cannot handle this";

    RascalCompilerConfig ccfg = rascalCompilerConfig(pcfg)[verbose = false][logPathConfig = false];
    list[ModuleMessages] checkBefore = checkAll(root, ccfg);

    // Back-up sources
    loc backupLoc = |memory:///tests/backup|;
    remove(backupLoc, recursive = true);
    copy(root, backupLoc, recursive = true);

    executeDocumentEdits(sortEdits(groupEditsByFile(edits)));
    remove(pcfg.bin, recursive = true);

    list[ModuleMessages] checkAfter = checkAll(root, ccfg);
    newMsgs = checkAfter - checkBefore;

    if (newErrors: [_, *_] := [e | program(_, msgs) <- newMsgs, e <- msgs, e is error]) {
        throw newErrors;
    }

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

bool testProject(set[TestModule] modules, str testName, bool(set[TestModule] mods, loc testDir, PathConfig pcfg) doCheck) {
    loc testDir = |unknown:///|;
    bool moduleExistsOnDisk = any(mmm <- modules, mmm is byLoc);
    if (moduleExistsOnDisk){
        testDir = cover([m.file.parent | m <- modules, m is byLoc]).parent;
    } else {
        // If none of the modules refers to an existing file, clear the test directory before writing files.
        testDir = |memory:///tests/rename/<testName>|;
        remove(testDir);
    }

    pcfg = getTestPathConfig(testDir);
    modulesByLocation = {mByLoc | m <- modules, mByLoc := (m is byLoc ? m : byLoc(m.name, storeTestModule(testDir, m.name, m.body), m.nameOccs, newName = m.newName, skipCursors = m.skipCursors))};

    for (m <- modulesByLocation) {
        try {
            parse(#start[Module], m.file);
        } catch ParseError(l): {
            throw "Parse error in test module <m.file>: <l>";
        }
    }

    // Do the actual work here
    bool result = doCheck(modulesByLocation, testDir, pcfg);

    if (!moduleExistsOnDisk) {
        remove(testDir);
    }

    return result;
}

bool testRenameOccurrences(set[TestModule] modules, str oldName = "foo", str newName = "bar") {
    bool success = true;
    for (mm <- modules, cursorOcc <- (mm.nameOccs - mm.skipCursors)) {
        success = success && testProject(modules, "Test_<mm.name>_<cursorOcc>", bool(set[TestModule] modulesByLocation, loc testDir, PathConfig pcfg) {
            <cursor, focus> = findCursor([m.file | m <- modulesByLocation, m.name == mm.name][0], oldName, cursorOcc);

            println("Renaming \'<oldName>\' from <focus[0].src>");
            <edits, msgs> = rascalRenameSymbol(cursor, focus, newName, toSet(pcfg.srcs), PathConfig(loc _) { return pcfg; });

            throwMessagesIfError(msgs);

            renamesPerModule = (
                beforeRename: afterRename
                | renamed(oldLoc, newLoc) <- edits
                , beforeRename := safeRelativeModuleName(oldLoc, pcfg)
                , afterRename := safeRelativeModuleName(newLoc, pcfg)
            );

            replacesPerModule = toMap({
                <name, occ>
                | changed(file, changes) <- edits
                , name := safeRelativeModuleName(file, pcfg)
                , locs := {c.range | c <- changes}
                , occ <- locsToOccs(parseModuleWithSpaces(file), oldName, locs)
            });

            editsPerModule = (
                name : <occs, nameAfterRename>
                | srcDir <- pcfg.srcs
                , file <- find(srcDir, "rsc")
                , name := safeRelativeModuleName(file, pcfg)
                , occs := replacesPerModule[name] ? {}
                , nameAfterRename := renamesPerModule[name] ? name
            );

            expectedEditsPerModule = (name: <m.nameOccs, m.newName> | m <- modulesByLocation, name := safeRelativeModuleName(m.file, pcfg));

            if (expectEq(expectedEditsPerModule, editsPerModule, epilogue = "Rename from cursor <focus[0].src> failed:")) {
                verifyTypeCorrectRenaming(testDir, edits, pcfg);
                return true;
            }

            return false;
        });
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

public loc calculateRascalLib() {
    result = resolveLocation(|std:///|);
    if (/org.rascalmpl.library.?$/ := result.path) {
        return result.parent.parent.parent;
    }
    return result;
}


public PathConfig getTestPathConfig(loc testDir) {
    return pathConfig(
        bin=testDir + "bin",
        libs=[calculateRascalLib()],
        srcs=[testDir + "rascal"]
    );
}

PathConfig getRascalCorePathConfig(loc rascalCoreProject) {
   return pathConfig(
        srcs = [rascalCoreProject + "src/org/rascalmpl/core/library"],
        bin = rascalCoreProject + "target/test-classes",
        libs = [|mvn://org.rascalmpl--typepal--0.15.1/|, calculateRascalLib()]
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

void throwMessagesIfError(set[Message] msgs) {
    for (msg <- msgs, msg is error) {
        throw illegalRename(msgs);
    }
}

Edits getEdits(loc singleModule, set[loc] projectDirs, int cursorAtOldNameOccurrence, str oldName, str newName, PathConfig(loc) getPathConfig) {
    j = "Testing renaming <cursorAtOldNameOccurrence>th occurrence of \'<oldName>\' in <singleModule>";
    jobStart(j, totalWork = 3);
    jobStep(j, "Finding cursor focus tree");
    <cursor, focus> = findCursor(singleModule, oldName, cursorAtOldNameOccurrence);
    jobStep(j, "Performing renaming");
    res =  rascalRenameSymbol(cursor, focus, newName, projectDirs, getPathConfig);
    jobEnd(j);
    return res;
}

tuple[Edits, set[int]] getEditsAndOccurrences(loc singleModule, loc projectDir, int cursorAtOldNameOccurrence, str oldName, str newName, PathConfig pcfg = getTestPathConfig(projectDir)) {
    edits = getEdits(singleModule, {projectDir}, cursorAtOldNameOccurrence, oldName, newName, PathConfig(loc _) { return pcfg; });
    throwMessagesIfError(edits<1>);

    for (src <- pcfg.srcs) {
        verifyTypeCorrectRenaming(src, edits<0>, pcfg);
    }

    occs = extractRenameOccurrences(singleModule, edits<0>, oldName);

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
    loc testDir = |memory:///tests/rename/<moduleName>|;
    remove(testDir);
    loc moduleFileName = testDir + "rascal" + "<moduleName>.rsc";
    writeFile(moduleFileName, moduleStr);

    <edits, occs> = getEditsAndOccurrences(moduleFileName, testDir, cursorAtOldNameOccurrence, oldName, newName);
    return <edits, occs>;
}

private set[str] reservedNames = getRascalReservedIdentifiers();
str forceUnescapeNames(str name) = replaceAll(name, "\\", "");
str escapeReservedNames(str name, str sep = "::") = intercalate(sep, [n in reservedNames ? "\\<n>" : n | n <- split(sep, name)]);
str normalizeEscaping(str name) = escapeReservedNames(forceUnescapeNames(name));

private lrel[int, loc, Maybe[Tree]] collectNameTrees(start[Module] m, str name) {
    lrel[loc, Maybe[Tree]] names = [];
    sname = normalizeEscaping(name);
    top-down-break visit (m) {
        case QualifiedName qn: {
            if (normalizeEscaping("<qn>") == sname) {
                names += <qn.src, just(qn)>;
            }
            else {
                modPrefix = prefix([n | n <- qn.names]);
                if ([_, *_] := modPrefix && normalizeEscaping(intercalate("::", ["<n>" | n <- modPrefix])) == sname) {
                    names += <cover([n.src | n <- modPrefix]), nothing()>;
                } else {
                    fail;
                }
            }
        }
        // 'Normal' names
        case Name n:
            if (normalizeEscaping("<n>") == sname) names += <n.src, just(n)>;
        // Nonterminals (grammars)
        case Nonterminal s:
            if (normalizeEscaping("<s>") == sname) names += <s.src, just(s)>;
        // Labels for nonterminals (grammars)
        case NonterminalLabel label:
            if (normalizeEscaping("<label>") == sname) names += <label.src, just(label)>;
    }

    return [<i, l, mt> | <i, <l, mt>> <- zip2(index(names), names)];
}

private set[int] extractRenameOccurrences(loc moduleFileName, list[DocumentEdit] edits, str name) {
    start[Module] m = parseModuleWithSpaces(moduleFileName);
    list[loc] oldNameOccurrences = [l | <_, l, _> <- collectNameTrees(m, name)];

    if ([changed(_, replaces)] := edits) {
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
            throw "Test produced some invalid (i.e. not pointing to `oldName`) locations: <intercalate("\n- ", ["<readFile(l)> at <l>" | loc l <- nonOldNameLocs])>";
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

private tuple[loc, list[Tree]] findCursor(loc f, str id, int occ) {
    m = parseModuleWithSpaces(f);
    names = collectNameTrees(m, id);
    if (occ >= size(names) || occ < 0) throw "Found <size(names)> occurrences of \'<id>\' in <f>; cannot use occurrence at position <occ> as cursor";
    loc cl = (names<1>)[occ];
    return <cl, computeFocusList(m, cl.begin.line, cl.begin.column + 1)>;
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
