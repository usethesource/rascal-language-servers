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
module lang::rascal::lsp::refactor::rename::Modules

extend framework::Rename;
import lang::rascal::\syntax::Rascal;

import analysis::typepal::TModel;
import lang::rascal::lsp::refactor::Rename;
import lang::rascal::lsp::refactor::rename::Common;
import lang::rascalcore::check::BasicRascalConfig;

import IO;
import List;
import Location;
import ParseTree;
import Set;
import String;

import util::FileSystem;
import util::Reflective;
import util::Util;

tuple[type[Tree] as, str desc] asType(moduleId()) = <#QualifiedName, "module name">;

tuple[set[loc], set[loc], set[loc]] findOccurrenceFilesUnchecked(set[Define] _:{<_, str curModName, _, moduleId(), loc d, _>}, list[Tree] cursor, str newName, Tree(loc) getTree, Renamer r) {
    loc modFile = d.top;
    set[loc] useFiles = {};
    set[loc] newFiles = {};

    modName = [QualifiedName] curModName;
    newModName = reEscape(newName);

    for (loc f <- getSourceFiles(r)) {
        bottom-up-break visit (getTree(f)) {
            case modName: {
                // Import of exact module name
                useFiles += f;
            }
            case QualifiedName qn: {
                // Import of redundantly escaped module name
                escQn = reEscape("<qn>");
                if (curModName == escQn) useFiles += f;
                else if (newModName == escQn) newFiles += f;
                else {
                    // Qualified use of declaration in module
                    // If through extends, there might be no import
                    qualPref = reEscape(qualifiedPrefix(qn).name);
                    if (qualPref == curModName) useFiles += f;
                    if (qualPref == newModName) newFiles += f;
                }
            }
        }
    }
    return <{modFile}, useFiles, newFiles>;
}

bool isUnsupportedCursor(list[Tree] _:[*_, QualifiedName _, i:Import _, _, Header _, *_], Renamer r) {
    r.error(i.src, "External imports are deprecated; renaming is not supported.");
    return true;
}

void renameDefinitionUnchecked(Define d:<_, currentName, _, moduleId(), _, _>, loc nameLoc, str newName, TModel tm, Renamer r) {
    r.textEdit(replace(nameLoc, newName));

    // Additionally, we rename the file
    if (currentName == newName) return;
    loc moduleFile = d.defined.top;
    pcfg = r.getConfig().getPathConfig(moduleFile);
    loc relModulePath = relativize(pcfg.srcs, moduleFile);
    loc srcFolder = [srcFolder | srcFolder <- pcfg.srcs, exists(srcFolder + relModulePath.path)][0];
    r.documentEdit(renamed(moduleFile, srcFolder + makeFileName(forceUnescapeNames(newName))));
}

void renameAdditionalUses(set[Define] _:{<_, moduleName, _, moduleId(), modDef, _>}, str newName, TModel tm, Renamer r) {
    // We get the module location from the uses. If there are no uses, this is skipped.
    // That's intended, since this function is only supposed to rename uses.
    if ({loc u, *_} := tm.useDef<0>) {
        for (/QualifiedName qn := r.getConfig().parseLoc(u.top), any(d <- tm.useDef[qn.src], d.top == modDef.top),
            pref := qualifiedPrefix(qn), moduleName == reEscape(pref.name)) {
            r.textEdit(replace(pref.l, newName));
        }
    }
}

private tuple[str, loc] fullQualifiedName(QualifiedName qn) = <"<qn>", qn.src>;
private tuple[str name, loc l] qualifiedPrefix(QualifiedName qn) {
    list[Name] prefixNames = prefix([n | n <- qn.names]);
    if ([] := prefixNames) return <"", |unknown:///|>;

    return <intercalate("::", ["<n>" | n <- prefixNames]), cover([n.src | n <- prefixNames])>;
}

private bool isReachable(PathConfig toProject, PathConfig fromProject) =
    toProject == fromProject           // Both configs belong to the same project
 || toProject.bin in fromProject.libs; // The using project can import the declaring project

list[TextEdit] getChanges(loc f, PathConfig wsProject, rel[str oldName, str newName, PathConfig pcfg] qualifiedNameChanges) {
    list[TextEdit] changes = [];

    start[Module] m = parseModuleWithSpaces(f);
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

set[tuple[str, str, PathConfig]] getQualifiedNameChanges(loc old, loc new, PathConfig(loc) getPathConfig) {
    PathConfig oldPcfg = getPathConfig(old);
    PathConfig newPcfg = getPathConfig(new);
    if (isFile(new) && endsWith(new.file, ".rsc")) {
        return {<safeRelativeModuleName(old, oldPcfg), safeRelativeModuleName(new, newPcfg), newPcfg>};
    }

    return {
        <safeRelativeModuleName(oldFile, oldPcfg), safeRelativeModuleName(newFile, newPcfg), newPcfg>
        | loc newFile <- find(new, "rsc")
        , loc relFilePath := relativize(new, newFile)
        , loc oldFile := old + relFilePath.path
    };
}

tuple[list[DocumentEdit], set[Message]] propagateModuleRenames(list[tuple[loc old, loc new]] renames, set[loc] workspaceFolders, PathConfig(loc) getPathConfig) {
    rel[str oldName, str newName, PathConfig pcfg] qualifiedNameChanges = {
        rename
        | <oldLoc, newLoc> <- renames
        , tuple[str, str, PathConfig] rename <- getQualifiedNameChanges(oldLoc, newLoc, getPathConfig)
    };

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

    return <toList(edits), {}>;
}
