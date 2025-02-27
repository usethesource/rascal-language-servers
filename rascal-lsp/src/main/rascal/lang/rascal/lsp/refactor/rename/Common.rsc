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
module lang::rascal::lsp::refactor::rename::Common

extend framework::Rename;
import framework::TextEdits;

import analysis::typepal::TModel;
import lang::rascal::\syntax::Rascal;
import lang::rascalcore::check::ATypeBase;
import lang::rascalcore::check::RascalConfig;
import lang::rascalcore::check::BasicRascalConfig;
import util::refactor::WorkspaceInfo;

import List;
import Location;
import Map;
import Relation;
import Set;
import String;
import util::FileSystem;
import util::Maybe;
import util::Reflective;
import util::Util;

data RenameConfig(
    set[loc] workspaceFolders = {}
  , PathConfig(loc) getPathConfig = PathConfig(loc l) { throw "No path config for <l>"; }
);

bool(loc) containsFilter(type[&T <: Tree] t, str name, str(str) escape, Tree(loc) getTree) {
    Tree n = parse(t, name);
    Tree en = parse(t, escape(name));
    return bool(loc l) {
        bottom-up-break visit (getTree(l)) {
            case n: return true;
            case en: return true;
        }
        return false;
    };
}

bool isContainedInScope(loc l, loc scope, TModel tm) {
    // lexical containment
    if (isContainedIn(l, scope)) return true;

    // via import/extend
    set[loc] reachableFrom = (tm.paths<pathRole, to, from>)[{importPath(), extendPath()}, scope];
    return any(loc fromScope <- reachableFrom, isContainedIn(l, fromScope));
}

set[loc] findSortOccurrenceFiles(type[&T <: Tree] N, str curName, set[loc]() getSourceFiles, Tree(loc) getTree) {
    containsName = containsFilter(N, curName, rascalEscapeName, getTree);
    return {f
        | loc f <- getSourceFiles()
        , containsName(f)
    };
}

