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

import analysis::typepal::Collector;
import analysis::typepal::TModel;
import analysis::diff::edits::TextEdits;

import Map;
import util::Maybe;

bool isFieldRole(IdRole role) = role in {fieldId(), keywordFieldId()};

set[Define] findAdditionalDefinitions(set[Define] cursorDefs:{<_, _, _, role, _, _>, *_}, Tree tr, TModel tm, Renamer r) {
    if (!isFieldRole(role)) fail findAdditionalDefinitions;
    if ({str fieldName} := cursorDefs.id) {
        adtDefs = {tm.definitions[d] | loc d <- (tm.defines<idRole, defined, defined>)[dataId(), cursorDefs.scope]};
        adtDefs += findAdditionalDefinitions(adtDefs, tr, tm, r);

        // Find all fields with the same name in these ADT definitions
        return flatMapPerFile(adtDefs, set[Define](loc f, set[Define] fileDefs) {
            fileTm = r.getConfig().tmodelForLoc(f);
            return {fileTm.definitions[d] | loc d <- (fileTm.defines<id, idRole, scope, defined>)[fieldName, role, fileDefs.defined]};
        });
    }

    for (d <- cursorDefs) {
        r.error(d.defined, "Cannot find overloads for fields with multiple names (<cursorDefs.id>).");
    }
    return {};
}

set[Define] getFieldDefinitions((Expression) `<Expression e> has <Name n>`, TModel tm, Renamer r) {
    lhsDefs = tm.useDef[e.src];
    if ({} := lhsDefs) {
        r.error(e, "Cannot rename field of expression.");
        return {};
    }

    set[Define] containerDefs = toRel(tm.definitions)[lhsDefs];
    if ({} := containerDefs) {
        r.error(n, "Cannot rename field of a definition in another module.");
        return {};
    }
    set[AType] containerDefTypes = {di.atype | DefInfo di <- containerDefs.defInfo};
    rel[AType, IdRole, Define] definesByType = {<d.defInfo.atype, d.idRole, d> | d <- tm.defines};
    set[Define] containerTypeDefs = definesByType[containerDefTypes, dataOrSyntaxRoles];
    set[Define] fieldDefs = toRel(tm.definitions)[(tm.defines<id, idRole, scope, defined>)["<n>", fieldId(), containerTypeDefs.defined]];

    return fieldDefs;
}

tuple[bool, set[Define]] getCursorDefinitions(hasExpr:(Expression) `<Expression e> has <Name n>`, TModel tm, Tree(loc) _, TModel(Tree) _, Renamer r) {
    fieldDefs = getFieldDefinitions(hasExpr, tm, r);
    return <fieldDefs == {} ? false : true, fieldDefs>;
}

void renameAdditionalUses(set[Define] defs:{<_, str fieldName, _, fieldId(), _, defType(AType fieldType)>, *_}, str newName, TModel tm, Renamer r) {
    if ({loc u, *_} := tm.useDef<0>) {
        visit (r.getConfig().parseLoc(u.top)) {
            case hasExpr:(Expression) `<Expression e> has <Name n>`: {
                if ({} != (getFieldDefinitions(hasExpr, tm, r) & defs)) {
                    r.textEdit(replace(n.src, newName));
                }
            }
        }
    }
}

// Positional fields
tuple[type[Tree] as, str desc] asType(fieldId()) = <#NonterminalLabel, "field name">;

// Keyword fields
tuple[type[Tree] as, str desc] asType(keywordFieldId()) = <#Name, "keyword field name">;

bool isUnsupportedCursor(list[Tree] _:[*_, TypeArg tp, *_, StructuredType _, *_], Renamer r) {
    r.error(tp, "Cannot rename a field from a structured type.");
    return false;
}
