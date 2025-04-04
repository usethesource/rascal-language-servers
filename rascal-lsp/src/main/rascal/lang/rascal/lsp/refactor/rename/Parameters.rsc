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
module lang::rascal::lsp::refactor::rename::Parameters

extend framework::Rename;
extend lang::rascal::lsp::refactor::rename::Common;
import lang::rascal::lsp::refactor::rename::Fields;

import lang::rascal::\syntax::Rascal;
import analysis::typepal::TModel;
import lang::rascalcore::check::BasicRascalConfig;

import Relation;
import Set;
import util::Maybe;

bool isFormalId(IdRole role) = role in formalRoles;

tuple[type[Tree] as, str desc] asType(IdRole idRole) = <#Name, "formal parameter name"> when isFormalId(idRole);

tuple[set[loc], set[loc], set[loc]] findOccurrenceFilesUnchecked(set[Define] _:{<loc scope, _, _, IdRole role, _, _>}, list[Tree] cursor, str newName, Tree(loc) _, Renamer _) =
    <{scope.top}, {scope.top}, allNameSortsFilter(newName)(cursor[-1]) ? {scope.top} : {}>
    when role in positionalFormalRoles;

@synopsis{Add use/def relations for keyword function parameters, until they exist in the TModel.}
TModel augmentFormalUses(Tree tr, TModel tm, TModel(loc) getModel) {
    rel[loc funcDef, str kwName, loc kwLoc] keywordFormalDefs = {
        *(fileTm.defines<idRole, scope, id, defined>)[keywordFormalId()]
        | loc f <- getModuleFile(tm) + (tm.paths<to>)
        , fileTm := getModel(f.top)
    };
    visit (tr) {
        case (Expression) `<Expression e>(<{Expression ","}* _> <KeywordArguments[Expression] kwArgs>)`: {
            funcKwDefs = keywordFormalDefs[tm.useDef[e.src]];
            // Only visit uses of our keyword arguments - do not go into nested calls
            top-down-break visit (kwArgs) {
                case (KeywordArgument[Expression]) `<Name kw> = <Expression _>`: {
                    for (loc d <- funcKwDefs["<kw>"]) {
                        tm = tm[useDef = tm.useDef + <kw.src, d>];
                    }
                }
            }
        }
    }
    return tm;
}
