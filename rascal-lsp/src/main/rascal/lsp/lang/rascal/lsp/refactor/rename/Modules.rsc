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

extend analysis::typepal::refactor::Rename;
import lang::rascal::\syntax::Rascal;

import analysis::typepal::TModel;
import analysis::diff::edits::TextEdits;
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
import util::PathConfig;
import util::Reflective;
import util::Util;

tuple[type[Tree] as, str desc] asType(moduleId(), _) = <#QualifiedName, "module name">;

tuple[set[loc], set[loc], set[loc]] findOccurrenceFilesUnchecked(set[Define] _:{<_, str defName, _, moduleId(), loc d, _>}, list[Tree] cursor, str newName, Tree(loc) getTree, Renamer r) {
    set[loc] useFiles = {};
    set[loc] newFiles = {};

    modName = normalizeEscaping(defName);
    modNameTree = [QualifiedName] modName;
    newModName = normalizeEscaping(newName);
    newModNameTree = [QualifiedName] newModName;

    modNameNumberOfNames = size(findAll(modName, "::")) + 1;
    newModNameNumberOfNames = size(findAll(newModName, "::")) + 1;

    try {
        loc oldLoc = getModuleLocation(modName, r.getConfig().getPathConfig(d.top));
        loc newLoc = getModuleLocation(newModName, r.getConfig().getPathConfig(d.top));
        if (oldLoc != newLoc) {
            r.msg(error(d, "Cannot rename, since module \'<newModName>\' already exists at <newLoc>"));
            return <{}, {}, {}>;
        }
    } catch _: {;}

    for (loc f <- getSourceFiles(r)) {
        m = getTree(f);

        bool markedNew = false;
        bool markedUse = false;

        top-down-break visit (m.top.header.imports) {
            case modNameTree: {
                // Import of exact module name
                useFiles += f;
                markedUse = true;
            }
        }
        bottom-up-break visit(m) {
            case QualifiedName qn: {
                // Import of redundantly escaped module name
                qnSize = size(asNames(qn));
                if (qnSize == modNameNumberOfNames && modName == normalizeEscaping("<qn>")) {
                    useFiles += f;
                    markedUse = true;
                }
                else if (qnSize == modNameNumberOfNames + 1 || qnSize == newModNameNumberOfNames + 1) {
                    qualPref = qualifiedPrefix(qn);
                    if (qualPref.name == modName || normalizeEscaping(qualPref.name) == modName) {
                        useFiles += f;
                        markedUse = true;
                    }
                    else if (qualPref.name == newModName || normalizeEscaping(qualPref.name) == newModName) {
                        newFiles += f;
                        markedNew = true;
                    }
                }
                if (markedUse && markedNew) continue;
            }
        }
    }
    return <{d.top}, useFiles, newFiles>;
}

bool isUnsupportedCursor(list[Tree] _:[*_, QualifiedName _, i:Import _, _, Header _, *_], Renamer r) {
    r.msg(error(i.src, "External imports are deprecated; renaming is not supported."));
    return true;
}

void renameDefinitionUnchecked(Define d:<_, currentName, _, moduleId(), _, _>, loc nameLoc, str newName, Renamer r) {
    if (currentName == newName) return;
    loc moduleFile = d.defined.top;
    pcfg = r.getConfig().getPathConfig(moduleFile);
    // Re-implement `relativize(loc, list[loc])`
    if (loc srcDir <- pcfg.srcs, loc relModulePath := relativize(srcDir, moduleFile), relModulePath != moduleFile) {
        // Change the file header
        r.textEdit(replace(nameLoc, newName));
        // Rename the file
        r.documentEdit(renamed(moduleFile, srcDir + makeFileName(forceUnescapeNames(newName))));
    } else {
        r.msg(error(moduleFile, "Cannot rename <currentName>, since it is not defined in this project."));
    }
}

