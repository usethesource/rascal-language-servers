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

set[IdRole] keywordFieldRoles = {keywordFieldId(), keywordFormalId()};
set[IdRole] fieldRoles = {fieldId()} + keywordFieldRoles;

bool isFieldRole(IdRole role) = role in fieldRoles;

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

set[Define] getFieldDefinitions(Tree container, str fieldName, TModel tm, Renamer r) {
    lhsDefs = tm.useDef[container.src];

    if ({} := lhsDefs) {
        r.error(e, "Cannot rename field of expression.");
        return {};
    }

    return flatMapPerFile(lhsDefs, set[Define](loc f, set[loc] fileDefs) {
        fileTm = r.getConfig().tmodelForLoc(f);
        defRel = toRel(fileTm.definitions);

        set[Define] containerDefs = defRel[lhsDefs];
        set[AType] containerDefTypes = {acons(AType adt, _, _) := di.atype ? adt : di.atype | DefInfo di <- containerDefs.defInfo};
        rel[AType, IdRole, Define] definesByType = {<d.defInfo.atype, d.idRole, d> | d <- fileTm.defines};
        set[Define] containerTypeDefs = definesByType[containerDefTypes, dataOrSyntaxRoles];
        set[loc] fieldDefs = (fileTm.defines<id, idRole, scope, defined>)[fieldName, fieldRoles, containerTypeDefs.defined];

        return defRel[fieldDefs];
    });
}

tuple[bool, set[Define]] getCursorDefinitions((Expression) `<Expression e> has <Name n>`, list[Tree] _, TModel tm, Renamer r) =
    <true, getFieldDefinitions(e, "<n>", tm, r)>;

tuple[bool, set[Define]] getCursorDefinitions((Expression) `<Expression e>.<Name n>`, list[Tree] _, TModel tm, Renamer r) =
    <true, getFieldDefinitions(e, "<n>", tm, r)>;

tuple[bool, set[Define]] getCursorDefinitions((Assignable) `<Assignable rec>.<Name n>`, list[Tree] _, TModel tm, Renamer r) =
    <true, getFieldDefinitions(rec, "<n>", tm, r)>;

tuple[bool, set[Define]] getCursorDefinitions(Name n, list[Tree] _:[*_, (Expression) `<Expression e> ( <{Expression ","}* args> <KeywordArguments[Expression] kwArgs> )`, *_], TModel tm, Renamer r) =
    <true, getFieldDefinitions(e, "<n>", tm, r)>
    when kwArgs is \default, kwArg <- kwArgs.keywordArgumentList, n := kwArg.name;

tuple[bool, set[Define]] getCursorDefinitions(Name n, list[Tree] _:[*_, (Pattern) `<Pattern e> ( <{Pattern ","}* args> <KeywordArguments[Pattern] kwArgs> )`, *_], TModel tm, Renamer r) =
    <true, getFieldDefinitions(e, "<n>", tm, r)>
    when kwArgs is \default, kwArg <- kwArgs.keywordArgumentList, n := kwArg.name;

void renameAdditionalFieldUses(set[Define] defs:{<_, _, _, IdRole role, _, _>, *_}, str newName, TModel tm, Renamer r) {
    void renameFieldUse(Tree container, Name fieldName, Maybe[ChangeAnnotation] annotation = nothing()) {
        fieldDefs = getFieldDefinitions(container, "<fieldName>", tm, r);
        if ((fieldDefs & defs) != {}) {
            TextEdit te = annotation is nothing
                ? replace(fieldName.src, newName)
                : replace(fieldName.src, newName, annotation = annotation.val);
            r.textEdit(te);
        }
    }

    loc f = getModuleFile(tm);
    visit (r.getConfig().parseLoc(f)) {
        case (Expression) `<Expression e> has <Name n>`:
            renameFieldUse(e, n, annotation = just(changeAnnotation("Use of `has` \'<"<n>">\'", "", needsConfirmation = true)));
        case (Expression) `<Expression e>.<Name n>`:
            if (keywordFormalId() := role) renameFieldUse(e, n);
        case (Assignable) `<Assignable rec>.<Name n>`:
            if (keywordFormalId() := role) renameFieldUse(rec, n);
        case (Expression) `<Expression e>(<{Expression ","}* _> <KeywordArguments[Expression] kwArgs>)`:
            if (role in keywordFieldRoles, /(KeywordArgument[Expression]) `<Name n> = <Expression _>` := kwArgs) renameFieldUse(e, n);
        case (Pattern) `<Pattern e>(<{Pattern ","}* _> <KeywordArguments[Pattern] kwArgs>)`:
            if (role in keywordFieldRoles, /(KeywordArgument[Pattern]) `<Name n> = <Pattern _>` := kwArgs) renameFieldUse(e, n);
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
