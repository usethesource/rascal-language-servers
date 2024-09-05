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

data CursorKind
    = use()
    | def()
    | typeParam()
    | collectionField()
    | dataField(loc dataTypeDef, AType fieldType)
    | dataKeywordField(loc dataTypeDef, AType fieldType)
    | dataCommonKeywordField(loc dataTypeDef, AType fieldType)
    | keywordParam()
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
    PathConfig(loc) getPathConfig,
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

    return (moduleI + imports)  // 0 or 1 imports
         o (moduleI + extends+) // 0 or more extends
         ;
}

@memo{maximumSize=1, minutes=5}
rel[loc from, loc to] rascalGetTransitiveReflexiveScopes(WorkspaceInfo ws) = toRel(ws.scopes)*;

@memo{maximumSize=10, minutes=5}
set[loc] rascalReachableModules(WorkspaceInfo ws, set[loc] froms) {
    rel[loc from, loc scope] fromScopes = {};
    for (from <- froms) {
        if (scope <- ws.scopes<1>, isContainedIn(from, scope)) {
            fromScopes += <from, scope>;
        }
    }
    rel[loc from, loc modScope] reachable =
        fromScopes
      o rascalGetTransitiveReflexiveScopes(ws)
      o rascalGetTransitiveReflexiveModulePaths(ws);

    return {s.top | s <- reachable.modScope};
}

@memo{maximumSize=1, minutes=5}
rel[loc, Define] definitionsRel(WorkspaceInfo ws) = toRel(ws.definitions);

set[Define] rascalReachableDefs(WorkspaceInfo ws, set[loc] defs) {
    rel[loc from, loc to] modulePaths = rascalGetTransitiveReflexiveModulePaths(ws);
    rel[loc from, loc to] scopes = rascalGetTransitiveReflexiveScopes(ws);
    rel[loc from, Define define] reachableDefs =
        (ws.defines<defined, defined, scope>)[defs] // <definition, scope> pairs
      o scopes                                      // All scopes surrounding defs
      o modulePaths                                 // Transitive-reflexive paths from scope to reachable modules
      o ws.defines<scope, defined>                  // Definitions in these scopes
      o definitionsRel(ws);                         // Full define tuples
    return reachableDefs.define;                    // We are only interested in reached defines; not *from where* they were reached
}

