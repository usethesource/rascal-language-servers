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
module lang::rascal::lsp::refactor::WorkspaceInfo

import Relation;

import analysis::typepal::TModel;

import lang::rascalcore::check::Checker;

import lang::rascal::\syntax::Rascal;

import util::FileSystem;
import util::Maybe;
import util::Monitor;
import util::Reflective;

import lang::rascal::lsp::refactor::Exception;
import lang::rascal::lsp::refactor::Util;

import IO;
import List;
import Location;
import Map;
import Set;
import String;

data CursorKind = use()
                | def()
                | typeParam()
                | collectionField()
                | moduleName()
                ;

data Cursor = cursor(CursorKind kind, loc l, str name);

alias MayOverloadFun = bool(set[loc] defs, map[loc, Define] defines);
alias FileRenamesF = rel[loc old, loc new](str newName);
alias DefsUsesRenames = tuple[set[loc] defs, set[loc] uses, FileRenamesF renames];
alias ProjectFiles = rel[loc projectFolder, loc file];

data WorkspaceInfo (
    // Instance fields
    // Read-only
    rel[loc use, loc def] useDef = {},
    set[Define] defines = {},
    set[loc] modules = {},
    map[loc, Define] definitions = (),
    map[loc, AType] facts = (),
    set[loc] projects = {}
) = workspaceInfo(
    ProjectFiles() preloadFiles,
    ProjectFiles() allFiles,
    set[TModel](ProjectFiles) tmodelsForLocs
);

WorkspaceInfo loadLocs(WorkspaceInfo ws, ProjectFiles projectFiles) {
    for (tm <- ws.tmodelsForLocs(projectFiles)) {
        ws = loadTModel(ws, tm);
    }
    ws.modules += projectFiles.file;
    ws.projects += projectFiles.projectFolder;

    return ws;
}

WorkspaceInfo preLoad(WorkspaceInfo ws) {
    return loadLocs(ws, ws.preloadFiles());
}

WorkspaceInfo loadWorkspace(WorkspaceInfo ws) {
    return loadLocs(ws, ws.allFiles());
}

