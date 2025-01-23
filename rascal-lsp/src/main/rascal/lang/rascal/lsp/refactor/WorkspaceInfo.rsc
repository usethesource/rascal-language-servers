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

import lang::rascalcore::check::ATypeUtils;
import lang::rascalcore::check::Checker;

import lang::rascal::\syntax::Rascal;

import util::Maybe;
import util::Reflective;

import lang::rascal::lsp::refactor::Exception;
import lang::rascal::lsp::refactor::TextEdits;
import lang::rascal::lsp::refactor::Util;

import IO;
import List;
import Location;
import Map;
import ParseTree;
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
    | exceptConstructor()
    ;

data Cursor
    = cursor(CursorKind kind, loc l, str name)
    ;

alias MayOverloadFun = bool(set[loc] defs, map[loc, Define] defines);
alias FileRenamesF = rel[loc old, loc new](str newName);
alias RenameLocation = tuple[loc l, Maybe[ChangeAnnotationId] annotation];
alias DefsUsesRenames = tuple[set[RenameLocation] defs, set[RenameLocation] uses, FileRenamesF renames];
alias ProjectFiles = rel[loc projectFolder, bool loadModel, loc file];

// Extend the TModel to include some workspace information.
data TModel (
    set[loc] projects = {},
    set[loc] sourceFiles = {}
);

set[RenameLocation] annotateLocs(set[loc] locs, Maybe[ChangeAnnotationId] annotationId = nothing()) = {<l, annotationId> | l <- locs};

TModel loadLocs(TModel wsTM, ProjectFiles projectFiles, set[TModel](ProjectFiles projectFiles) tmodelsForFiles) {
    for (modTM <- tmodelsForFiles(projectFiles)) {
        wsTM = appendTModel(wsTM, modTM);
    }

    // In addition to data from the TModel, we keep track of which projects/modules we loaded.
    wsTM.sourceFiles += projectFiles.file;
    wsTM.projects += projectFiles.projectFolder;

    return wsTM;
}

