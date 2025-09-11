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
module lang::rascal::lsp::IDECheckerWrapper

import IO;
import List;
import Relation;
import Set;
import String;
import Location;
import analysis::graphs::Graph;
import util::FileSystem;
import util::Monitor;
import util::Reflective;

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
set[Message] checkFile(loc l, set[loc] workspaceFolders, start[Module](loc file) getParseTree, PathConfig(loc file) getPathConfig)
    = job("Rascal check", set[Message](void(str, int) step) {
    checkForImports = [getParseTree(l)];
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
                    try {
                        ml = locateRascalModule("<i.\module>", getPathConfig(currentProject), getPathConfig, workspaceFolders);
                        if (ml.extension == "rsc", mlpt := getParseTree(ml), mlpt.src.top notin checkedForImports) {
                            checkForImports += mlpt;
                            jobTodo("Building dependency graph");
                            dependencies += <currentProject, inferProjectRoot(mlpt.src.top)>;
                        }
                    } catch _: {
                        ;// Continue
                    }
                }
            }
            checkedForImports += currentSrc;
            checkForImports -= tree;
        }
        return true;
    }, totalWork=1);

    cyclicDependencies = {p | <p, p> <- (dependencies - ident(carrier(dependencies)))+};
    if (cyclicDependencies != {}) {
        return {program(l, {error("Cyclic dependencies detected between projects {<intercalate(", ", [*cyclicDependencies])>}. This is not supported. Fix your project setup.", l)})};
    }
    modulesPerProject = classify(checkedForImports, loc(loc l) {return inferProjectRoot(l);});
    msgs = [];

    upstreamDependencies = {project | project <- reverse(order(dependencies)), project in modulesPerProject, project != initialProject};

    step("Checking upstream dependencies ", 1);
    job("Checking upstream dependencies", bool (void (str, int) step3) {
        for (project <- upstreamDependencies) {
            step3("Checked module in `<project.file>`", 1);
            msgs += check([*modulesPerProject[project]], rascalCompilerConfig(getPathConfig(project)));
        }
        return true;
    }, totalWork=size(upstreamDependencies));

    step("Checking module <l>", 1);
    msgs += check([l], rascalCompilerConfig(getPathConfig(initialProject)));
    return { m | program(_, messages) <- msgs, m <- messages, inWorkspace(workspaceFolders, m.at)};
}, totalWork=3);

private bool inWorkspace(set[loc] workspaceFolders, loc lib) {
    try {
        relativize([*workspaceFolders], lib);
        return true;
    } catch PathNotFound(_):  {
        return false;
    }
}

loc locateRascalModule(str fqn, PathConfig pcfg, PathConfig(loc file) getPathConfig, set[loc] workspaceFolders) {
    fileName = makeFileName(fqn);
    // Check the source directories
    for (dir <- pcfg.srcs, fileLoc := dir + fileName, exists(fileLoc)) {
        return fileLoc;
    }

    // And libraries available in the current workspace
    if (lib <- pcfg.libs, inWorkspace(workspaceFolders, lib), dir <- getPathConfig(inferProjectRoot(lib)).srcs, fileLoc := dir + fileName, exists(fileLoc)) {
        return fileLoc;
    }

    throw "Module `<fqn>` not found!";
}

loc targetToProject(loc l) {
    if (l.scheme == "target") {
        return l[scheme="project"];
    }
    return l;
}

@memo
loc inferProjectRoot(loc member) {
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
