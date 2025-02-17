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
import lang::rascalcore::check::RascalConfig;

import List;
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

// Copied from `lang::rascalcore::check::BasicRascalConfig` to remove dependency on it
data IdRole
    = moduleId()
    | functionId()
    | formalId()
    | keywordFormalId()
    | nestedFormalId()
    | patternVariableId()
    | moduleVariableId()
    | fieldId()
    | keywordFieldId()
    | labelId()
    | constructorId()
    | productionId()
    | dataId()
    | aliasId()
    | annoId()
    | nonterminalId()
    | lexicalId()
    | layoutId()
    | keywordId()
    | typeVarId()
    ;

// Workaround to be able to pattern match on the emulated `src` field
data Tree (loc src = |unknown:///|(0,0,<0,0>,<0,0>));

@memo{maximumSize(1000), expireAfter(minutes=5)}
str rascalEscapeName(str name) = intercalate("::", [n in getRascalReservedIdentifiers() ? "\\<n>" : n | n <- split("::", name)]);

bool rascalMayOverloadSameName(set[loc] defs, map[loc, Define] definitions) {
    if (l <- defs, !definitions[l]?) return false;
    set[Define] defines = {definitions[d] | d <- defs};

    if (size(defines.id) > 1) return false;
    if (size(defines) == 0) return false;
    return rascalMayOverload(defs, definitions);
}

default void renameAdditionalUses(set[Define] defs, str newName, Tree tr, TModel tm, Renamer r) {}

default tuple[type[Tree] as, str desc] asType(_) = <#Name, "name">;

default bool isUnsupportedCursor(list[Tree] cursor, Renamer _) = false;

void rascalCheckLegalNameByRole(Define _:<_, _, _, role, at, _>, str name, Renamer r) {
    escName = rascalEscapeName(name);
    tuple[type[Tree] as, str desc] t = asType(role);
    if (tryParseAs(t.as, escName) is nothing) {
        r.error(at, "<escName> is not a valid <t.desc>");
    }
}

void rascalCheckCausesDoubleDeclarations(Define _:<cS, _, _, role, cD, _>, TModel tm, str newName, Renamer r) {
    set[Define] newNameDefs = {def | Define def:<_, newName, _, _, _, _> <- tm.defines};

    // Is newName already resolvable from a scope where <current-name> is currently declared?
    rel[loc old, loc new] doubleDeclarations = {<cD, nD.defined> | Define nD <- newNameDefs
                                                                 , isContainedIn(cD, nD.scope)
                                                                 , !rascalMayOverload({cD, nD.defined}, tm.definitions)
    };

    rel[loc old, loc new] doubleFieldDeclarations = {<cD, nD>
        | fieldId() := role
          // The scope of a field def is the surrounding data def
        , loc dataDef <- rascalGetOverloadedDefs(tm, {cS}, rascalMayOverloadSameName)
        , loc nD <- (newNameDefs<idRole, defined>)[fieldId()] & (tm.defines<idRole, scope, defined>)[fieldId(), dataDef]
    };

    // TODO Re-do once we decided how to treat definitions that are not in tm.defines
    // rel[loc old, loc new] doubleTypeParamDeclarations = {<cD, nD>
    //     | loc cD <- currentDefs
    //     , tm.facts[cD]?
    //     , cT: aparameter(_, _) := tm.facts[cD]
    //     , Define fD: <_, _, _, _, _, defType(afunc(_, funcParams:/cT, _))> <- tm.defines
    //     , isContainedIn(cD, fD.defined)
    //     , <loc nD, nT: aparameter(newName, _)> <- toRel(tm.facts)
    //     , isContainedIn(nD, fD.defined)
    //     , /nT := funcParams
    // };

    for (<old, new> <- doubleDeclarations + doubleFieldDeclarations /*+ doubleTypeParamDeclarations*/) {
        r.error(old, "Cannot rename to <newName>, since it will lead to double declaration error (<new>).");
    }
}

void rascalCheckDefinitionOutsideWorkspace(Define d, TModel tm, Renamer r) {
    f = d.defined.top;
    pcfg = r.getConfig().getPathConfig(f);
    if (!any(srcFolder <- pcfg.srcs, isPrefixOf(srcFolder, f))) {
        r.error(d, "Since this definition is not in the sources of open projects, it cannot be renamed.");
    }
}
