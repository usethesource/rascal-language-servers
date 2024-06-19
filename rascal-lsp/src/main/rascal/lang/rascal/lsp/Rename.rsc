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

import util::Maybe;
import util::Reflective;

// Only needed until we move to newer type checker
// TODO Remove
import ValueIO;

alias Capture = tuple[loc def, loc use];

data IllegalRenameReason
    = invalidName(str name)
    | doubleDeclaration(loc old, set[loc] new)
    | captureChange(set[Capture] captures)
    ;

data RenameException
    = illegalRename(loc location, set[IllegalRenameReason] reason)
    | unsupportedRename(rel[loc location, str message] issues)
    | unexpectedFailure(str message)
;

private set[loc] getUses(TModel tm, loc def) {
    return invert(getUseDef(tm))[def];
}

private set[Define] getDefines(TModel tm, loc use) {
    return {tm.definitions[d] | d <- tm.useDef[use]};
}

void throwIfNotEmpty(&T(&A) R, &A arg) {
    if (arg != {}) {
        throw R(arg);
    }
}

set[IllegalRenameReason] checkLegalName(str name) {
    try {
        parse(#Name, escapeName(name));
        return {};
    } catch ParseError(_): {
        return {invalidName(name)};
    }
}

set[IllegalRenameReason] checkCausesDoubleDeclarations(set[Define] currentDefs, set[Define] newDefs) {
    // Is newName already resolvable from a scope where <current-name> is currently declared?
    rel[loc old, loc new] doubleDeclarations = {<cD.defined, nD.defined> | Define nD <- newDefs
                                                                         , Define cD <- currentDefs
                                                                         , isContainedIn(cD.defined, nD.scope)
    };

    return {doubleDeclaration(old, doubleDeclarations[old]) | old <- doubleDeclarations.old};
}

set[Define] findImplicitDefinitions(TModel tm, start[Module] m, set[Define] newDefs) {
    set[loc] maybeImplicitDefs = {l | /QualifiedName n := m, just(l) := locationOfName(n)};
    return {def | Define def <- newDefs, (def.idRole is variableId && def.defined in tm.useDef<0>)
                                      || (def.idRole is patternVariableId && def.defined in maybeImplicitDefs)};
}

set[IllegalRenameReason] checkCausesCaptures(TModel tm, start[Module] m, set[Define] currentDefs, set[loc] currentUses, set[Define] newDefs) {
    set[Define] newNameImplicitDefs = findImplicitDefinitions(tm, m, newDefs);
    set[Define] newNameExplicitDefs = newDefs - newNameImplicitDefs;

    // Will this rename turn an implicit declaration of `newName` into a use of a current declaration?
    set[Capture] implicitDeclBecomesUseOfCurrentDecl =
        {<cD, nD.defined> | Define nD <- newNameImplicitDefs
                          , <loc cS, loc cD> <- currentDefs<scope, defined>
                          , isContainedIn(nD.defined, cS)
        };

    // Will this rename turn a use of <oldName> into a use of an existing declaration of <newName> (shadowing)?
    set[Capture] explicitDeclShadowsCurrentDecl =
        {<nD.defined, cU> | Define nD <- newNameExplicitDefs
                          , <cU, cS> <- ident(currentUses) o tm.useDef o tm.defines<defined, scope>
                          , isContainedIn(cU, nD.scope)
                          , isStrictlyContainedIn(nD.scope, cS)
        };

    allCaptures = implicitDeclBecomesUseOfCurrentDecl + explicitDeclShadowsCurrentDecl;
    return allCaptures == {} ? {} : {captureChange(allCaptures)};
}

void checkLegalRename(TModel tm, start[Module] m, loc cursorLoc, set[Define] currentDefs, set[loc] currentUses, str newName) {
    set[Define] newNameDefs = {def | def:<_, newName, _, _, _, _>  <- tm.defines};

    checkUnsupported(m.src, currentDefs);

    set[IllegalRenameReason] reasons =
        checkLegalName(newName)
      + checkCausesDoubleDeclarations(currentDefs, newNameDefs)
      + checkCausesCaptures(tm, m, currentDefs, currentUses, newNameDefs)
    ;

    if (reasons != {}) {
        throw illegalRename(cursorLoc, reasons);
    }
}

void checkUnsupported(loc moduleLoc, set[Define] defsToRename) {
    Maybe[str] isUnsupportedDefinition(Define _: <scope, id, _, functionId(), _, _>) = just("Global function definitions might span multiple modules; unsupported for now.")
        when moduleLoc == scope; // function is defined in module scope
    default Maybe[str] isUnsupportedDefinition(Define _) = nothing();

    throwIfNotEmpty(unsupportedRename, {<def.defined, msg> | def <- defsToRename, just(msg) := isUnsupportedDefinition(def)});
}

str escapeName(str name) = name in getRascalReservedIdentifiers() ? "\\<name>" : name;

loc findDeclarationAroundName(start[Module] m, loc nameLoc) {
    // we want to find the *largest* tree of defined non-terminal type of which the declared name is at nameLoc
    top-down visit (m.top) {
        case t: appl(prod(_, _, _), _): {
            if (just(nameLoc) := locationOfName(t)) {
                return t.src;
            }
        }
    }

    throw unexpectedFailure("No declaration found with name at <nameLoc> in module <m>");
}

loc findNameInDeclaration(start[Module] m, loc declLoc) {
    // we want to find the smallest tree of defined non-terminal type with source location `declLoc`
    visit(m.top) {
        case t: appl(prod(_, _, _), _, src = declLoc): {
            if (just(nameLoc) := locationOfName(t)) {
                return nameLoc;
            }
            throw UnsupportedRename(t, "Cannot find name in <t>");
        }
    }

    throw unexpectedFailure("No declaration at <declLoc> found within module <m>");
}

Maybe[loc] locationOfName(Name n) = just(n.src);
Maybe[loc] locationOfName(QualifiedName qn) = just((qn.names[-1]).src);
Maybe[loc] locationOfName(FunctionDeclaration f) = just(f.signature.name.src);
default Maybe[loc] locationOfName(Tree t) = nothing();

list[DocumentEdit] renameRascalSymbol(start[Module] m, Tree cursor, set[loc] workspaceFolders, TModel tm, str newName) {
    loc cursorLoc = cursor.src;

    set[Define] defs = {};
    if (cursorLoc in tm.useDef<0>) {
        // Cursor is at a use
        defs = getDefines(tm, cursorLoc);
    } else {
        // Cursor is at a name within a declaration
        loc cursorAtDef = findDeclarationAroundName(m, cursorLoc);
        defs = tm.definitions[cursorAtDef] + getDefines(tm, cursorAtDef);
    }

    set[loc] uses = ({} | it + getUses(tm, def) | def <- defs.defined);

    checkLegalRename(tm, m, cursorLoc, defs, uses, newName);

    rel[loc file, loc useDefs] useDefsPerFile = { <useDef.top, useDef> | loc useDef <- uses + defs<defined>};
    list[DocumentEdit] changes = [changed(file, [replace(findNameInDeclaration(m, useDef), escapeName(newName)) | useDef <- useDefsPerFile[file]]) | loc file <- useDefsPerFile.file];;
    // TODO If the cursor was a module name, we need to rename files as well
    list[DocumentEdit] renames = [];

    return changes + renames;
}

// Compute the edits for the complete workspace here
list[DocumentEdit] renameRascalSymbol(start[Module] m, Tree cursor, set[loc] workspaceFolders, PathConfig pcfg, str newName) {
    str moduleName = getModuleName(m.src.top, pcfg);
    ModuleStatus ms = newModuleStatus(pcfg);
    <success, tm, ms> = getTModelForModule(moduleName, ms);
    if (!success) {
        throw unexpectedFailure(tm.messages[0].msg);
    }

    return renameRascalSymbol(m, cursor, workspaceFolders, tm, newName);
}

//// WORKAROUNDS
//// Most (copied) definitions below can probably go once we depend on the new typechecker and new release of Rascal (Core)

// Workaround to be able to pattern match on the emulated `src` field
data Tree (loc src = |unknown:///|(0,0,<0,0>,<0,0>));

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
