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
module lang::rascal::lsp::refactor::rename::Types

extend framework::Rename;
import lang::rascal::lsp::refactor::rename::Common;

import lang::rascal::\syntax::Rascal;
import analysis::typepal::TModel;
import lang::rascalcore::check::BasicRascalConfig;
import lang::rascalcore::check::Import;

import Map;
import Set;

import util::Maybe;
import util::Util;

tuple[set[loc], set[loc], set[loc]] findOccurrenceFilesUnchecked(set[Define] defs:{<_, _, _, dataId(), _, _>, *_}, list[Tree] cursor, str newName, Tree(loc) getTree, Renamer r) =
    findDataLikeOccurrenceFilesUnchecked(defs, cursor, newName, getTree, r);

tuple[set[loc], set[loc], set[loc]] findOccurrenceFilesUnchecked(set[Define] _:{<loc scope, _, _, typeVarId(), _, _>}, list[Tree] cursor, str newName, Tree(loc) _, Renamer _) =
    <{scope.top}, {scope.top}, singleNameFilter(newName)(cursor[-1]) ? {scope.top} : {}>;

public tuple[set[loc], set[loc], set[loc]] findDataLikeOccurrenceFilesUnchecked(set[Define] defs, list[Tree] cursor, str newName, Tree(loc) getTree, Renamer r) {
    if (size(defs.id) > 1) {
        r.error(cursor[0], "Cannot find files for ADT definitions with multiple names (<defs.id>)");
        return <{}, {}, {}>;
    }

    <curAdtFiles, newFiles> = filterFiles(getSourceFiles(r), "<cursor[0]>", newName, twoNameFilter, getTree);

    consIds = {consId
        | Define _:<_, _, _, _, loc dataDef, defType(AType adtType)> <- defs
        , tm := r.getConfig().tmodelForLoc(dataDef.top)
        , Define _:<_, str consId, _, constructorId(), _, defType(acons(adtType, _, _))> <- tm.defines
    };

    consFiles = {*filterFiles(getSourceFiles(r), consId, singleNameFilter, getTree) | consId <- consIds};

    return <curAdtFiles + consFiles, curAdtFiles, newFiles>;
}

set[Define] findAdditionalDefinitions(set[Define] cursorDefs:{<_, _, _, dataId(), _, _>, *_}, Tree tr, TModel tm, Renamer r) =
    findAdditionalDataLikeDefinitions(cursorDefs, tm, r);

public set[Define] findAdditionalDataLikeDefinitions(set[Define] defs, TModel tm, Renamer r) {
    reachable = rascalGetReflexiveModulePaths(tm).to;
    reachableCursorDefs = {d.defined | Define d <- defs, any(loc modScope <- reachable, isContainedInScope(d.defined, modScope, tm))};

    if ({} := reachableCursorDefs) return {};

    return {fileTm.definitions[overload]
        | loc modScope <- reachable
        , loc f := modScope.top
        , fileTm := r.getConfig().tmodelForLoc(f)
        , definitions := (d.defined: d | d <- fileTm.defines) + tm.definitions
        , loc overload <- (fileTm.defines<idRole, defined>)[dataOrSyntaxRoles]
        , rascalMayOverloadSameName(reachableCursorDefs + overload, definitions)
    };
}

tuple[type[Tree] as, str desc] asType(aliasId()) = <#Name, "type name">;
tuple[type[Tree] as, str desc] asType(annoId()) = <#Name, "annotation name">;
tuple[type[Tree] as, str desc] asType(dataId()) = <#Name, "ADT name">;
tuple[type[Tree] as, str desc] asType(typeVarId()) = <#Name, "type variable name">;

alias Environment = tuple[TModel tm, map[str, loc] defs];

Environment addDef(Tree def, loc scope, <TModel tm, map[str, loc] defs>) {
    str name = "<def>";
    Define d = <scope, name, name, typeVarId(), def.src, noDefInfo()>;
    tm = tm[defines = tm.defines + d][definitions = tm.definitions + (d.defined: d)];

    defs[name] = def.src;
    return <tm, defs>;
}

Environment removeUses(Tree use, <TModel tm, map[str, loc] defs>) {
    loc u = use.src;
    if (useDefs:{_, *_} := tm.useDef[u]) {
        removals = {<u, d> | loc d <- useDefs};
        tm = tm[useDef = tm.useDef - removals];
    }
    return <tm, defs>;
}

Environment addUse(Tree use, <TModel tm, map[str, loc] defs>) {
    str useName = "<use>";
    // We do not know how to augment this; silently fail
    if (useName notin defs) return <tm, defs>;
    tm = tm[useDef = tm.useDef + <use.src, defs[useName]>];
    return <tm, defs>;
}

Environment addDefOtherwiseUse(Tree occ, loc defScope, <TModel tm, map[str, loc] defs>) {
    if ("<occ>" in defs) return addUse(occ, <tm, defs>);
    return addDef(occ, defScope, <tm, defs>);
}

Environment augmentTypeParams(Tree tr, <TModel tm, map[str, loc] defs>) {
    top-down-break visit (tr) {
        case (Module) `<Tags _> module <QualifiedName _> <ModuleParameters params> <Import* _> <Body body>`: {
            if ({loc modScope} := range(getModuleScopes(tm))) {
                <tm, modDefs> = (<tm, defs> | addDef(tv.name, modScope, it) | TypeVar tv <- params.parameters);
                <tm, _> = augmentTypeParams(body, <tm, modDefs>);
            }
        }
        case FunctionDeclaration func: {
            funcDefs = defs;
            for (Pattern pat <- func.signature.parameters.formals.formals, pat has \type, /TypeVar tv := pat.\type) {
                // The TModel sometimes contains uses that we do not want
                <tm, funcDefs> = removeUses(tv.name, <tm, funcDefs>);
                <tm, funcDefs> = addDefOtherwiseUse(tv.name, func.src, <tm, funcDefs>);
            }
            <tm, funcDefs> = (<tm, funcDefs> | addUse(tv.name, removeUses(tv.name, it)) | /TypeVar tv := func.signature.\type);

            if (func has conditions) <tm, funcDefs> = augmentTypeParams(func.conditions, <tm, funcDefs>);
            if (func has expression) <tm, _> = augmentTypeParams(func.expression, <tm, funcDefs>);
            else if (func has body) <tm, _> = augmentTypeParams(func.body, <tm, funcDefs>);
        }
        case Concrete pat: {
            <tm, concDefs> = (<tm, defs> | addDefOtherwiseUse(nt, pat.src, it) | /(Sym) `&<Nonterminal nt>` := pat.symbol);
            <tm, _> = (<tm, concDefs> | addUse(nt, it) | /(Sym) `&<Nonterminal nt>` := pat.parts);
        }
        case SyntaxDefinition def: {
            <tm, symDefs> = (<tm, defs> | addDefOtherwiseUse(nt, def.src, it) | /(Sym) `&<Nonterminal nt>` := def.defined);
            <tm, _> = (<tm, symDefs> | addUse(nt, it) | /(Sym) `&<Nonterminal nt>` := def.production);
        }
        case TypeVar tv: <tm, defs> = addUse(tv.name, <tm, defs>);
        case (Sym) `&<Nonterminal nt>`: <tm, defs> = addUse(nt, <tm, defs>);
    }
    return <tm, defs>;
}

TModel augmentTypeParams(Tree tr, TModel tm) =
    augmentTypeParams(tr, <tm, ()>)<0>;
