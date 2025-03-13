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
module lang::rascal::lsp::refactor::rename::Fields

extend framework::Rename;
import lang::rascal::lsp::refactor::rename::Common;
import lang::rascalcore::check::BasicRascalConfig;

import lang::rascal::lsp::refactor::rename::Constructors;
import lang::rascal::lsp::refactor::rename::Types;

import lang::rascal::\syntax::Rascal;
import analysis::typepal::TModel;

import util::Maybe;

bool isFieldRole(IdRole role) = role in {fieldId(), keywordFieldId()};

set[Define] findAdditionalDefinitions(set[Define] cursorDefs:{<_, _, _, role, _, _>, *_}, Tree tr, TModel tm, Renamer r) {
    if (!isFieldRole(role)) fail findAdditionalDefinitions;
    if ({str fieldName} := cursorDefs.id) {
        adtDefs = {tm.definitions[d] | loc d <- (tm.defines<idRole, defined, defined>)[dataId(), cursorDefs.scope]};
        adtDefs += findAdditionalDefinitions(adtDefs, tr, tm, r);

        // Find all fields with the same name in these ADT definitions
        return flatMapPerFile(adtDefs, set[Define](loc f, set[Define] fileDefs) {
            fileTr = r.getConfig().parseLoc(f);
            fileTm = r.getConfig().tmodelForTree(fileTr);
            return {fileTm.definitions[d] | loc d <- (fileTm.defines<id, idRole, scope, defined>)[fieldName, role, fileDefs.defined]};
        });
    }

    for (d <- cursorDefs) {
        r.error(d.defined, "Cannot find overloads for fields with multiple names (<cursorDefs.id>).");
    }
    return {};
}

// Positional fields
tuple[type[Tree] as, str desc] asType(fieldId()) = <#NonterminalLabel, "field name">;

// Keyword fields
tuple[type[Tree] as, str desc] asType(keywordFieldId()) = <#Name, "keyword field name">;

bool isUnsupportedCursor(list[Tree] _:[*_, (Expression) `<Expression _> has <Name n>`, *_], Renamer r) {
    r.error(n, "Cannot rename a field from this expression.");
    return false;
}

bool isUnsupportedCursor(list[Tree] _:[*_, TypeArg tp, *_, StructuredType _, *_], Renamer r) {
    r.error(tp, "Cannot rename a field from a structured type.");
    return false;
}
