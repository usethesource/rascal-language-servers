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
module lang::rascal::lsp::Rename

import Exception;
import IO;
import List;
import Location;
import Node;
import ParseTree;
import Relation;
import Set;
import String;

import lang::rascal::\syntax::Rascal;
// import lang::rascalcore::check::Checker;
import lang::rascalcore::check::Import;
import analysis::typepal::TypePal;
import analysis::typepal::TModel;

import analysis::diff::edits::TextEdits;

import vis::Text;

import util::Reflective;

// Only needed until we move to newer type checker
// TODO Remove
import ValueIO;

data RuntimeException
    = IllegalRename(loc location, str message)
    | UnsupportedRename(loc location, str message)
    | UnexpectedFailure(str message)
;

private set[loc] getUses(TModel tm, loc def) {
    return invert(getUseDef(tm))[def];
}

private set[loc] getDefinitions(TModel tm, loc use) {
    return getUseDef(tm)[use] ? {};
}

bool isLegalName(str name) {
    try {
        parse(#Name, escapeName(name));
        return true;
    } catch ParseError(_): {
        return false;
    }
}

loc findSmallestEnclosingScope(TModel tm, loc l) {
    return (l.top | isContainedIn(inner, it) && isContainedIn(l, inner) ? inner : it | inner <- tm.scopes);
}

bool renameCausesDoubleDeclaration(TModel tm, set[loc] defLocs, set[loc] useLocs, str newName) {
    // Is newName already resolvable from a scope where <current-name> is currently used or declared?
    set[loc] newNameScopes = {def.scope | def <- tm.defines, def.id == newName};
    return any(loc dS <- newNameScopes, loc l <- (useLocs + defLocs), isContainedIn(l, dS));
}

bool isImplicitDefinition(start[Module] _, map[loc, loc] useDef, Define _: <_, _, _, variableId(), defined, _>) = defined in useDef; // use of name at implicit declaration loc

bool isImplicitDefinition(start[Module] m, map[loc, loc] _, Define _: <_, _, _, patternVariableId(), defined, _>) {
    visit (m) {
        case qualifiedName(qn): {
            if (findNameLocation(qn) == defined) return true;
        }
        case multiVariable(qn): {
            if (findNameLocation(qn) == defined) return true;
        }
        case variableBecomes(n, _): {
            if (findNameLocation(n) == defined) return true;
        }
    }

    return false;
}

default bool isImplicitDefinition(start[Module] _, map[loc, loc] _, Define _) = false;

bool renameCausesCapture(TModel tm, start[Module] m, set[loc] currentNameDefLocs, str newName) {
    // Is newName implicitly declared in a scope from where <current-name> can be resolved?
    set[loc] currentNameScopes = {tm.definitions[dL].scope | loc dL <- currentNameDefLocs};
    map[loc, loc] useDef = toMapUnique(tm.useDef);
    set[loc] newNameImplicitDeclLocs = {def.defined | def <- tm.defines, def.id == newName, isImplicitDefinition(m, useDef, def)};

    return any(loc iD <- newNameImplicitDeclLocs, loc dS <- currentNameScopes, isContainedIn(iD, dS));
}

bool isLegalRename(TModel tm, start[Module] m, set[loc] defLocs, set[loc] useLocs, str newName) {
    if (!isLegalName(newName)) {
        println("Rename rejected: illegal name \'<newName>\'");
        return false;
    }
    if (renameCausesDoubleDeclaration(tm, defLocs, useLocs, newName)) {
        println("Rename rejected: causes double declaration");
        return false;
    }
    if (renameCausesCapture(tm, m, defLocs, newName)) {
        println("Rename rejected: causes capture at implicit declaration");
        return false;
    }

    return true;
}

str escapeName(str name) = name in getRascalReservedIdentifiers() ? "\\<name>" : name;

list[DocumentEdit] renameRascalSymbol(start[Module] m, Tree cursor, set[loc] workspaceFolders, TModel tm, str newName) {
    loc cursorLoc = cursor.src;
    set[loc] defs = getDefinitions(tm, cursorLoc);

    if (defs == {}) {
        // Cursor points at a definition
        defs += cursorLoc;
    }

    set[loc] uses = ({} | it + getUses(tm, def) | loc def <- defs);

    if (!isLegalRename(tm, m, defs, uses, newName)) {
        throw IllegalRename(cursorLoc, newName);
    }

    // TODO Check if all definitions are user-defined;
    // i.e. we're not trying to rename something from stdlib or compiled libraries(?)

    print("Definition locations ");
    iprintln(defs);

    print("Usage locations ");
    iprintln(uses);

    set[loc] useDefs = uses + defs;

    rel[loc file, loc useDefs] useDefsPerFile = { <useDef.top, useDef> | loc useDef <- useDefs};
    println("Use/Defs per file:");
    iprintln(toMap(useDefsPerFile));

    list[DocumentEdit] changes = [changed(file, [replace(findNameLocation(m, useDef), escapeName(newName)) | useDef <- useDefsPerFile[file]]) | loc file <- useDefsPerFile.file];;

    // TODO If the cursor was a module name, we need to rename files as well
    list[DocumentEdit] renames = [];

    list[DocumentEdit] edits = changes + renames;

    return edits;
}

// This is copied from the newest version of the type-checker and simplified for future compatibilty.
// TODO Remove once we migrate to a newer type checker version
data ModuleStatus =
    moduleStatus(
      map[str, list[Message]] messages,
      PathConfig pathConfig
   );

// This is copied from the newest version of the type-checker and simplified for future compatibilty.
// TODO Remove once we migrate to a newer type checker version
ModuleStatus newModuleStatus(PathConfig pcfg) = moduleStatus((), pcfg);

// This is copied from the newest version of the type-checker and simplified for future compatibilty.
// TODO Remove once we migrate to a newer type checker version
private tuple[bool, TModel, ModuleStatus] getTModelForModule(str m, ModuleStatus ms) {
    pcfg = ms.pathConfig;
    <found, tplLoc> = getTPLReadLoc(m, pcfg);
    if (!found) {
        return <found, tmodel(), moduleStatus((), pcfg)>;
    }

    try {
        tpl = readBinaryValueFile(#TModel, tplLoc);
        return <true, tpl, ms>;
    } catch e: {
        //ms.status[qualifiedModuleName] ? {} += not_found();
        return <false, tmodel(modelName=qualifiedModuleName, messages=[error("Cannot read TPL for <qualifiedModuleName>: <e>", tplLoc)]), ms>;
        //throw IO("Cannot read tpl for <qualifiedModuleName>: <e>");
    }
}

// Compute the edits for the complete workspace here
list[DocumentEdit] renameRascalSymbol(start[Module] m, Tree cursor, set[loc] workspaceFolders, PathConfig pcfg, str newName) {
    str moduleName = getModuleName(m.src.top, pcfg);
    ModuleStatus ms = newModuleStatus(pcfg);
    <success, tm, ms> = getTModelForModule(moduleName, ms);
    if (!success) {
        throw UnexpectedFailure(tm.messages[0].msg);
    }

    return renameRascalSymbol(m, cursor, workspaceFolders, tm, newName);
}

// Workaround to be able to pattern match on the emulated `src` field
data Tree (loc src = |unknown:///|(0,0,<0,0>,<0,0>));

loc findNameLocation(start[Module] m, loc declLoc) {
    // we want to find the smallest tree of defined non-terminal type with source location `declLoc`
    visit(m.top) {
        case t: appl(prod(_, _, _), _, src = declLoc): {
            return findNameLocation(t);
        }
    }

    throw "No declaration at <declLoc> found within module <m>";
}

loc findNameLocation(Name n) = n.src;
loc findNameLocation(QualifiedName qn) = (qn.names[-1]).src;
loc findNameLocation(FunctionDeclaration f) = f.signature.name.src when f.visibility is \private;
loc findNameLocation(FunctionDeclaration f) {
    throw UnsupportedRename(f.src, "Renaming public functions is not supported");
}

default loc findNameLocation(Tree t) {
    println("Unsupported: cannot find name in <t.src>");
    print(prettyTree(t));
    rprintln(t);
    throw UnsupportedRename(t, "Cannot find name in <t>");
}
