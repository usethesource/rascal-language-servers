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
module lang::rascal::lsp::IDECheckerWrapper

import IO;
import List;
import Relation;
import Set;
import String;
import ValueIO;
import Location;
import analysis::graphs::Graph;
import util::FileSystem;
import util::Monitor;
import util::ParseErrorRecovery;

import lang::rascal::\syntax::Rascal;
import lang::rascalcore::check::Checker;
import lang::rascalcore::check::ModuleLocations;

@synopsis{
    This function is a wrapper for the Rascal type checker, taking into account that dependencies
    may be open in the current workspace. It takes an overapproximation of the import/extend graph,
    and calls the type checker on (transitive) dependencies first.

    This function must only be used in an IDE context, as this is the only situation in which non-lib
    source locations can occur in the `libs` entry of the PathConfig of a project. Note that for `lib`
    locations, the type checker uses `tpl` files that are packaged with libraries.
}
map[loc, set[Message]] checkFile(loc l, set[loc] workspaceFolders, start[Module](loc file) getParseTree, PathConfig(loc file) getPathConfig)
    = job("Rascal check", map[loc, set[Message]](void(str, int) step) {

    tuple[start[Module], set[Message]] getParseTreeOrErrors(loc l, str name, loc errorLocation) {
        try {
            t = getParseTree(l);
            errors = hasParseErrors(t)
                ? {error("Cannot typecheck this module, since dependency `<name>` has parse error(s).", errorLocation,
                        causes=[error("Parse error around this position.", e.src) | e <- findBestParseErrors(t)])}
                : {};
            return <t, errors>;
        } catch ParseError(loc err): {
            return <(start[Module]) `module ModuleHadParseError`, {error("Cannot typecheck this module, since dependency `<name>` has parse error(s).", errorLocation, causes=[error("Parse error(s).", err)])}>;
        }
    }

    // Note: check further down parses again, possibly leading to a different tree if the contents changed in the meantime.
    // We cannot fix that here, unless we pass `getParseTree` to `check`.
    <openFile, parseErrors> = getParseTreeOrErrors(l, "unknown", l);
    if ({} != parseErrors) {
        // No need to return the errors, since the language server will take care of parse errors in open modules
        return ();
    }

    openFileHeader = openFile.top.header.name;
    checkForImports = [openFile];
    checkedForImports = {};
    initialProject = inferProjectRoot(l);

    rel[loc, loc] dependencies = {};

    step("Dependency graph", 1);
    job("Building dependency graph", bool (void (str, int) step2) {
        while (tree <- checkForImports) {
            step2("Calculating imports for <tree.top.header.name>", 1);
            currentSrc = tree.src.top;
            currentProject = inferProjectRoot(currentSrc);
            if (currentProject in workspaceFolders && currentProject.file notin {"rascal", "rascal-lsp"}) {
                for (i <- tree.top.header.imports, i has \module) {
                    modName = "<i.\module>";
                    for (ml <- locateRascalModules(modName, getPathConfig(currentProject), getPathConfig, workspaceFolders)) {
                        if (<mlpt, importErrors> := getParseTreeOrErrors(ml, modName, openFileHeader.src)) {
                            if ({} !:= importErrors) {
                                parseErrors += importErrors;
                                checkedForImports += currentSrc; // do not check this module again
                                continue; // since there is an error in this module, we do not recurse into its imports
                            }
                            if (mlpt.src.top notin checkedForImports) {
                                checkForImports += mlpt;
                                jobTodo("Building dependency graph");
                                dependencies += <currentProject, inferProjectRoot(mlpt.src.top)>;
                            }
                        }
                    }
                }
            }
            checkedForImports += currentSrc;
            checkForImports -= tree;
        }
        return true;
    }, totalWork=1);

    if ({} != parseErrors) {
        // Since we only reported errors on `l`, there is not need to analyze to which files the errors belong here.
        return (l: parseErrors);
    }

    cyclicDependencies = {p | <p, p> <- (dependencies - ident(carrier(dependencies)))+};
    if (cyclicDependencies != {}) {
        return (l : {error("Cyclic dependencies detected between projects {<intercalate(", ", [*cyclicDependencies])>}. This is not supported. Fix your project setup.", l)});
    }
    modulesPerProject = classify(checkedForImports, loc(loc l) {return inferProjectRoot(l);});
    msgs = [];

    upstreamDependencies = {project | project <- reverse(order(dependencies)), project in modulesPerProject, project != initialProject};

    step("Checking upstream dependencies ", 1);
    job("Checking upstream dependencies", bool (void (str, int) step3) {
        for (project <- upstreamDependencies) {
            step3("Checked module in `<project.file>`", 1);
            pcfg = getPathConfig(project);
            checkOutdatedPathConfig(pcfg);
            modulesToCheck = calculateOutdated(modulesPerProject[project], pcfg);
            if (modulesToCheck != []) {
                msgs += check(modulesToCheck, rascalCompilerConfig(pcfg));
            }
        }
        return true;
    }, totalWork=size(upstreamDependencies));

    step("Checking module <l>", 1);
    pcfg = getPathConfig(initialProject);
    checkOutdatedPathConfig(pcfg);
    msgs += check(calculateOutdated(modulesPerProject[initialProject], pcfg) + [l], rascalCompilerConfig(pcfg));
    return filterAndFix(msgs, workspaceFolders);
}, totalWork=3);

