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
module lang::rascal::lsp::refactor::rename::Modules

import lang::rascal::lsp::refactor::TextEdits;
import lang::rascal::lsp::refactor::Util;

import lang::rascal::\syntax::Rascal;

import IO;
import List;
import Location;
import ParseTree;
import Set;
import String;

import util::FileSystem;
import util::Reflective;

private tuple[str, loc] fullQualifiedName(QualifiedName qn) = <"<qn>", qn.src>;
private tuple[str, loc] qualifiedPrefix(QualifiedName qn) {
    list[Name] names = [n | n <- qn.names];
    if (size(names) <= 1) return <"", |unknown:///|>;

    str fullName = "<qn>";
    str namePrefix = substring(fullName, 0, findLast(fullName, "::"));
    loc prefixLoc = cover([n.src | Name n <- prefix(names)]);

    return <namePrefix, prefixLoc>;
}

private bool isReachable(PathConfig toProject, PathConfig fromProject) =
    toProject == fromProject           // Both configs belong to the same project
 || toProject.bin in fromProject.libs; // The using project can import the declaring project

list[TextEdit] getChanges(loc f, PathConfig wsProject, rel[str oldName, str newName, PathConfig pcfg] qualifiedNameChanges) {
    list[TextEdit] changes = [];

    start[Module] m = parseModuleWithSpacesCached(f);
    for (/QualifiedName qn := m) {
        for (<oldName, l> <- {fullQualifiedName(qn), qualifiedPrefix(qn)}
           , {<newName, projWithRenamedMod>} := qualifiedNameChanges[oldName]
           , isReachable(projWithRenamedMod, wsProject)
           ) {
            changes += replace(l, newName);
        }
    }

    return changes;
}

Edits propagateModuleRenames(rel[str oldName, str newName, PathConfig pcfg] qualifiedNameChanges, set[loc] workspaceFolders, PathConfig(loc) getPathConfig) {
    set[PathConfig] projectWithRenamedModule = qualifiedNameChanges.pcfg;
    set[DocumentEdit] edits = flatMap(workspaceFolders, set[DocumentEdit](loc wsFolder) {
        PathConfig wsFolderPcfg = getPathConfig(wsFolder);

        // If this workspace cannot reach any of the renamed modules, no need to continue looking for references to renamed modules here at all
        if (!any(PathConfig changedProj <- projectWithRenamedModule, isReachable(changedProj, wsFolderPcfg))) return {};

        return {changed(file, changes)
            | loc file <- find(wsFolder, "rsc")
            , changes := getChanges(file, wsFolderPcfg, qualifiedNameChanges)
            , changes != []
        };
    });

    return <toList(edits), ()>;
}