WorkspaceInfo loadTModel(WorkspaceInfo ws, TModel tm) {
    try {
        throwAnyErrors(tm);
    } catch set[Message] errors: {
        throw unsupportedRename("Cannot rename: some modules in workspace have errors.\n<toString(errors)>", issues={<(error.at ? |unknown:///|), error.msg> | error <- errors});
    }

    ws.useDef      += tm.useDef;
    ws.defines     += tm.defines;
    ws.definitions += tm.definitions;
    ws.facts       += tm.facts;

    return ws;
}

loc getProjectFolder(WorkspaceInfo ws, loc l) {
    if (project <- ws.projects, isPrefixOf(project, l)) {
        return project;
    }

    throw "Could not find project containing <l>";
}

set[loc] getUses(WorkspaceInfo ws, loc def) = invert(ws.useDef)[def];

set[loc] getUses(WorkspaceInfo ws, set[loc] defs) = invert(ws.useDef)[defs];

set[loc] getDefs(WorkspaceInfo ws, loc use) = ws.useDef[use];

Maybe[AType] getFact(WorkspaceInfo ws, loc l) = l in ws.facts ? just(ws.facts[l]) : nothing();

set[loc] getOverloadedDefs(WorkspaceInfo ws, set[loc] defs, MayOverloadFun mayOverloadF) {
    set[loc] overloadedLocs = defs;

    // Pre-condition
    assert mayOverloadF(overloadedLocs, ws.definitions):
        "Initial defs are invalid overloads!";

    for (loc d <- ws.definitions) {
        if (mayOverloadF(defs + d, ws.definitions)) {
            overloadedLocs += d;
        }
    }

    return overloadedLocs;
}

private rel[loc, loc] NO_RENAMES(str _) = {};
private int qualSepSize = size("::");

DefsUsesRenames getDefsUses(WorkspaceInfo ws, cursor(use(), l, cursorName), MayOverloadFun mayOverloadF, PathConfig(loc) _) {
    defs = getOverloadedDefs(ws, getDefs(ws, l), mayOverloadF);
    uses = getUses(ws, defs);
    return <defs, uses, NO_RENAMES>;
}

DefsUsesRenames getDefsUses(WorkspaceInfo ws, cursor(def(), l, _), MayOverloadFun mayOverloadF, PathConfig(loc) _) {
    defs = getOverloadedDefs(ws, {l}, mayOverloadF);
    uses = getUses(ws, defs);
    return <defs, uses, NO_RENAMES>;
}

DefsUsesRenames getDefsUses(WorkspaceInfo ws, cursor(typeParam(), cursorLoc, cursorName), MayOverloadFun _, PathConfig(loc) _) {
    AType at = ws.facts[cursorLoc];
    if(Define d: <_, _, _, _, _, defType(afunc(_, /at, _))> <- ws.defines, isContainedIn(cursorLoc, d.defined)) {
        // From here on, we can assume that all locations are in the same file, because we are dealing with type parameters and filtered on `isContainedIn`
        facts = {<l, ws.facts[l]> | l <- ws.facts
                                  , at := ws.facts[l]
                                  , isContainedIn(l, d.defined)};

        formals = {l | <l, f> <- facts, f.alabel != ""};

        // Given the location/offset of `&T`, find the location/offset of `T`
        offsets = sort({l.offset | l <- facts<0>});
        nextOffsets = toMapUnique(zip2(prefix(offsets), tail(offsets)));

        loc sentinel = |unknown:///|(0, 0, <0, 0>, <0, 0>);
        defs = {min([f | f <- facts<0>, f.offset == nextOffset]) | formal <- formals, nextOffsets[formal.offset]?, nextOffset := nextOffsets[formal.offset]};

        useDefs = {trim(l, removePrefix = l.length - size(cursorName))
                    | l <- facts<0>
                    , !ws.definitions[l]? // If there is a definition at this location, this is a formal argument name
                    , !any(ud <- ws.useDef[l], ws.definitions[ud]?) // If there is a definition for the use at this location, this is a use of a formal argument
                    , !any(flInner <- facts<0>, isStrictlyContainedIn(flInner, l)) // Filter out any facts that contain other facts
        };

        return <defs, useDefs - defs, NO_RENAMES>;
    }

    throw unsupportedRename("Cannot find function definition which defines template variable \'<cursorName>\'");
}

DefsUsesRenames getDefsUses(WorkspaceInfo ws, cursor(collectionField(), cursorLoc, cursorName), MayOverloadFun _, PathConfig(loc) _) {
    AType cursorType = ws.facts[cursorLoc];
    factLocsSortedBySize = sort(domain(ws.facts), bool(loc l1, loc l2) { return l1.length < l2.length; });

    AType fieldType = avoid();
    AType collectionType = avoid();
    if (l <- factLocsSortedBySize, isStrictlyContainedIn(cursorLoc, l), at := ws.facts[l], (at is arel || at is alrel || at is atuple), at.elemType is atypeList) {
        // We are at a definition site
        collectionType = at;
        fieldType = cursorType;
    }
    else {
        // We are at a use site, where the field element type is wrapped in a `aset` of `alist` constructor
        fieldType = cursorType.alabel == ""
            // Collection type
            ? cursorType.elmType
            // Tuple type
            : cursorType;

        // We need to find the collection type by looking for the first use to the left of the cursor that has a collection type
        usesToLeft = reverse(sort({<u, d> | <u, d> <- ws.useDef, isSameFile(u, cursorLoc), u.offset < cursorLoc.offset}));
        if (<_, d> <- usesToLeft, define := ws.definitions[d], defType(AType at) := define.defInfo, (at is arel || at is alrel || at is atuple)) {
            collectionType = at;
        } else {
            throw unsupportedRename("Could not find a collection definition corresponding to the field at the cursor.");
        }
    }

    set[loc] collectionFacts = invert(ws.facts)[collectionType];
    rel[loc file, loc u] factsByModule = groupBy(collectionFacts, loc(loc l) { return l.top; });

    set[loc] defs = {};
    set[loc] uses = {};
    for (file <- factsByModule.file) {
        fileFacts = factsByModule[file];
        visit(parseModuleWithSpacesCached(file)) {
            case (Expression) `<Expression e>.<Name field>`: {
                if ("<field>" == cursorName && any(f <- fileFacts, isContainedIn(f, e.src))) {
                    uses += field.src;
                }
            }
            case (Assignable) `<Assignable rec>.<Name field>`: {
                if ("<field>" == cursorName && any(f <- fileFacts, isContainedIn(f, rec.src))) {
                    uses += field.src;
                }
            }
            case (Expression) `<Expression e>\< <{Field ","}+ fields> \>`: {
                if (any(f <- fileFacts, isContainedIn(e.src, f))) {
                    uses += {field.src | field <- fields
                                       , field is name
                                       , "<field.fieldName>" == cursorName};
                }
            }
            case (Expression) `<Expression e>[<Name field> = <Expression _>]`: {
                if ("<field>" == cursorName && any(f <- fileFacts, isContainedIn(f, e.src))) {
                    uses += field.src;
                }
            }
            case t:(StructuredType) `<BasicType _>[<{TypeArg ","}+ args>]`: {
                for (at := ws.facts[t.src], collectionType.elemType == at.elemType, (TypeArg) `<Type _> <Name name>` <- args, fieldType == ws.facts[name.src]) {
                    defs += name.src;
                }
            }
        }
    }

    if (defs == {}) {
        throw unsupportedRename("Cannot rename field that is not declared inside this workspace.");
    }

    return <defs, uses, NO_RENAMES>;
}

DefsUsesRenames getDefsUses(WorkspaceInfo ws, cursor(moduleName(), cursorLoc, cursorName), MayOverloadFun _, PathConfig(loc) getPathConfig) {
    loc moduleFile = |unknown:///|;
    if (d <- ws.useDef[cursorLoc], amodule(_) := ws.facts[d]) {
        // Cursor is at an import
        moduleFile = d.top;
    } else if (just(l) := findSmallestContaining(ws.facts<0>, cursorLoc), amodule(_) := ws.facts[l]) {
        // Cursor is at a module header
        moduleFile = l.top;
    } else {
        // Cursor is at the module part of a qualified use
        if (<u, d> <- ws.useDef, u.begin <= cursorLoc.begin, u.end == cursorLoc.end) {
            moduleFile = d.top;
        } else {
            throw unsupportedRename("Could not find cursor location in TPL.");
        }
    }

    modName = getModuleName(moduleFile, getPathConfig(getProjectFolder(ws, moduleFile)));

    defs = {parseModuleWithSpacesCached(moduleFile).top.header.name.names[-1].src};

    imports = {u | u <- ws.useDef<0>, amodule(modName) := ws.facts[u]};
    qualifiedUses = {
        // We compute the location of the module name in the qualified name at `u`
        // some::qualified::path::to::Foo::SomeVar
        // \____________________________/\/\_____/
        // moduleNameSize ^  qualSepSize ^   ^ idSize
        trim(u, removePrefix = moduleNameSize - size(cursorName)
              , removeSuffix = idSize + qualSepSize)
        | <loc u, Define d> <- ws.useDef o toRel(ws.definitions)
        , idSize := size(d.id)
        , u.length > idSize // There might be a qualified prefix
        , moduleNameSize := size(modName)
        , u.length == moduleNameSize + qualSepSize + idSize
    };
    uses = imports + qualifiedUses;

    rel[loc, loc] getRenames(str newName) = {<file, file[file = "<newName>.rsc"]> | d <- defs, file := d.top};

    return <defs, uses, getRenames>;
}