// Workaround to be able to pattern match on the emulated `src` field
data Tree (loc src = |unknown:///|(0,0,<0,0>,<0,0>));

@memo{maximumSize(1000), expireAfter(minutes=5)}
str rascalEscapeName(str name) = intercalate("::", [n in getRascalReservedIdentifiers() ? "\\<n>" : n | n <- split("::", name)]);

Maybe[AType] getFact(TModel tm, loc l) = l in tm.facts ? just(tm.facts[l]) : nothing();

bool rascalMayOverloadSameName(set[loc] defs, map[loc, Define] definitions) {
    if (l <- defs, !definitions[l]?) return false;
    set[Define] defines = {definitions[d] | d <- defs};

    if (size(defines.id) > 1) return false;
    if (size(defines) == 0) return false;
    return rascalMayOverload(defs, definitions);
}

@memo{maximumSize(1), expireAfter(minutes=5)}
set[loc] getModuleScopes(TModel tm) = invert(tm.scopes)[|global-scope:///|];

@memo{maximumSize(1), expireAfter(minutes=5)}
map[loc, loc] getModuleScopePerFile(TModel tm) = (scope.top: scope | loc scope <- getModuleScopes(tm));

@memo{maximumSize(1), expireAfter(minutes=5)}
rel[loc from, loc to] rascalGetTransitiveReflexiveScopes(TModel tm) = toRel(tm.scopes)*;

set[Define] rascalReachableDefs(TModel tm, set[loc] defs) {
    rel[loc from, loc to] modulePaths = rascalGetTransitiveReflexiveModulePaths(tm);
    rel[loc from, loc to] scopes = rascalGetTransitiveReflexiveScopes(tm);
    rel[loc from, Define define] reachableDefs =
        ((tm.defines<defined, defined, scope>)[defs]             // <definition, scope> pairs
        + (tm.defines<scope, defined, scope>)[defs])
      o (
         (scopes                                                // All scopes surrounding defs
        o modulePaths                                           // Transitive-reflexive paths from scope to reachable modules
        ) + (                                                   // In ADT and syntax definitions, search inwards to find field scopes
            (tm.defines<idRole, scope, defined>)[{dataId(), nonterminalId(), lexicalId()}]
          + tm.defines<scope, scope>
        )
      )
      o tm.defines<scope, defined>                              // Definitions in these scopes
      o definitionsRel(tm);                                     // Full define tuples
    return reachableDefs.define;                                // We are only interested in reached defines; not *from where* they were reached
}

@memo{maximumSize(1), expireAfter(minutes=5)}
rel[loc from, loc to] rascalGetTransitiveReflexiveModulePaths(TModel tm) {
    rel[loc from, loc to] moduleI = ident(getModuleScopes(tm));
    rel[loc from, loc to] imports = (tm.paths<pathRole, from, to>)[importPath()];
    rel[loc from, loc to] extends = (tm.paths<pathRole, from, to>)[extendPath()];

    return (moduleI + imports)  // 0 or 1 imports
         o (moduleI + extends+) // 0 or more extends
         ;
}

@memo{maximumSize(100), expireAfter(minutes=5)}
rel[loc from, loc to] rascalGetReflexiveModulePaths(TModel tm) =
    ident(getModuleScopes(tm))
  + (tm.paths<pathRole, from, to>)[importPath()]
  + (tm.paths<pathRole, from, to>)[extendPath()];

set[loc] rascalGetOverloadedDefs(TModel tm, set[loc] defs) {
    if (defs == {}) return {};

    set[Define] overloadedDefs = {tm.definitions[d] | d <- defs};
    set[IdRole] roles = overloadedDefs.idRole;

    // Pre-conditions
    assert size(roles) == 1:
        "Initial defs are of different roles!";
    assert rascalMayOverloadSameName(defs, tm.definitions):
        "Initial defs are invalid overloads!";

    IdRole role = getFirstFrom(roles);
    map[loc file, loc scope] moduleScopePerFile = getModuleScopePerFile(tm);
    rel[loc def, loc scope] defUseScopes = {<d, moduleScopePerFile[u.top]> | <loc u, loc d> <- tm.useDef};
    rel[loc fromScope, loc toScope] modulePaths = rascalGetTransitiveReflexiveModulePaths(tm);

    rel[loc def, loc moduleScope] defPathStep =
        (tm.defines<defined, scope>+ + defUseScopes) // 1. Look up scopes of defs and scopes of their uses
        o (modulePaths + invert(modulePaths))        // 2. Follow import/extend relations to reachable scopes
        ;

    rel[loc fromDef, loc toDef] defPaths = {};
    set[loc] reachableDefs = rascalReachableDefs(tm, overloadedDefs.defined).defined;

    solve(overloadedDefs) {
        if (constructorId() := role) {
            set[AType] adtTypes = {adtType | defType(acons(AType adtType, _, _)) <- overloadedDefs.defInfo};
            set[loc] initialADTs = {
                adtDef
                | Define _: <_, _, _, dataId(), loc adtDef, defType(AType adtType)> <- rascalReachableDefs(tm, overloadedDefs.defined)
                , adtType in adtTypes
            };
            set[loc] selectedADTs = rascalGetOverloadedDefs(tm, initialADTs);

            // Any constructor definition of the right type where any `selectedADTs` element is in the reachable defs
            rel[loc scope, loc def] selectedConstructors = {<s, d>
                | <s, d, defType(acons(AType adtType, _, _))> <- (tm.defines<idRole, scope, defined, defInfo>)[role]
                , adtType in adtTypes
                , any(<_, _, _, dataId(), loc r, _> <- rascalReachableDefs(tm, {d}), r in selectedADTs)
            };

            // We transitively resolve module scopes via modules that have a relevant constructor/ADTs use or definition
            rel[loc scope, loc def] selectedDefs = selectedConstructors + (tm.defines<defined, scope, defined>)[selectedADTs];
            rel[loc fromScope, loc toScope] constructorStep = (selectedDefs + invert(defUseScopes)) o defPathStep;

            defPathStep = defPathStep /* <def, scope> */
                        + (defPathStep /* <def, scope> */ o constructorStep+ /* <scope, scope> */) /* <def, scope> */;

            defPaths = defPathStep /* <def, scope> */ o selectedConstructors /* <scope, def> */;
        } else if (dataId() := role) {
            set[AType] adtTypes = {adtType | defType(AType adtType) <- overloadedDefs.defInfo};
            set[loc] constructorDefs = {d
                | <defType(acons(AType adtType, _, _)), d> <- (rascalReachableDefs(tm, overloadedDefs.defined)<idRole, defInfo, defined>)[constructorId()]
                , adtType in adtTypes
                , any(dd <- overloadedDefs.defined, isStrictlyContainedIn(d, dd))
            };

            set[Define] defsReachableFromOverloads = rascalReachableDefs(tm, overloadedDefs.defined + defUseScopes[overloadedDefs.defined]);
            set[Define] defsReachableFromOverloadConstructors = rascalReachableDefs(tm, constructorDefs + defUseScopes[constructorDefs]);

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
            set[DefInfo] selectedADTTypes = (tm.defines<defined, defInfo>)[overloadedDefs.scope];
            rel[loc, loc] selectedADTs = (tm.defines<defInfo, scope, defined>)[selectedADTTypes];
            rel[loc, loc] selectedFields = selectedADTs o tm.defines<scope, defined>;
            defPaths = defPathStep o selectedFields;
        } else {
            // Find definitions in the reached scope, and definitions within those definitions (transitively)
            defPaths = defPathStep o (tm.defines<idRole, scope, defined>)[role]+;
        }

        set[loc] overloadCandidates = defPaths[overloadedDefs.defined];
        overloadedDefs += {tm.definitions[d]
            | loc d <- overloadCandidates
            , rascalMayOverloadSameName(overloadedDefs.defined + d, tm.definitions)
        };
        reachableDefs = rascalReachableDefs(tm, overloadedDefs.defined).defined;
    }

    return overloadedDefs.defined;
}

default void renameAdditionalUses(set[Define] defs, str newName, Tree tr, TModel tm, Renamer r) {}

default bool isUnsupportedCursor(list[Tree] cursor, Renamer _) = false;
