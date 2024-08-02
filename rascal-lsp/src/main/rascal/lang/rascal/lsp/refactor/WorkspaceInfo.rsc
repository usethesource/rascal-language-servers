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

import util::Maybe;
import util::Reflective;

import lang::rascal::lsp::refactor::Exception;
import lang::rascal::lsp::refactor::Util;

import List;
import Location;
import Map;
import Set;
import String;

import IO;

data CursorKind
    = use()
    | def()
    | typeParam()
    | collectionField()
    | moduleName()
    ;

data Cursor
    = cursor(CursorKind kind, loc l, str name)
    ;

alias MayOverloadFun = bool(set[loc] defs, map[loc, Define] defines);
alias FileRenamesF = rel[loc old, loc new](str newName);
alias DefsUsesRenames = tuple[set[loc] defs, set[loc] uses, FileRenamesF renames];
alias ProjectFiles = rel[loc projectFolder, loc file];

/**
 * This is a subset of the fields from analysis::typepal::TModel, specifically tailored to refactorings.
 * WorkspaceInfo comes with a set of functions that allow (incrementally) loading information from multiple TModels, and doing (cached) queries on this data.
 */
data WorkspaceInfo (
    // Instance fields
    // Read-only
    rel[loc use, loc def] useDef = {},
    set[Define] defines = {},
    set[loc] sourceFiles = {},
    map[loc, Define] definitions = (),
    map[loc, AType] facts = (),
    Scopes scopes = (),
    Paths paths = {},
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

    // In addition to data from the TModel, we keep track of which projects/modules we loaded.
    ws.sourceFiles += projectFiles.file;
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
        throw unsupportedRename("Cannot rename: some files in workspace have errors.\n<toString(errors)>", issues={<(error.at ? |unknown:///|), error.msg> | error <- errors});
    }

    ws.useDef      += tm.useDef;
    ws.defines     += tm.defines;
    ws.definitions += tm.definitions;
    ws.facts       += tm.facts;
    ws.scopes      += tm.scopes;
    ws.paths       += tm.paths;

    return ws;
}

loc getProjectFolder(WorkspaceInfo ws, loc l) {
    if (project <- ws.projects, isPrefixOf(project, l)) {
        return project;
    }

    throw "Could not find project containing <l>";
}

@memo{maximumSize(1), expireAfter(minutes=5)}
rel[loc, loc] defUse(WorkspaceInfo ws) = invert(ws.useDef);

@memo{maximumSize(1), expireAfter(minutes=5)}
map[AType, set[loc]] factsInvert(WorkspaceInfo ws) = invert(ws.facts);

set[loc] getUses(WorkspaceInfo ws, loc def) = defUse(ws)[def];

set[loc] getUses(WorkspaceInfo ws, set[loc] defs) = defUse(ws)[defs];

set[loc] getDefs(WorkspaceInfo ws, loc use) = ws.useDef[use];

Maybe[AType] getFact(WorkspaceInfo ws, loc l) = l in ws.facts ? just(ws.facts[l]) : nothing();

@memo{maximumSize(1), expireAfter(minutes=5)}
set[loc] getModuleScopes(WorkspaceInfo ws) = invert(ws.scopes)[|global-scope:///|];

@memo{maximumSize(1), expireAfter(minutes=5)}
map[loc, loc] getModuleScopePerFile(WorkspaceInfo ws) = (scope.top: scope | loc scope <- getModuleScopes(ws));

@memo{maximumSize(1), expireAfter(minutes=5)}
rel[loc from, loc to] rascalGetTransitiveReflexiveModulePaths(WorkspaceInfo ws) {
    rel[loc from, loc to] moduleI = ident(getModuleScopes(ws));
    rel[loc from, loc to] imports = (ws.paths<pathRole, from, to>)[importPath()];
    rel[loc from, loc to] extends = (ws.paths<pathRole, from, to>)[extendPath()];

    return (moduleI + imports)  // o or 1 imports
         o (moduleI + extends+) // 0 or more extends
         ;
}

set[loc] rascalGetOverloadedDefs(WorkspaceInfo ws, set[loc] defs, MayOverloadFun mayOverloadF) {
    set[loc] overloadedDefs = defs;

    // Pre-condition
    assert mayOverloadF(overloadedDefs, ws.definitions):
        "Initial defs are invalid overloads!";

    map[loc file, loc scope] moduleScopePerFile = getModuleScopePerFile(ws);
    rel[loc d, loc scope] scopesOfDefUses = {<d, moduleScopePerFile[u.top]> | <loc u, loc d> <- ws.useDef};
    rel[loc def, loc scope] defAndTheirUseScopes = ws.defines<defined, scope> + scopesOfDefUses;
    rel[loc from, loc to] modulePaths = rascalGetTransitiveReflexiveModulePaths(ws);

    solve(overloadedDefs) {
        rel[loc from, loc to] reachableDefs =
            ident(overloadedDefs)       // Start from all current defs
          o defAndTheirUseScopes        // - Look up their scope and scopes of their uses
          o modulePaths                 // - Follow import/extend relations to reachable scopes
          o ws.defines<scope, defined>  // - Find definitions in the reached scope
          ;

        overloadedDefs += {d
            | loc d <- reachableDefs<1>
            , mayOverloadF(overloadedDefs + d, ws.definitions)
        };
    }

    return overloadedDefs;
}

private rel[loc, loc] NO_RENAMES(str _) = {};
private int qualSepSize = size("::");

bool rascalIsCollectionType(AType at) = at is arel || at is alrel || at is atuple;

set[loc] rascalGetKeywordFormalUses(WorkspaceInfo ws, set[loc] defs, str cursorName) {
    set[loc] uses = {};

    for (d <- defs
       , f <- ws.facts
       , isStrictlyContainedIn(d, f)
       , afunc(retType, _, [*_, kwField(kwAType, cursorName, _), *_]) := ws.facts[f]
       , funcName <- getUses(ws, f)
       ) {
        loc funcCall = min({l | l <- factsInvert(ws)[retType], l.offset == funcName.offset});
        uses += {name.src | /Name name := parseModuleWithSpacesCached(funcCall.top)
                          , isStrictlyContainedIn(name.src, funcCall)
                          , "<name>" == kwAType.alabel};
    }

    return uses;
}

set[loc] rascalGetKeywordFieldUses(WorkspaceInfo ws, set[loc] defs, str cursorName) {
    set[loc] uses = getUses(ws, defs);

    for (d <- defs
       , Define dataDef: <_, _, _, dataId(), _, _> <- ws.defines
       , isStrictlyContainedIn(d, dataDef.defined)
       , Define consDef: <_, _, _, constructorId(), _, _> <- ws.defines
       , isStrictlyContainedIn(consDef.defined, dataDef.defined)
    ) {
        if (AType fieldType := ws.definitions[d].defInfo.atype) {
            // println("Def: <ws.definitions[d]>");
            if (<{}, consUses, _> := rascalGetFieldDefsUses(ws, dataDef.defInfo.atype, fieldType, cursorName)) {
                uses += consUses;
            }
        } else {
            throw unsupportedRename("Unknown type for definition at <d>: <ws.definitions[d].defInfo>");
        }
    }
    return uses;
}

private set[loc] rascalGetExceptUses(WorkspaceInfo ws, set[loc] defs) {

    constructorDefs = {d | l <- defs, Define d: <_, _, _, constructorId(), _, _> := ws.definitions[l]};
    if (constructorDefs == {}) return {};

    // We consider constructor definitions; we need to additionally find any uses at excepts (`!constructor``)
    sortedFacts = [<l, ws.facts[l]> | l <- sort(domain(ws.facts), bool(loc l1, loc l2) {
        // Sort facts by end location (ascending), then by length (ascending)
        return l1.end != l2.end ? l1.end < l2.end : l1.length < l2.length;
    })];

    set[loc] uses = {};
    for (Define d: <_, consName, _, _, _, defType(acons(aadt(aadtName, _, _), _, _))> <- constructorDefs) {
        // Find all neighbouring pairs of facts where an except for `cursorName` exists only in the latter
        for (
            [ _*
            , <l1, at1: !/\a-except(consName)>
            , <l2, at2:  /\a-except(consName)>
            , _*] := sortedFacts
        ) {
            // There might be whitespace before (but not after) the `cursorName`, so we correct the location length
            uses += trim(l2, removePrefix = l2.length - size(consName));
        }
    }

    return uses;
}

DefsUsesRenames rascalGetDefsUses(WorkspaceInfo ws, cursor(use(), l, cursorName), MayOverloadFun mayOverloadF, PathConfig(loc) _) {
    defs = rascalGetOverloadedDefs(ws, getDefs(ws, l), mayOverloadF);
    uses = getUses(ws, defs);

    set[IdRole] roles = {ws.definitions[d].idRole | d <- defs};
    if (keywordFormalId() in roles) {
        uses += rascalGetKeywordFormalUses(ws, defs, cursorName);
        uses += rascalGetKeywordFieldUses(ws, defs, cursorName);
    } else if (constructorId() in roles) {
        uses += rascalGetExceptUses(ws, defs);
    }

    return <defs, uses, NO_RENAMES>;
}

DefsUsesRenames rascalGetDefsUses(WorkspaceInfo ws, cursor(def(), l, cursorName), MayOverloadFun mayOverloadF, PathConfig(loc) _) {
    set[loc] initialUses = getUses(ws, l);
    set[loc] initialDefs = {l} + {*ds | u <- initialUses, ds := getDefs(ws, u)};
    defs = rascalGetOverloadedDefs(ws, initialDefs, mayOverloadF);
    uses = getUses(ws, defs);

    set[IdRole] roles = {ws.definitions[d].idRole | d <- defs};
    if (keywordFormalId() in roles) {
        uses += rascalGetKeywordFormalUses(ws, defs, cursorName);
        uses += rascalGetKeywordFieldUses(ws, defs, cursorName);
    } else if (constructorId() in roles) {
        uses += rascalGetExceptUses(ws, defs);
    }

    return <defs, uses, NO_RENAMES>;
}

DefsUsesRenames rascalGetDefsUses(WorkspaceInfo ws, cursor(typeParam(), cursorLoc, cursorName), MayOverloadFun _, PathConfig(loc) _) {
    set[loc] getFormals(afunc(_, _, _), rel[loc, AType] facts) = {l | <l, f> <- facts, f.alabel != ""};
    set[loc] getFormals(aadt(_, _, _), rel[loc, AType] facts) {
        perName = {<name, l> | <l, f: aparameter(name, _)> <- facts, f.alabel == ""};
        // Per parameter name, keep the top-left-most occurrence
        return mapper(groupRangeByDomain(perName), loc(set[loc] locs) {
            return (getFirstFrom(locs) | l.offset < it.offset ? l : it | l <- locs);
        });
    }

    bool definesTypeParam(Define _: <_, _, _, functionId(), _, defType(dT)>, AType paramType) =
        afunc(_, /paramType, _) := dT;
    bool definesTypeParam(Define _: <_, _, _, nonterminalId(), _, defType(dT)>, AType paramType) =
        aadt(_, /paramType, _) := dT;
    default bool definesTypeParam(Define _, AType _) = false;

    AType cursorType = ws.facts[cursorLoc];

    if(Define containingDef <- ws.defines
     , isContainedIn(cursorLoc, containingDef.defined)
     , definesTypeParam(containingDef, cursorType)) {
        // From here on, we can assume that all locations are in the same file, because we are dealing with type parameters and filtered on `isContainedIn`
        facts = {<l, ws.facts[l]> | l <- ws.facts
                                  , cursorType := ws.facts[l]
                                  , isContainedIn(l, containingDef.defined)};

        formals = getFormals(containingDef.defInfo.atype, facts);

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

    throw unsupportedRename("Cannot find function or nonterminal which defines type parameter \'<cursorName>\'");
}

DefsUsesRenames rascalGetDefsUses(WorkspaceInfo ws, cursor(collectionField(), cursorLoc, cursorName), MayOverloadFun _, PathConfig(loc) _) {
    bool isTupleField(AType fieldType) = fieldType.alabel == "";

    AType cursorType = ws.facts[cursorLoc];
    list[loc] factLocsSortedBySize = sort(domain(ws.facts), byLength);

    if (l <- factLocsSortedBySize, isStrictlyContainedIn(cursorLoc, l), collUseType := ws.facts[l], rascalIsCollectionType(collUseType), collUseType.elemType is atypeList) {
        // We are at a collection definition site
        return rascalGetFieldDefsUses(ws, collUseType, cursorType, cursorName);
    }

    // We can find the collection type by looking for the first use to the left of the cursor that has a collection type
    lrel[loc use, loc def] usesToLeft = reverse(sort({<u, d> | <u, d> <- ws.useDef, isSameFile(u, cursorLoc), u.offset < cursorLoc.offset}));
    if (<_, d> <- usesToLeft, define := ws.definitions[d], defType(AType collDefType) := define.defInfo, rascalIsCollectionType(collDefType)) {
        // We are at a use site, where the field element type is wrapped in a `aset` of `alist` constructor
        return rascalGetFieldDefsUses(ws, collDefType, isTupleField(cursorType) ? cursorType.elmType : cursorType, cursorName);
    } else {
        throw unsupportedRename("Could not find a collection definition corresponding to the field at the cursor.");
    }
}

Maybe[tuple[loc, set[loc]]] rascalGetFieldLocs(str fieldName, (Expression) `<Expression e>.<Name field>`) =
    just(<e.src, {field.src}>) when fieldName == "<field>";

Maybe[tuple[loc, set[loc]]] rascalGetFieldLocs(str fieldName, (Assignable) `<Assignable rec>.<Name field>`) =
    just(<rec.src, {field.src}>) when fieldName == "<field>";

Maybe[tuple[loc, set[loc]]] rascalGetFieldLocs(str fieldName, (Expression) `<Expression e>\< <{Field ","}+ fields> \>`) {
    fieldLocs = {field.src
        | field <- fields
        , field is name
        , "<field.fieldName>" == fieldName
    };

    return fieldLocs != {} ? just(<e.src, fieldLocs>) : nothing();
}

Maybe[tuple[loc, set[loc]]] rascalGetFieldLocs(str fieldName, (Expression) `<Expression e>[<Name field> = <Expression _>]`) =
    just(<e.src, {field.src}>) when fieldName == "<field>";

Maybe[tuple[loc, set[loc]]] rascalGetFieldLocs(str fieldName, (StructuredType) `<BasicType tp>[<{TypeArg ","}+ args>]`) {
    fieldLocs = {name.src | (TypeArg) `<Type _> <Name name>` <- args, fieldName == "<name>"};
    return fieldLocs != {} ? just(<tp.src, fieldLocs>) : nothing();
}

default Maybe[tuple[loc, set[loc]]] rascalGetFieldLocs(str fieldName, Tree _) = nothing();

private DefsUsesRenames rascalGetFieldDefsUses(WorkspaceInfo ws, AType containerType, AType fieldType, str cursorName) {
    set[loc] containerFacts = factsInvert(ws)[containerType];
    rel[loc file, loc u] factsByModule = groupBy(containerFacts, loc(loc l) { return l.top; });

    set[loc] defs = {};
    set[loc] uses = {};
    for (file <- factsByModule.file) {
        fileFacts = factsByModule[file];
        for (/Tree t := parseModuleWithSpacesCached(file), just(<lhs, fields>) := rascalGetFieldLocs(cursorName, t)) {
            if ((StructuredType) `<BasicType _>[<{TypeArg ","}+ _>]` := t
              , at := ws.facts[t.src]
              , containerType.elemType?
              , containerType.elemType == at.elemType) {
                defs += {f | f <- fields, fieldType == ws.facts[f]};
            } else if (any(f <- fileFacts, isContainedIn(f, lhs))) {
                uses += fields;
            }
        }
    }

    return <defs, uses, NO_RENAMES>;
}

DefsUsesRenames rascalGetDefsUses(WorkspaceInfo ws, cursor(moduleName(), cursorLoc, cursorName), MayOverloadFun _, PathConfig(loc) getPathConfig) {
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
