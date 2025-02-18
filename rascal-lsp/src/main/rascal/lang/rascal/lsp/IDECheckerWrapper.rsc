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
import Set;
import String;
import analysis::graphs::Graph;
import util::FileSystem;
import util::Reflective;

import lang::rascal::\syntax::Rascal;
import lang::rascalcore::check::Checker;
import lang::rascalcore::check::ModuleLocations;

@synopsis{
    This function is a wrapper for the Rascal type checker, taking into account that dependencies
    may be open in the current workspace. It takes an overapproximation of the import/extend graph,
    and calls the type checker on (transitive) dependencies first.

    This function must only be used in an IDE context.
}
list[ModuleMessages] checkFile(loc l, node(loc file) getParseTree, PathConfig(loc file) getPathConfig) {
    checkForImports = [pt | start[Module] pt := getParseTree(l)];
    checkedForImports = {};

    rel[loc, loc] dependencies = {<root, root> | root := inferProjectRoot(l)};
    
    msgs = [];

    while (tree <- checkForImports) {
        currentSrc = tree.src.top;
        currentProject = inferProjectRoot(currentSrc);
        for (i <- tree.top.header.imports) {
            try {
                ml = locateRascalModule("<i.\module>", getPathConfig(currentProject), getPathConfig);
                if (ml.extension == "rsc", start[Module] mlpt := getParseTree(ml), mlpt.src.top notin checkedForImports) {
                    checkForImports += mlpt;
                    dependencies += <currentProject, inferProjectRoot(mlpt.src.top)>;
                }
            } catch msg: {
                ;// Continue
            }
        }
        checkedForImports += currentSrc;
        checkForImports -= tree;
    }
    modulesPerProject = classify(checkedForImports, loc(loc l) {return inferProjectRoot(l);});
    return [*check([*modulesPerProject[project]], makeCompilerConfig(getPathConfig(project))) | project <- reverse(order(dependencies)), project in modulesPerProject];
}

RascalCompilerConfig makeCompilerConfig(PathConfig pcfg) = rascalCompilerConfig(pcfg[resources=pcfg.bin]);

loc locateRascalModule(str fqn, PathConfig pcfg, PathConfig(loc file) getPathConfig) {
    fileName = makeFileName(fqn);
    for (dir <- pcfg.srcs, fileLoc := dir + fileName, exists(fileLoc)) {
        return fileLoc;
    }
    if (lib <- pcfg.libs, lib != |lib://rascal/|, dir <- getPathConfig(inferProjectRoot(lib)).srcs, fileLoc := dir + fileName, exists(fileLoc)) {
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
            return ensureTrailingSpaceInPath(current);
        }
        if (!current.parent?) {
            return isDirectory(member) ? member : member.parent;
        }
        current = current.parent;
    }
    
    return ensureTrailingSpaceInPath(current);
}

loc ensureTrailingSpaceInPath(loc l) = endsWith(l.path, "/") ? l : l[path=l.path + "/"];