private bool inWorkspace(set[loc] workspaceFolders, loc lib) {
    try {
        relativize([*workspaceFolders], lib);
        return true;
    } catch PathNotFound(_):  {
        return false;
    }
}

private list[loc] calculateOutdated(set[loc] modules, PathConfig pcfg) = [ m | m <- modules, tplExpired(m, pcfg)];

private LanguageFileConfig rascalLFC = fileConfig();

private bool tplExpired(loc m, PathConfig pcfg) {
    tpl = binFile(srcsModule(m, pcfg, rascalLFC), pcfg, rascalLFC);
    return !exists(tpl) || lastModified(m) >= lastModified(tpl);
}

loc pathConfigFile(PathConfig pcfg) = pcfg.bin + "rascal.pathconfig";

void checkOutdatedPathConfig(PathConfig pcfg) {
    pcfgFile = pathConfigFile(pcfg);
    try {
        if (!exists(pcfgFile) || tplInputsChanged(pcfg, readBinaryValueFile(#PathConfig, pcfgFile))) {
            // We do not know the previous path config, or it changed
            // Be safe and remove TPLs
            for (loc f <- find(pcfg.bin, "tpl")) {
                try {
                    remove(f);
                } catch IO(_): {
                    jobWarning("Cannot remove TPL", f);
                }
            }
            writeBinaryValueFile(pcfgFile, pcfg);
        }
    } catch IO(str msg): {
        jobWarning(msg, pcfg.bin);
    }
}

bool tplInputsChanged(PathConfig old, PathConfig new) = old[messages=[]] != new[messages=[]];

set[loc] locateRascalModules(str fqn, PathConfig pcfg, PathConfig(loc file) getPathConfig, set[loc] workspaceFolders) {
    fileName = makeFileName(fqn);
    // Check the source directories
    return {fileLoc | dir <- pcfg.srcs, fileLoc := dir + fileName, exists(fileLoc)}
    // And libraries available in the current workspace
         + {fileLoc | lib <- pcfg.libs, inWorkspace(workspaceFolders, lib), dir <- getPathConfig(inferProjectRoot(lib)).srcs, fileLoc := dir + fileName, exists(fileLoc)};
}

loc targetToProject(loc l) {
    if (l.scheme == "target") {
        return l[scheme="project"];
    }
    return l;
}

@memo
@synopsis{Infers the root of the project that `member` is in.}
loc inferProjectRoot(loc member) {
    parentRoot = member;
    root = parentRoot;

    do {
        root = parentRoot;
        parentRoot = inferLongestProjectRoot(root.parent);
    } while (root.parent? && parentRoot != root.parent);
    return root;
}

@synopsis{Infers the longest project root-like path that `member` is in.}
@pitfalls{Might return a sub-directory of `target/`.}
loc inferLongestProjectRoot(loc member) {
    current = targetToProject(member);
    if (!isDirectory(current)) {
        current = current.parent;
    }

    while (exists(current), isDirectory(current)) {
        if (exists(current + "META-INF" + "RASCAL.MF")) {
            return current;
        }
        if (!current.parent?) {
            return isDirectory(member) ? member : member.parent;
        }
        current = current.parent;
    }

    return current;
}

map[loc, set[Message]] filterAndFix(list[ModuleMessages] messages, set[loc] workspaceFolders) {
    set[Message] empty = {};
    map[loc, set[Message]] result = ( f.top : empty | program(f,_) <- messages);
    for (program(_, ms) <- messages, m <- ms, inWorkspace(workspaceFolders, m.at.top)) {
        result[m.at.top]?empty += {m};
    }
    return result;
}