void renameAdditionalUses(set[Define] _:{<_, moduleName, _, moduleId(), modDef, _>}, str newName, TModel tm, Renamer r) {
    // We get the module location from the uses. If there are no uses, this is skipped.
    // That's intended, since this function is only supposed to rename uses.
    if ({loc u, *_} := tm.useDef<0>) {
        for (/QualifiedName qn := r.getConfig().parseLoc(u.top), any(d <- tm.useDef[qn.src], d.top == modDef.top),
            pref := qualifiedPrefix(qn), moduleName == normalizeEscaping(pref.name)) {
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

list[TextEdit] getChangesByContents(loc f, PathConfig wsProject, lrel[str oldName, str newName, PathConfig pcfg] qualifiedNameChanges, void(Message) registerMessage) {
    str contents = readFile(f);
    changesInFile = [<oldName, newName>
        | <oldName, newName, projWithRenamedMod> <- qualifiedNameChanges
        , contains(contents, oldName) && isReachable(projWithRenamedMod, wsProject)
    ];

    if (changesInFile != []) {
        enum = "\n - ";
        changeList = enum + intercalate(enum, ["`<oldName>` -\> `<newName>`" | <oldName, newName> <- changesInFile]);
        registerMessage(warning("File contains reference(s) to renamed module(s), but it has a parse error and cannot be modified. Please modify this file manually.<changeList>", f));
    }
    return [];
}

list[TextEdit] getChanges(loc f, PathConfig wsProject, lrel[str oldName, str newName, PathConfig pcfg] qualifiedNameChanges, void(Message) registerMessage) {
    try {
        start[Module] m = parseModuleWithSpaces(f);
        return [replace(l, newName)
            | /QualifiedName qn := m
            , <oldName, l> <- {fullQualifiedName(qn), qualifiedPrefix(qn)}
            , [<newName, projWithRenamedMod>] := qualifiedNameChanges[normalizeEscaping(oldName)]
            , isReachable(projWithRenamedMod, wsProject)
        ];
    }
    catch Java("ParseError", str msg): return getChangesByContents(f, wsProject, qualifiedNameChanges, registerMessage);
    catch JavaException("ParseError", str msg): return getChangesByContents(f, wsProject, qualifiedNameChanges, registerMessage);
    // Catch all
    catch e: registerMessage(error("<e>", f));
    return [];
}

set[tuple[str, str, PathConfig]] getQualifiedNameChanges(loc old, loc new, PathConfig(loc) getPathConfig, void(Message) msg) {
    PathConfig oldPcfg = getPathConfig(old);
    PathConfig newPcfg = getPathConfig(new);
    // Moved a single file
    if (isFile(new)) {
        if(new.extension == "rsc") {
            // Moved a single Rascal module
            try {
                return {<normalizeEscaping(srcsModule(old, oldPcfg, fileConfig())), normalizeEscaping(srcsModule(new, newPcfg, fileConfig())), newPcfg>};
            } catch PathNotFound(loc f): {
                msg(error("Cannot rename references to this file, since it was moved outside of the project\'s source directories.", f));
                return {};
            }
        } else {
            // Renamed from .rsc to a non-Rascal extension
            str reason = new.extension == ""
                ? "its extension was removed"
                : "it was renamed to the non-Rascal extension \'<new.extension>\'"
                ;

            msg(error("Cannot rename references to thie file, since <reason>.", new));
            return {};
        }
    }

    // Moved directories
    set[tuple[str, str, PathConfig]] moves = {};
    for (loc newFile <- find(new, "rsc")
       , loc relFilePath := relativize(new, newFile)
       , loc oldFile := old + relFilePath.path) {
        try {
            moves += <normalizeEscaping(srcsModule(oldFile, oldPcfg, fileConfig())), normalizeEscaping(srcsModule(newFile, newPcfg, fileConfig())), newPcfg>;
        } catch PathNotFound(loc f): {
            msg(error("Cannot rename references to this file, since it was moved outside of the project\'s source directories.", f));
        }
    }

    return moves;
}

tuple[list[DocumentEdit], set[Message]] propagateModuleRenames(lrel[loc old, loc new] renames, set[loc] workspaceFolders, PathConfig(loc) getPathConfig) {
    set[Message] messages = {};
    void registerMessage(Message msg) { messages += msg; }
    lrel[str oldName, str newName, PathConfig pcfg] qualifiedNameChanges = [
        *getQualifiedNameChanges(oldLoc, newLoc, getPathConfig, registerMessage)
        | <oldLoc, newLoc> <- renames
    ];

    list[PathConfig] projectWithRenamedModule = qualifiedNameChanges.pcfg;
    set[DocumentEdit] edits = flatMap(workspaceFolders, set[DocumentEdit](loc wsFolder) {
        PathConfig wsFolderPcfg = getPathConfig(wsFolder);

        // If this workspace cannot reach any of the renamed modules, no need to continue looking for references to renamed modules here at all
        if (!any(PathConfig changedProj <- projectWithRenamedModule, isReachable(changedProj, wsFolderPcfg))) return {};

        return {changed(file, changes)
            | loc srcFolder <- wsFolderPcfg.srcs
            , loc file <- find(srcFolder, "rsc")
            , changes:[_, *_] := getChanges(file, wsFolderPcfg, qualifiedNameChanges, registerMessage)
        };
    });

    return <any(msg <- messages, msg is error) ? [] : toList(edits), messages>;
}