TModel appendTModel(TModel to, TModel from) {
    try {
        throwAnyErrors(from);
    } catch set[Message] errors: {
        throw unsupportedRename("Cannot rename: some files in workspace have errors.\n<toString(errors)>", issues={<(error.at ? |unknown:///|), error.msg> | error <- errors});
    }

    to.useDef      += from.useDef;
    to.defines     += from.defines;
    to.definitions += from.definitions;
    to.facts       += from.facts;
    to.scopes      += from.scopes;
    to.paths       += from.paths;

    return to;
}

loc getProjectFolder(TModel ws, loc l) {
    if (project <- ws.projects, isPrefixOf(project, l)) {
        return project;
    }

    throw "Could not find project containing <l>";
}

@memo{maximumSize(1), expireAfter(minutes=5)}
rel[loc, loc] defUse(TModel ws) = invert(ws.useDef);

@memo{maximumSize(1), expireAfter(minutes=5)}
map[AType, set[loc]] factsInvert(TModel ws) = invert(ws.facts);

set[loc] getUses(TModel ws, loc def) = defUse(ws)[def];

set[loc] getUses(TModel ws, set[loc] defs) = defUse(ws)[defs];

set[loc] getDefs(TModel ws, loc use) = ws.useDef[use];

Maybe[AType] getFact(TModel ws, loc l) = l in ws.facts ? just(ws.facts[l]) : nothing();

@memo{maximumSize(1), expireAfter(minutes=5)}
set[loc] getModuleScopes(TModel ws) = invert(ws.scopes)[|global-scope:///|];

@memo{maximumSize(1), expireAfter(minutes=5)}
map[loc, loc] getModuleScopePerFile(TModel ws) = (scope.top: scope | loc scope <- getModuleScopes(ws));

@memo{maximumSize(1), expireAfter(minutes=5)}
rel[loc from, loc to] rascalGetTransitiveReflexiveModulePaths(TModel ws) {
    rel[loc from, loc to] moduleI = ident(getModuleScopes(ws));
    rel[loc from, loc to] imports = (ws.paths<pathRole, from, to>)[importPath()];
    rel[loc from, loc to] extends = (ws.paths<pathRole, from, to>)[extendPath()];

    return (moduleI + imports)  // 0 or 1 imports
         o (moduleI + extends+) // 0 or more extends
         ;
}

@memo{maximumSize(1), expireAfter(minutes=5)}
rel[loc from, loc to] rascalGetTransitiveReflexiveScopes(TModel ws) = toRel(ws.scopes)*;

@memo{maximumSize(10), expireAfter(minutes=5)}
set[loc] rascalReachableModules(TModel ws, set[loc] froms) {
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

@memo{maximumSize(1), expireAfter(minutes=5)}
rel[loc, Define] definitionsRel(TModel ws) = toRel(ws.definitions);

set[Define] rascalReachableDefs(TModel ws, set[loc] defs) {
    rel[loc from, loc to] modulePaths = rascalGetTransitiveReflexiveModulePaths(ws);
    rel[loc from, loc to] scopes = rascalGetTransitiveReflexiveScopes(ws);
    rel[loc from, Define define] reachableDefs =
        ((ws.defines<defined, defined, scope>)[defs]             // <definition, scope> pairs
        + (ws.defines<scope, defined, scope>)[defs])
      o (
         (scopes                                                // All scopes surrounding defs
        o modulePaths                                           // Transitive-reflexive paths from scope to reachable modules
        ) + (                                                   // In ADT and syntax definitions, search inwards to find field scopes
            (ws.defines<idRole, scope, defined>)[{dataId(), nonterminalId(), lexicalId()}]
          + ws.defines<scope, scope>
        )
      )
      o ws.defines<scope, defined>                              // Definitions in these scopes
      o definitionsRel(ws);                                     // Full define tuples
    return reachableDefs.define;                                // We are only interested in reached defines; not *from where* they were reached
}

set[loc] rascalGetOverloadedDefs(TModel ws, set[loc] defs, MayOverloadFun mayOverloadF) {
    if (defs == {}) return {};

    set[Define] overloadedDefs = definitionsRel(ws)[defs];
    set[IdRole] roles = overloadedDefs.idRole;

    // Pre-conditions
    assert size(roles) == 1:
        "Initial defs are of different roles!";
    assert mayOverloadF(defs, ws.definitions):
        "Initial defs are invalid overloads!";

    IdRole role = getFirstFrom(roles);
    map[loc file, loc scope] moduleScopePerFile = getModuleScopePerFile(ws);
    rel[loc def, loc scope] defUseScopes = {<d, moduleScopePerFile[u.top]> | <loc u, loc d> <- ws.useDef};
    rel[loc fromScope, loc toScope] modulePaths = rascalGetTransitiveReflexiveModulePaths(ws);

    rel[loc def, loc moduleScope] defPathStep =
        (ws.defines<defined, scope>+ + defUseScopes) // 1. Look up scopes of defs and scopes of their uses
        o (modulePaths + invert(modulePaths))        // 2. Follow import/extend relations to reachable scopes
        ;

    rel[loc fromDef, loc toDef] defPaths = {};
    set[loc] reachableDefs = rascalReachableDefs(ws, overloadedDefs.defined).defined;

    solve(overloadedDefs) {
        if (constructorId() := role) {
            set[AType] adtTypes = {adtType | defType(acons(AType adtType, _, _)) <- overloadedDefs.defInfo};
            set[loc] initialADTs = {
                adtDef
                | Define _: <_, _, _, dataId(), loc adtDef, defType(AType adtType)> <- rascalReachableDefs(ws, overloadedDefs.defined)
                , adtType in adtTypes
            };
            set[loc] selectedADTs = rascalGetOverloadedDefs(ws, initialADTs, mayOverloadF);

            // Any constructor definition of the right type where any `selectedADTs` element is in the reachable defs
            rel[loc scope, loc def] selectedConstructors = {<s, d>
                | <s, d, defType(acons(AType adtType, _, _))> <- (ws.defines<idRole, scope, defined, defInfo>)[role]
                , adtType in adtTypes
                , any(<_, _, _, dataId(), loc r, _> <- rascalReachableDefs(ws, {d}), r in selectedADTs)
            };

            // We transitively resolve module scopes via modules that have a relevant constructor/ADTs use or definition
            rel[loc scope, loc def] selectedDefs = selectedConstructors + (ws.defines<defined, scope, defined>)[selectedADTs];
            rel[loc fromScope, loc toScope] constructorStep = (selectedDefs + invert(defUseScopes)) o defPathStep;

            defPathStep = defPathStep /* <def, scope> */
                        + (defPathStep /* <def, scope> */ o constructorStep+ /* <scope, scope> */) /* <def, scope> */;

            defPaths = defPathStep /* <def, scope> */ o selectedConstructors /* <scope, def> */;
        } else if (dataId() := role) {
            set[AType] adtTypes = {adtType | defType(AType adtType) <- overloadedDefs.defInfo};
            set[loc] constructorDefs = {d
                | <defType(acons(AType adtType, _, _)), d> <- (rascalReachableDefs(ws, overloadedDefs.defined)<idRole, defInfo, defined>)[constructorId()]
                , adtType in adtTypes
                , any(dd <- overloadedDefs.defined, isStrictlyContainedIn(d, dd))
            };

            set[Define] defsReachableFromOverloads = rascalReachableDefs(ws, overloadedDefs.defined + defUseScopes[overloadedDefs.defined]);
            set[Define] defsReachableFromOverloadConstructors = rascalReachableDefs(ws, constructorDefs + defUseScopes[constructorDefs]);

            rel[loc scope, loc def] selectedADTs = {
                <s, d>
                | <s, d, defType(AType adtType)> <- ((defsReachableFromOverloads + defsReachableFromOverloadConstructors)<idRole, scope, defined, defInfo>)[role]
                , adtType in adtTypes
            };

            rel[loc fromScope, loc toScope] adtStep = (selectedADTs + invert(defUseScopes)) o defPathStep;
            defPathStep = defPathStep
                        + (defPathStep o adtStep+);
            defPaths = defPathStep o selectedADTs;
        } else if (fieldId() := role) {
            // We are looking for fields for the same ADT type (but not necessarily same constructor type)
            set[DefInfo] selectedADTTypes = (ws.defines<defined, defInfo>)[overloadedDefs.scope];
            rel[loc, loc] selectedADTs = (ws.defines<defInfo, scope, defined>)[selectedADTTypes];
            rel[loc, loc] selectedFields = selectedADTs o ws.defines<scope, defined>;
            defPaths = defPathStep o selectedFields;
        } else {
            // Find definitions in the reached scope, and definitions within those definitions (transitively)
            defPaths = defPathStep o (ws.defines<idRole, scope, defined>)[role]+;
        }

        set[loc] overloadCandidates = defPaths[overloadedDefs.defined];
        overloadedDefs += {ws.definitions[d]
            | loc d <- overloadCandidates
            , mayOverloadF(overloadedDefs.defined + d, ws.definitions)
        };
        reachableDefs = rascalReachableDefs(ws, overloadedDefs.defined).defined;
    }

    return overloadedDefs.defined;
}

private rel[loc, loc] NO_RENAMES(str _) = {};

bool rascalIsCollectionType(AType at) = at is arel || at is alrel || at is atuple;
bool rascalIsConstructorType(AType at) = at is acons;
bool rascalIsDataType(AType at) = at is aadt;

bool rascalIsDataTypeLike(dataId()) = true;
bool rascalIsDataTypeLike(nonterminalId()) = true;
bool rascalIsDataTypeLike(lexicalId()) = true;
default bool rascalIsDataTypeLike(IdRole _) = false;

bool rascalMayOverloadSameName(set[loc] defs, map[loc, Define] definitions) {
    if (l <- defs, !definitions[l]?) return false;
    set[Define] defines = {definitions[d] | d <- defs};

    if (size(defines.id) > 1) return false;
    if ({IdRole role} := defines.idRole) {
        return rascalMayOverload(defs, definitions);
    }
    return false;
}

set[Define] rascalGetADTDefinitions(TModel ws, loc lhs) {
    set[loc] fromDefs = (ws.definitions[lhs]? || lhs in ws.useDef<1>)
        ? {lhs}
        : getDefs(ws, lhs)
        ;

    if ({AType lhsType} := toRel(ws.facts)[fromDefs]) {
        if (rascalIsConstructorType(lhsType)) {
            return {adt
                | loc cD <- rascalGetOverloadedDefs(ws, fromDefs, rascalMayOverloadSameName)
                , Define cons: <_, _, _, constructorId(), _, _> := ws.definitions[cD]
                , AType consAdtType := cons.defInfo.atype.adt
                , Define adt: <_, _, _, _, _, defType(consAdtType)> <- rascalReachableDefs(ws, {cons.defined})
                , rascalIsDataTypeLike(adt.idRole)
            };
        } else if (rascalIsDataType(lhsType)) {
            return {adt
                | set[loc] overloads := rascalGetOverloadedDefs(ws, fromDefs, rascalMayOverloadSameName)
                , Define adt: <_, _, _, _, _, defType(lhsType)> <- rascalReachableDefs(ws, overloads)
                , rascalIsDataTypeLike(adt.idRole)
            };
        }
    }

    return {};
}

set[RenameLocation] rascalGetKeywordFormalUses(TModel ws, set[loc] defs, str cursorName) {
    set[RenameLocation] uses = {};

    for (d <- defs
       , f <- ws.facts
       , isStrictlyContainedIn(d, f)
       , afunc(retType, _, [*_, kwField(kwAType, cursorName, _), *_]) := ws.facts[f]
       , funcName <- getUses(ws, f)
       ) {
        loc funcCall = min({l | l <- factsInvert(ws)[retType], l.offset == funcName.offset});
        uses += {<name.src, nothing()> | /Name name := parseModuleWithSpacesCached(funcCall.top)
                          , isStrictlyContainedIn(name.src, funcCall)
                          , "<name>" == kwAType.alabel};
    }

    return uses;
}

set[loc] rascalGetKeywordFormals((KeywordFormals) ``, str _) = {};
set[loc] rascalGetKeywordFormals((KeywordFormals) `<OptionalComma _> <{KeywordFormal ","}+ keywordFormals>`, str cursorName) =
    rascalGetKeywordFormalList(keywordFormals, cursorName);

set[loc] rascalGetKeywordFormalList({KeywordFormal ","}+ keywordFormals, str cursorName) =
    { kwFormal.name.src
    | kwFormal <- keywordFormals
    , "<kwFormal.name>" == cursorName};

set[loc] rascalGetKeywordArgs(none(), str _) = {};
set[loc] rascalGetKeywordArgs(\default(_, {KeywordArgument[Expression] ","}+ keywordArgs), str cursorName) =
    { kwArg.name.src
    | kwArg <- keywordArgs
    , "<kwArg.name>" == cursorName};
set[loc] rascalGetKeywordArgs(\default(_, {KeywordArgument[Pattern] ","}+ keywordArgs), str cursorName) =
    { kwArg.name.src
    | kwArg <- keywordArgs
    , "<kwArg.name>" == cursorName};

set[RenameLocation] rascalGetKeywordFieldUses(TModel ws, set[loc] defs, str cursorName) {
    set[RenameLocation] uses = annotateLocs(getUses(ws, defs));

    set[Define] reachableDefs = rascalReachableDefs(ws, defs);

    for (d <- defs
       , Define dataDef <- reachableDefs
       , rascalIsDataTypeLike(dataDef.idRole)
       , isStrictlyContainedIn(d, dataDef.defined)
       , Define consDef: <_, _, _, constructorId(), _, _> <- reachableDefs
       , isStrictlyContainedIn(consDef.defined, dataDef.defined)
    ) {
        if (AType fieldType := ws.definitions[d].defInfo.atype) {
            if (<{}, consUses, _> := rascalGetFieldDefsUses(ws, d, dataDef.defInfo.atype, fieldType, cursorName)) {
                uses += consUses;
            }
        } else {
            throw unsupportedRename("Unknown type for definition at <d>: <ws.definitions[d].defInfo>");
        }
    }
    return uses;
}

private set[RenameLocation] rascalGetExceptUses(TModel ws, set[loc] defs) {
    constructorDefs = {d | l <- defs, Define d: <_, _, _, constructorId(), _, _> := ws.definitions[l]};
    if (constructorDefs == {}) return {};

    // We consider constructor definitions; we need to additionally find any uses at excepts (`!constructor``)
    sortedFacts = [<l, ws.facts[l]> | l <- sort(domain(ws.facts), bool(loc l1, loc l2) {
        // Sort facts by end location (ascending), then by length (ascending)
        return l1.end != l2.end ? l1.end < l2.end : l1.length < l2.length;
    })];

    set[RenameLocation] uses = {};
    for (Define d: <_, consName, _, constructorId(), _, defType(acons(aadt(_, _, _), _, _))> <- constructorDefs) {
        // Find all neighbouring pairs of facts where an except for `cursorName` exists only in the latter
        for (
            [ *_
            , <_, !/\a-except(consName)>
            , <l2, at2:  /\a-except(consName)>
            , *_] := sortedFacts
            , aprod(choice(_, _)) !:= at2
        ) {
            // There might be whitespace before (but not after) the `cursorName`, so we correct the location length
            uses += <trim(l2, removePrefix = l2.length - size(consName)), nothing()>;
        }
    }

    return uses;
}

DefsUsesRenames rascalGetDefsUses(TModel ws, cursor(use(), l, cursorName), MayOverloadFun mayOverloadF, ChangeAnnotationRegister registerChangeAnnotation, PathConfig(loc) getPathConfig) {
    set[loc] defs = rascalGetOverloadedDefs(ws, getDefs(ws, l), mayOverloadF);
    set[RenameLocation] uses = annotateLocs(getUses(ws, defs));

    set[IdRole] roles = {ws.definitions[d].idRole | d <- defs};
    if (keywordFormalId() in roles) {
        uses += rascalGetKeywordFormalUses(ws, defs, cursorName);
        uses += rascalGetKeywordFieldUses(ws, defs, cursorName);
        uses += rascalGetHasUses(ws, defs, cursorName, registerChangeAnnotation, getPathConfig);
    } else if (constructorId() in roles) {
        uses += rascalGetExceptUses(ws, defs);
    }

    return <annotateLocs(defs), uses, NO_RENAMES>;
}

DefsUsesRenames rascalGetDefsUses(TModel ws, cursor(def(), l, cursorName), MayOverloadFun mayOverloadF, ChangeAnnotationRegister registerChangeAnnotation, PathConfig(loc) getPathConfig) {
    set[loc] initialUses = getUses(ws, l);
    set[loc] initialDefs = {l} + {*ds | u <- initialUses, ds := getDefs(ws, u)};
    set[loc] defs = rascalGetOverloadedDefs(ws, initialDefs, mayOverloadF);
    set[RenameLocation] uses = annotateLocs(getUses(ws, defs));

    set[IdRole] roles = {ws.definitions[d].idRole | d <- defs};
    if (roles & {keywordFormalId(), fieldId()} != {}) {
        uses += rascalGetKeywordFormalUses(ws, defs, cursorName);
        uses += rascalGetKeywordFieldUses(ws, defs, cursorName);
        uses += rascalGetHasUses(ws, defs, cursorName, registerChangeAnnotation, getPathConfig);
    } else if (constructorId() in roles) {
        uses += rascalGetExceptUses(ws, defs);
    }

    return <annotateLocs(defs), uses, NO_RENAMES>;
}

DefsUsesRenames rascalGetDefsUses(TModel ws, cursor(exceptConstructor(), l, cursorName), MayOverloadFun mayOverloadF, ChangeAnnotationRegister registerChangeAnnotation, PathConfig(loc) getPathConfig) {
    if (f <- ws.facts
       , isContainedIn(l, f)
       , aprod(prod(_, [*_, conditional(AType nontermType, /\a-except(cursorName)) ,*_])) := ws.facts[f]
       , Define currentCons:<_, _, _, constructorId(), _, _> <- ws.defines
       , isContainedIn(currentCons.defined, f)
       , Define exceptCons:<_, cursorName, _, constructorId(), _, defType(acons(nontermType, _, _))> <- rascalReachableDefs(ws, {currentCons.defined})) {
        return rascalGetDefsUses(ws, cursor(def(), exceptCons.defined, cursorName), mayOverloadF, registerChangeAnnotation, getPathConfig);
    }

    return <{}, {}, NO_RENAMES>;
}

DefsUsesRenames rascalGetDefsUses(TModel ws, cursor(typeParam(), cursorLoc, cursorName), MayOverloadFun _, ChangeAnnotationRegister _, PathConfig(loc) getPathConfig) {
    set[loc] getFormals(afunc(_, _, _), rel[loc, AType] facts) = {l | <l, f> <- facts, f.alabel != ""};
    set[loc] getFormals(aadt(_, _, _), rel[loc, AType] facts) {
        perName = {<name, l> | <l, f: aparameter(name, _)> <- facts, f.alabel == ""};
        // Per parameter name, keep the top-left-most occurrence
        return mapper(groupRangeByDomain(perName), loc(set[loc] locs) {
            return (getFirstFrom(locs) | l.offset < it.offset ? l : it | l <- locs);
        });
    }

    bool definesTypeParam(Define _: <_, _, _, functionId(), _, defType(AType dT)>, AType paramType) =
        afunc(_, /paramType, _) := dT;
    bool definesTypeParam(Define _: <_, _, _, nonterminalId(), _, defType(AType dT)>, AType paramType) =
        aadt(_, /paramType, _) := dT;
    default bool definesTypeParam(Define _, AType _) = false;

    AType cursorType = ws.facts[cursorLoc];

    set[loc] defs = {};
    set[loc] useDefs = {};
    for (Define containingDef <- ws.defines
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
        defs += {min([f | f <- facts<0>, f.offset == nextOffset]) | formal <- formals, nextOffsets[formal.offset]?, nextOffset := nextOffsets[formal.offset]};

        useDefs += {trim(l, removePrefix = l.length - size(cursorName))
                    | l <- facts<0>
                    , !ws.definitions[l]? // If there is a definition at this location, this is a formal argument name
                    , !any(ud <- ws.useDef[l], ws.definitions[ud]?) // If there is a definition for the use at this location, this is a use of a formal argument
                    , !any(flInner <- facts<0>, isStrictlyContainedIn(flInner, l)) // Filter out any facts that contain other facts
        };
    }

    return <annotateLocs(defs), annotateLocs(useDefs - defs), NO_RENAMES>;
}

private str describeFact(just(AType tp)) = "type \'<prettyAType(tp)>\'";
private str describeFact(nothing()) = "unknown type";

Maybe[tuple[loc, set[loc]]] rascalGetHasLocs(str fieldName, (Expression) `<Expression e> has <Name field>`) =
    just(<e.src, {field.src}>) when fieldName == "<field>";

default Maybe[tuple[loc, set[loc]]] rascalGetHasLocs(str _, Tree _) = nothing();

set[RenameLocation] rascalGetHasUses(TModel ws, set[loc] defs, str cursorName, ChangeAnnotationRegister registerChangeAnnotation, PathConfig(loc) _) {
    return {
        <field, just(registerChangeAnnotation("Use of `has <cursorName>` on value of <describeFact(getFact(ws, lhs))>", "Due to the dynamic nature of these names, please review these suggested changes.", true))>
        | loc l <- rascalReachableModules(ws, defs)
        , /Tree t := parseModuleWithSpacesCached(l)
        , just(<lhs, {field}>) := rascalGetHasLocs(cursorName, t)
    };
}

DefsUsesRenames rascalGetDefsUses(TModel ws, cursor(dataField(loc adtLoc, AType fieldType), cursorLoc, cursorName), MayOverloadFun mayOverloadF, ChangeAnnotationRegister registerChangeAnnotation, PathConfig(loc) getPathConfig) {
    set[loc] initialDefs = {};
    if (cursorLoc in ws.useDef<0>) {
        initialDefs = getDefs(ws, cursorLoc);
    } else if (cursorLoc in ws.defines<defined>) {
        initialDefs = {cursorLoc};
    } else if (just(AType adtType) := getFact(ws, adtLoc)) {
        set[Define] reachableDefs = rascalReachableDefs(ws, {adtLoc});
        initialDefs = {
            fieldDef.defined
            | Define dataDef: <_, _, _, _, _, defType(adtType)> <- rascalGetADTDefinitions(ws, adtLoc)
            , Define fieldDef: <_, _, cursorName, _, _, _> <- reachableDefs
            , fieldDef.idRole in {keywordFormalId(), fieldId()}
            , isStrictlyContainedIn(fieldDef.defined, dataDef.defined)
        };
    } else {
        throw unsupportedRename("Cannot rename data field \'<cursorName>\' from <cursorLoc>");
    }

    set[loc] defs = rascalGetOverloadedDefs(ws, initialDefs, mayOverloadF);
    set[RenameLocation] uses = annotateLocs(getUses(ws, defs)) + rascalGetKeywordFieldUses(ws, defs, cursorName);
    set[RenameLocation] hasUses = rascalGetHasUses(ws, defs, cursorName, registerChangeAnnotation, getPathConfig);

    return <annotateLocs(defs), uses + hasUses, NO_RENAMES>;
}

bool debug = false;

DefsUsesRenames rascalGetDefsUses(TModel ws, cursor(cursorKind, cursorLoc, cursorName), MayOverloadFun mayOverloadF, ChangeAnnotationRegister registerChangeAnnotation, PathConfig(loc) getPathConfig) {
    if (cursorKind is dataKeywordField || cursorKind is dataCommonKeywordField) {
        set[RenameLocation] defs = {};
        set[RenameLocation] uses = {};

        set[loc] adtDefs = rascalGetOverloadedDefs(ws, {cursorKind.dataTypeDef}, mayOverloadF);
        set[Define] reachableDefs = rascalReachableDefs(ws, adtDefs);
        set[loc] reachableModules = rascalReachableModules(ws, reachableDefs.defined);

        for (Define d:<_, _, _, constructorId(), _, defType(AType consType)> <- reachableDefs) {
            <ds, us, _> = rascalGetFieldDefsUses(ws, d.defined, consType, cursorKind.fieldType, cursorName);
            defs += ds;
            uses += us;
        }
        for (Define d:<_, _, _, _, _, defType(acons(AType dataType, _, _))> <- reachableDefs
           , d.idRole != dataId()) {
            <ds, us, _> = rascalGetFieldDefsUses(ws, d.defined, dataType, cursorKind.fieldType, cursorName);
            defs += ds;
            uses += us;
        }

        uses += rascalGetHasUses(ws, adtDefs, cursorName, registerChangeAnnotation, getPathConfig);

        return <defs, uses, NO_RENAMES>;
    }

    fail;
}

DefsUsesRenames rascalGetDefsUses(TModel ws, cursor(collectionField(), cursorLoc, cursorName), MayOverloadFun _, ChangeAnnotationRegister _, PathConfig(loc) getPathConfig) {
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
        return rascalGetFieldDefsUses(ws, cursorLoc, collUseType, cursorType, cursorName);
    }

    // We can find the collection type by looking for the first use to the left of the cursor that has a collection type
    lrel[loc use, loc def] usesToLeft = reverse(sort({<u, d> | <u, d> <- ws.useDef, isSameFile(u, cursorLoc), u.offset < cursorLoc.offset}));
    if (<_, d> <- usesToLeft, define := ws.definitions[d], defType(AType collDefType) := define.defInfo, rascalIsCollectionType(collDefType)) {
        // We are at a use site, where the field element type is wrapped in a `aset` of `alist` constructor
        return rascalGetFieldDefsUses(ws, define.defined, collDefType, isTupleField(cursorType) ? cursorType.elmType : cursorType, cursorName);
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

private DefsUsesRenames rascalGetFieldDefsUses(TModel ws, loc consOrDataDef, AType containerType, AType fieldType, str cursorName) {
    set[loc] containerFacts = {f | f <- factsInvert(ws)[containerType], consOrDataDef.top in  rascalReachableModules(ws, {f})};
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

    return <annotateLocs(defs), annotateLocs(uses), NO_RENAMES>;
}

DefsUsesRenames rascalGetDefsUses(TModel ws, cursor(moduleName(), cursorLoc, cursorName), MayOverloadFun _, ChangeAnnotationRegister registerChangeAnnotation, PathConfig(loc) getPathConfig) {
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

    set[loc] defs = {ms | ms <- getModuleScopes(ws), ms.top == moduleFile};

    rel[loc fromFile, loc toFile] modulePaths = toRel(getModuleScopePerFile(ws)) o rascalGetTransitiveReflexiveModulePaths(ws);
    set[loc] importUses = {u
        | <loc u, Define _: <_, cursorName, _, moduleId(), d, _>> <- ws.useDef o definitionsRel(ws)
        , <u.top, d> in modulePaths
    };

    rel[loc file, loc use] qualifiedUseCandidates = {
        <u.top, u>
        | <loc u, Define d> <- ws.useDef o definitionsRel(ws)
        , u.length > size(d.id) // use name > declaration name, i.e. there is a qualified prefix
    };
    set[loc] qualifiedUses = {
        qn.src
        | loc file <- qualifiedUseCandidates.file
        , start[Module] m := parseModuleWithSpacesCached(file)
        , set[loc] localUses := qualifiedUseCandidates[file]
        , /QualifiedName qn := m
        , qn.src in localUses
        , cursorName == intercalate("::", prefix(["<n>" | n <- qn.names]))
    };

    set[loc] uses = importUses + qualifiedUses;

    pcfg = getPathConfig(getProjectFolder(ws, moduleFile));
    loc srcFolder = [srcFolder | relModulePath := relativize(pcfg.srcs, moduleFile), srcFolder <- pcfg.srcs, exists(srcFolder + relModulePath.path)][0];
    rel[loc, loc] getRenames(str newName) = {<file, srcFolder + makeFileName(newName)> | d <- defs, file := d.top};

    return <annotateLocs(defs), annotateLocs(uses), getRenames>;
}