set[loc] rascalGetOverloadedDefs(WorkspaceInfo ws, set[loc] defs, MayOverloadFun mayOverloadF) {
    if (defs == {}) return {};
    set[loc] overloadedDefs = defs;

    set[IdRole] roles = definitionsRel(ws)[defs].idRole;

    // Pre-conditions
    assert size(roles) == 1:
        "Initial defs are of different roles!";
    assert mayOverloadF(overloadedDefs, ws.definitions):
        "Initial defs are invalid overloads!";

    IdRole role = getFirstFrom(roles);
    map[loc file, loc scope] moduleScopePerFile = getModuleScopePerFile(ws);
    rel[loc def, loc scope] defUseScopes = {<d, moduleScopePerFile[u.top]> | <loc u, loc d> <- ws.useDef};
    rel[loc from, loc to] modulePaths = rascalGetTransitiveReflexiveModulePaths(ws);
    rel[loc def, loc scope] defScopes = ws.defines<defined, scope>+;
    rel[loc scope, loc defined] scopeDefs =
        (ws.defines<scope, defined>)+                  // Follow definition scopes ...
      o ((ws.defines<idRole, defined, defined>)[role]) // Until we arrive at a definition with the same role ...
      ;

    rel[loc from, loc to] fromDefPaths =
        (defScopes + defUseScopes) // 1. Look up scopes of defs and scopes of their uses
      o modulePaths                // 2. Follow import/extend relations to reachable scopes
      o scopeDefs                  // 3. Find definitions in the reached scope, and definitions within those definitions (transitively)
      ;

    rel[loc from, loc to] defPaths = fromDefPaths + invert(fromDefPaths);

    solve(overloadedDefs) {
        rel[loc from, loc to] reachableDefs = ident(overloadedDefs) o defPaths;

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
bool rascalIsConstructorType(AType at) = at is acons;
bool rascalIsDataType(AType at) = at is aadt;

bool rascalMayOverloadSameName(set[loc] defs, map[loc, Define] definitions) {
    if (l <- defs, !definitions[l]?) return false;
    set[str] names = {definitions[l].id | l <- defs};
    if (size(names) > 1) return false;

    map[loc, Define] potentialOverloadDefinitions = (l: d | l <- definitions, d := definitions[l], d.id in names);
    return rascalMayOverload(defs, potentialOverloadDefinitions);
}

set[Define] rascalGetADTDefinitions(WorkspaceInfo ws, loc lhs) {
    set[loc] fromDefs = (ws.definitions[lhs]? || lhs in ws.useDef<1>)
        ? {lhs}
        : getDefs(ws, lhs)
        ;

    AType lhsType = ws.facts[lhs];
    if (rascalIsConstructorType(lhsType)) {
        return {adt
            | loc cD <- rascalGetOverloadedDefs(ws, fromDefs, rascalMayOverloadSameName)
            , Define cons: <_, _, _, constructorId(), _, _> := ws.definitions[cD]
            , AType consAdtType := cons.defInfo.atype.adt
            , Define adt: <_, _, _, dataId(), _, defType(consAdtType)> <- rascalReachableDefs(ws, {cons.defined})
        };
    } else if (rascalIsDataType(lhsType)) {
        return {adt
            | set[loc] overloads := rascalGetOverloadedDefs(ws, fromDefs, rascalMayOverloadSameName)
            , Define adt: <_, _, _, dataId(), _, defType(lhsType)> <- rascalReachableDefs(ws, overloads)
        };
    }

    return {};
}

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

set[loc] rascalGetKeywordFormals((KeywordFormals) ``, str _) = {};
set[loc] rascalGetKeywordFormals((KeywordFormals) `<OptionalComma _> <{KeywordFormal ","}+ keywordFormals>`, str cursorName) =
    rascalGetKeywordFormalList(keywordFormals, cursorName);

set[loc] rascalGetKeywordFormalList([{KeywordFormal ","}+] keywordFormals, str cursorName) =
    { kwFormal.name.src
    | kwFormal <- keywordFormals
    , "<kwFormal.name>" == cursorName};

set[loc] rascalGetKeywordArgs(none(), str _) = {};
set[loc] rascalGetKeywordArgs(\default(_, keywordArgs), str cursorName) =
    { kwArg.name.src
    | kwArg <- keywordArgs
    , "<kwArg.name>" == cursorName};

set[loc] rascalGetKeywordFieldUses(WorkspaceInfo ws, set[loc] defs, str cursorName) {
    set[loc] uses = getUses(ws, defs);

    set[Define] reachableDefs = rascalReachableDefs(ws, defs);

    for (d <- defs
       , Define dataDef: <_, _, _, dataId(), _, _> <- reachableDefs
       , isStrictlyContainedIn(d, dataDef.defined)
       , Define consDef: <_, _, _, constructorId(), _, _> <- reachableDefs
       , isStrictlyContainedIn(consDef.defined, dataDef.defined)
    ) {
        if (AType fieldType := ws.definitions[d].defInfo.atype) {
            set[loc] reachableModules = rascalReachableModules(ws, {d});
            if (<{}, consUses, _> := rascalGetFieldDefsUses(ws, reachableModules, dataDef.defInfo.atype, fieldType, cursorName)) {
                uses += consUses;
            }
        } else {
            throw unsupportedRename("Unknown type for definition at <d>: <ws.definitions[d].defInfo>");
        }
    }

    return uses;
}

DefsUsesRenames rascalGetDefsUses(WorkspaceInfo ws, cursor(use(), l, cursorName), MayOverloadFun mayOverloadF) {
    defs = rascalGetOverloadedDefs(ws, getDefs(ws, l), mayOverloadF);
    uses = getUses(ws, defs);

    if (keywordFormalId() in {ws.definitions[d].idRole | d <- defs}) {
        uses += rascalGetKeywordFormalUses(ws, defs, cursorName);
        uses += rascalGetKeywordFieldUses(ws, defs, cursorName);
    }

    return <defs, uses, NO_RENAMES>;
}

DefsUsesRenames rascalGetDefsUses(WorkspaceInfo ws, cursor(def(), l, cursorName), MayOverloadFun mayOverloadF) {
    set[loc] initialUses = getUses(ws, l);
    set[loc] initialDefs = {l} + {*ds | u <- initialUses, ds := getDefs(ws, u)};
    defs = rascalGetOverloadedDefs(ws, initialDefs, mayOverloadF);
    uses = getUses(ws, defs);

    if (keywordFormalId() in {ws.definitions[d].idRole | d <- defs}) {
        uses += rascalGetKeywordFormalUses(ws, defs, cursorName);
        uses += rascalGetKeywordFieldUses(ws, defs, cursorName);
    }

    return <defs, uses, NO_RENAMES>;
}

DefsUsesRenames rascalGetDefsUses(WorkspaceInfo ws, cursor(typeParam(), cursorLoc, cursorName), MayOverloadFun _) {
    AType at = ws.facts[cursorLoc];
    set[loc] defs = {};
    set[loc] useDefs = {};
    for (Define d: <_, _, _, _, _, defType(afunc(_, /at, _))> <- ws.defines, isContainedIn(cursorLoc, d.defined)) {
        // From here on, we can assume that all locations are in the same file, because we are dealing with type parameters and filtered on `isContainedIn`
        facts = {<l, ws.facts[l]> | l <- ws.facts
                                  , at := ws.facts[l]
                                  , isContainedIn(l, d.defined)};

        formals = {l | <l, f> <- facts, f.alabel != ""};

        // Given the location/offset of `&T`, find the location/offset of `T`
        offsets = sort({l.offset | l <- facts<0>});
        nextOffsets = toMapUnique(zip2(prefix(offsets), tail(offsets)));

        loc sentinel = |unknown:///|(0, 0, <0, 0>, <0, 0>);
        defs += {min([f | f <- facts<0>, f.offset == nextOffset]) | formal <- formals, nextOffsets[formal.offset]?, nextOffset := nextOffsets[formal.offset]};

        useDefs += {trim(l, removePrefix = l.length - size(cursorName))
                    | l <- facts<0>
                    , !ws.definitions[l]? // If there is a definition at this location, this is a formal argument name
                    , !any(ud <- ws.useDef[l], ws.definitions[ud]?) // If there is a definition for the use at this location, this is a use of a formal argument
                    , !any(flInner <- facts<0>, isStrictlyContainedIn(flInner, l)) // Filter out any facts that contain other facts
        };


    }

    return <defs, useDefs - defs, NO_RENAMES>;
}

DefsUsesRenames rascalGetDefsUses(WorkspaceInfo ws, cursor(dataField(loc adtLoc, AType fieldType), cursorLoc, cursorName), MayOverloadFun mayOverloadF) {
    set[loc] initialDefs = {};
    if (cursorLoc in ws.useDef<0>) {
        initialDefs = getDefs(ws, cursorLoc);
    } else if (cursorLoc in ws.defines<defined>) {
        initialDefs = {cursorLoc};
    } else if (just(AType adtType) := getFact(ws, cursorLoc)) {
        set[Define] reachableDefs = rascalReachableDefs(ws, {cursorLoc});
        initialDefs = {
            kwDef.defined
            | Define dataDef: <_, _, _, dataId(), _, defType(adtType)> <- rascalGetADTDefinitions(ws, cursorLoc)
            , Define kwDef: <_, _, cursorName, keywordFormalId(), _, _> <- reachableDefs
            , isStrictlyContainedIn(kwDef.defined, dataDef.defined)
        };
    } else {
        throw unsupportedRename("Cannot rename data field \'<cursorName>\' from <cursorLoc>");
    }

    set[loc] defs = rascalGetOverloadedDefs(ws, initialDefs, mayOverloadF);
    set[loc] uses = getUses(ws, defs) + rascalGetKeywordFieldUses(ws, defs, cursorName);

    return <defs, uses, NO_RENAMES>;
}

bool debug = false;

DefsUsesRenames rascalGetDefsUses(WorkspaceInfo ws, cursor(cursorKind, cursorLoc, cursorName), MayOverloadFun mayOverloadF) {
    if (cursorKind is dataKeywordField || cursorKind is dataCommonKeywordField) {
        set[loc] defs = {};
        set[loc] uses = {};

        set[loc] adtDefs = rascalGetOverloadedDefs(ws, {cursorKind.dataTypeDef}, mayOverloadF);
        set[Define] reachableDefs = rascalReachableDefs(ws, adtDefs);
        set[loc] reachableModules = rascalReachableModules(ws, reachableDefs.defined);

        for (Define _:<_, _, _, constructorId(), _, defType(AType consType)> <- reachableDefs) {
            <ds, us, _> = rascalGetFieldDefsUses(ws, reachableModules, consType, cursorKind.fieldType, cursorName);
            defs += ds;
            uses += us;
        }
        for (Define _:<_, _, _, IdRole idRole, _, defType(acons(AType dataType, _, _))> <- reachableDefs
           , idRole != dataId()) {
            <ds, us, _> += rascalGetFieldDefsUses(ws, reachableModules, dataType, cursorKind.fieldType, cursorName);
            defs += ds;
            uses += us;
        }

        return <defs, uses, NO_RENAMES>;
    }

    fail;
}

DefsUsesRenames rascalGetDefsUses(WorkspaceInfo ws, cursor(collectionField(), cursorLoc, cursorName), MayOverloadFun _) {
    bool isTupleField(AType fieldType) = fieldType.alabel == "";

    lrel[loc, AType] factsBySize = sort(toRel(ws.facts), isShorterTuple);
    AType cursorType = avoid();

    if (ws.facts[cursorLoc]?) {
        cursorType = ws.facts[cursorLoc];
    } else if (just(loc fieldAccess) := findSmallestContaining(ws.facts<0>, cursorLoc)
             , just(AType collectionType) := getFact(ws, fieldAccess)) {
        cursorType = collectionType;
    }

    if (<l, collUseType> <- factsBySize
      , isStrictlyContainedIn(cursorLoc, l)
      , rascalIsCollectionType(collUseType)
      , collUseType.elemType is atypeList) {
        // We are at a collection definition site
        return rascalGetFieldDefsUses(ws, rascalReachableModules(ws, {cursorLoc}), collUseType, cursorType, cursorName);
    }

    // We can find the collection type by looking for the first use to the left of the cursor that has a collection type
    lrel[loc use, loc def] usesToLeft = reverse(sort({<u, d> | <u, d> <- ws.useDef, isSameFile(u, cursorLoc), u.offset < cursorLoc.offset}));
    if (<_, d> <- usesToLeft, define := ws.definitions[d], defType(AType collDefType) := define.defInfo, rascalIsCollectionType(collDefType)) {
        // We are at a use site, where the field element type is wrapped in a `aset` of `alist` constructor
        return rascalGetFieldDefsUses(ws, rascalReachableModules(ws, {define.defined}), collDefType, isTupleField(cursorType) ? cursorType.elmType : cursorType, cursorName);
    } else {
        throw unsupportedRename("Could not find a collection definition corresponding to the field at the cursor.");
    }
}

Maybe[tuple[loc, set[loc], bool]] rascalGetFieldLocs(str fieldName, (Expression) `<Expression e>.<Name field>`) =
    just(<e.src, {field.src}, false>) when fieldName == "<field>";

Maybe[tuple[loc, set[loc], bool]] rascalGetFieldLocs(str fieldName, (Assignable) `<Assignable rec>.<Name field>`) =
    just(<rec.src, {field.src}, false>) when fieldName == "<field>";

Maybe[tuple[loc, set[loc], bool]] rascalGetFieldLocs(str fieldName, (Expression) `<Expression e>\< <{Field ","}+ fields> \>`) {
    fieldLocs = {field.src
        | field <- fields
        , field is name
        , "<field.fieldName>" == fieldName
    };

    return fieldLocs != {} ? just(<e.src, fieldLocs, false>) : nothing();
}

Maybe[tuple[loc, set[loc], bool]] rascalGetFieldLocs(str fieldName, (Expression) `<Expression e>[<Name field> = <Expression _>]`) =
    just(<e.src, {field.src}, false>) when fieldName == "<field>";

Maybe[tuple[loc, set[loc], bool]] rascalGetFieldLocs(str fieldName, (StructuredType) `<BasicType tp>[<{TypeArg ","}+ args>]`) {
    fieldLocs = {name.src | (TypeArg) `<Type _> <Name name>` <- args, fieldName == "<name>"};
    return fieldLocs != {} ? just(<tp.src, fieldLocs, true>) : nothing();
}

default Maybe[tuple[loc, set[loc], bool]] rascalGetFieldLocs(str fieldName, Tree _) = nothing();

Maybe[tuple[loc, set[loc], bool]] rascalGetKeywordLocs(str fieldName, (Expression) `<Expression e>(<{Expression ","}* _> <KeywordArguments[Expression] kwArgs>)`) =
    just(<e.src, rascalGetKeywordArgs(kwArgs, fieldName), false>);


Maybe[tuple[loc, set[loc], bool]] rascalGetKeywordLocs(str fieldName, (Pattern) `<Pattern p>(<{Pattern ","}* _> <KeywordArguments[Pattern] kwArgs>)`) =
    just(<p.src, rascalGetKeywordArgs(kwArgs, fieldName), false>);

Maybe[tuple[loc, set[loc], bool]] rascalGetKeywordLocs(str fieldName, (Variant) `<Name name>(<{TypeArg ","}* _> <KeywordFormals kwFormals>)`) =
    just(<name.src, rascalGetKeywordFormals(kwFormals, fieldName), true>);

Maybe[tuple[loc, set[loc], bool]] rascalGetKeywordLocs(str fieldName, d:(Declaration) `<Tags _> <Visibility _> data <UserType ut>(<{KeywordFormal ","}+ kwFormalList>) = <{Variant "|"}+ _>;`) =
    just(<d.src, rascalGetKeywordFormalList(kwFormalList, fieldName), true>);

Maybe[tuple[loc, set[loc], bool]] rascalGetKeywordLocs(str fieldName, d:(Declaration) `<Tags _> <Visibility _> data <UserType ut>(<{KeywordFormal ","}+ kwFormalList>);`) =
    just(<d.src, rascalGetKeywordFormalList(kwFormalList, fieldName), true>);

default Maybe[tuple[loc, set[loc], bool]] rascalGetKeywordLocs(str _, Tree _) = nothing();

private DefsUsesRenames rascalGetFieldDefsUses(WorkspaceInfo ws, set[loc] reachableModules, AType containerType, AType fieldType, str cursorName) {
    set[loc] containerFacts = {f | f <- factsInvert(ws)[containerType], f.top in reachableModules};
    rel[loc file, loc u] factsByModule = groupBy(containerFacts, loc(loc l) { return l.top; });

    set[loc] defs = {};
    set[loc] uses = {};
    for (file <- factsByModule.file) {
        fileFacts = factsByModule[file];
        for (/Tree t := parseModuleWithSpacesCached(file)
           , just(<lhs, fields, isDef>) := rascalGetFieldLocs(cursorName, t) || just(<lhs, fields, isDef>) := rascalGetKeywordLocs(cursorName, t)) {
            if((StructuredType) `<BasicType _>[<{TypeArg ","}+ _>]` := t) {
                if(at := ws.facts[t.src]
                 , containerType.elemType?
                 , containerType.elemType == at.elemType) {
                    defs += {f | f <- fields, just(fieldType) := getFact(ws, f)};
                }
            } else if (isDef) {
                defs += fields;
            } else if (any(f <- fileFacts, isContainedIn(f, lhs))) {
                uses += fields;
            }
        }
    }

    return <defs, uses, NO_RENAMES>;
}

DefsUsesRenames rascalGetDefsUses(WorkspaceInfo ws, cursor(moduleName(), cursorLoc, cursorName), MayOverloadFun _) {
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

    modName = getModuleName(moduleFile, ws.getPathConfig(getProjectFolder(ws, moduleFile)));

    defs = {parseModuleWithSpacesCached(moduleFile).top.header.name.names[-1].src};

    imports = {u | u <- ws.useDef<0>, amodule(modName) := ws.facts[u]};
    qualifiedUses = {
        // We compute the location of the module name in the qualified name at `u`
        // some::qualified::path::to::Foo::SomeVar
        // \____________________________/\/\_____/
        // moduleNameSize ^  qualSepSize ^   ^ idSize
        trim(u, removePrefix = moduleNameSize - size(cursorName)
              , removeSuffix = idSize + qualSepSize)
        | <loc u, Define d> <- ws.useDef o definitionsRel(ws)
        , idSize := size(d.id)
        , u.length > idSize // There might be a qualified prefix
        , moduleNameSize := size(modName)
        , u.length == moduleNameSize + qualSepSize + idSize
    };
    uses = imports + qualifiedUses;

    rel[loc, loc] getRenames(str newName) = {<file, file[file = "<newName>.rsc"]> | d <- defs, file := d.top};

    return <defs, uses, getRenames>;
}
