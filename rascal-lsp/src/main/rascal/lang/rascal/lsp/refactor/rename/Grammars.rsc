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
module lang::rascal::lsp::refactor::rename::Grammars

extend framework::Rename;
import lang::rascal::lsp::refactor::Rename;

import lang::rascal::lsp::refactor::rename::Common;
import lang::rascal::lsp::refactor::rename::Types;

import lang::rascal::\syntax::Rascal;
import lang::rascalcore::check::BasicRascalConfig;

import Location;

data Tree;

tuple[set[loc], set[loc], set[loc]] findOccurrenceFilesUnchecked(set[Define] defs:{<_, _, _, IdRole role, _, _>, *_}, list[Tree] cursor, str newName, Tree(loc) getTree, Renamer r) =
    findDataLikeOccurrenceFilesUnchecked(defs, cursor, newName, getTree, r)
    when role in syntaxRoles;

set[Define] findAdditionalDefinitions(set[Define] cursorDefs:{<_, _, _, IdRole role, _, _>, *_}, Tree tr, TModel tm, Renamer r) =
    findAdditionalDataLikeDefinitions(cursorDefs, tr.src.top, tm, r)
    when role in syntaxRoles;

void renameDefinitionUnchecked(Define d: <_, _, _, nonterminalId(), _, _>, loc _, str _, TModel _, Renamer _) {
    // Do not register an edit for the definition, as it will appear as a use again
    // TODO Rascal Core: why register the name of a production definition as a use of itself?
}

void renameDefinitionUnchecked(Define d: <_, _, _, lexicalId(), _, _>, loc _, str _, TModel _, Renamer _) {
    // Do not register an edit for the definition, as it will appear as a use again
    // TODO Rascal Core: why register the name of a production definition as a use of itself?
}

// Non-terminals
tuple[type[Tree] as, str desc] asType(nonterminalId()) = <#Nonterminal, "production name">;

// Lexicals
tuple[type[Tree] as, str desc] asType(lexicalId()) = <#Nonterminal, "production name">;

tuple[Nonterminal, list[NonterminalLabel]] extractExcepts(Sym s) {
    list[NonterminalLabel] excepts = [];
    top-down visit (s) {
        case (Sym) `<Sym _>!<NonterminalLabel except>`: excepts += except;
        case Nonterminal nonTerm: return <nonTerm, excepts>;
    }

    throw "No Nonterminal reached (for excepts <excepts>)";
}

TModel augmentExceptProductions(Tree tr, TModel tm, TModel(loc) tmodelForLoc) {
    top-down-break visit (tr) {
        case outerExcept:(Sym) `<Sym _>!<NonterminalLabel _>`: {
            <nonTerm, excepts> = extractExcepts(outerExcept);
            ntDefs = tm.useDef[nonTerm.src];
            rel[loc, loc] exceptUseDefs = flatMapPerFile(ntDefs, rel[loc, loc](loc f, set[loc] localNtDefs) {
                localTm = tmodelForLoc(f);
                return {<cons.src, cd>
                    | cons <- excepts
                    // Find all constructors that are defined in this file
                    , loc cd <- (localTm.defines<idRole, id, defined>)[constructorId(), "<cons>"]
                    // Check if they belong to the non-terminal from the original production
                    , any(loc ntd <- localNtDefs, isContainedIn(cd, ntd))
                };
            });

            tm = tm[useDef = tm.useDef + exceptUseDefs];
        }
    }
    return tm;
}
