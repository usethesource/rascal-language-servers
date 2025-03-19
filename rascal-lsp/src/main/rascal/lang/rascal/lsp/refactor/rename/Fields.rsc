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

import lang::rascalcore::check::ATypeBase;
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

set[Define] getFieldDefinitions(Tree container, str fieldName, TModel tm, TModel(loc) getModel)
    = flatMapPerFile(tm.useDef[container.src], set[Define](loc f, set[loc] fileDefs) {
        fileTm = f == container.src.top ? tm : getModel(f);
        defRel = toRel(fileTm.definitions);

        set[Define] containerDefs = defRel[fileDefs];
        set[AType] containerDefTypes = {acons(AType adt, _, _) := di.atype ? adt : di.atype | DefInfo di <- containerDefs.defInfo};
        rel[AType, IdRole, Define] definesByType = {<d.defInfo.atype, d.idRole, d> | d <- fileTm.defines};
        set[Define] containerTypeDefs = definesByType[containerDefTypes, dataOrSyntaxRoles];
        set[loc] fieldDefs = (fileTm.defines<id, idRole, scope, defined>)[fieldName, fieldRoles, containerTypeDefs.defined];

        return defRel[fieldDefs];
    });

tuple[bool, set[Define]] getCursorDefinitions((Expression) `<Expression e> has <Name n>`, list[Tree] _, TModel tm, Renamer r) =
    <true, getFieldDefinitions(e, "<n>", tm, r.getConfig().tmodelForLoc)>;

tuple[bool, set[Define]] getCursorDefinitions((Expression) `<Expression e>.<Name n>`, list[Tree] _, TModel tm, Renamer r) =
    <true, getFieldDefinitions(e, "<n>", tm, r.getConfig().tmodelForLoc)>;

tuple[bool, set[Define]] getCursorDefinitions((Assignable) `<Assignable rec>.<Name n>`, list[Tree] _, TModel tm, Renamer r) =
    <true, getFieldDefinitions(rec, "<n>", tm, r.getConfig().tmodelForLoc)>;

tuple[bool, set[Define]] getCursorDefinitions(Name n, list[Tree] _:[*_, (Expression) `<Expression e> ( <{Expression ","}* args> <KeywordArguments[Expression] kwArgs> )`, *_], TModel tm, Renamer r) =
    <true, getFieldDefinitions(e, "<n>", tm, r.getConfig().tmodelForLoc)>
    when kwArgs is \default, kwArg <- kwArgs.keywordArgumentList, n := kwArg.name;

tuple[bool, set[Define]] getCursorDefinitions(Name n, list[Tree] _:[*_, (Pattern) `<Pattern e> ( <{Pattern ","}* args> <KeywordArguments[Pattern] kwArgs> )`, *_], TModel tm, Renamer r) =
    <true, getFieldDefinitions(e, "<n>", tm, r.getConfig().tmodelForLoc)>
    when kwArgs is \default, kwArg <- kwArgs.keywordArgumentList, n := kwArg.name;

tuple[bool, set[Define]] getCursorDefinitions(Name n, list[Tree] _:[*_, (Expression) `<Expression e>\<<{Field ","}+ fields>\>`, *_], TModel tm, Renamer r)
    = <true, getFieldDefinitions(e, "<n>", tm, r.getConfig().tmodelForLoc)>
    when name(n) <- fields;

TModel augmentFieldUses(Tree tr, TModel tm, TModel(loc) getModel) {
    void addDef(Define d) {
        tm = tm[defines = tm.defines + d]
               [definitions = tm.definitions + (d.defined: d)];
    }

    void addUseDef(loc use, loc def) {
        tm = tm[useDef = tm.useDef + <use, def>];
    }

    void removeUseDef(loc use, loc def) {
        tm = tm[useDef = tm.useDef - <use, def>];
    }

    void addFieldUse(Tree container, Tree fieldName) {
        fieldDefs = getFieldDefinitions(container, "<fieldName>", tm, getModel);
        // Common/ADT keyword field uses currently point to their parent ADT definition instead of the field definition
        // https://github.com/usethesource/rascal/issues/2172?issue=usethesource%7Crascal%7C2186
        for (Define field <- fieldDefs) {
            removeUseDef(fieldName.src, field.scope);
            addUseDef(fieldName.src, field.defined);
        }
    }

    void addCollectionFieldDef(Tree _, (TypeArg) `<Type _>`) {}
    void addCollectionFieldDef(Tree structuredType, (TypeArg) `<Type fieldType> <Name fieldName>`) {
        if (just(AType containerType) := getFact(tm, structuredType.src)) {
            addDef(<structuredType.src, "<fieldName>", "<fieldName>", fieldId(), fieldName.src, defType(aparameter("<fieldName>", containerType))>);
        }
    }

    visit (tr) {
        case (Expression) `<Expression e> has <Name n>`: addFieldUse(e, n);
        case (Expression) `<Expression e>.<Name n>`: addFieldUse(e, n);
        case (Assignable) `<Assignable rec>.<Name n>`: addFieldUse(rec, n);
        case (Expression) `<Expression e>(<{Expression ","}* _> <KeywordArguments[Expression] kwArgs>)`:
            for (/(KeywordArgument[Expression]) `<Name n> = <Expression _>` := kwArgs) addFieldUse(e, n);
        case (Pattern) `<Pattern e>(<{Pattern ","}* _> <KeywordArguments[Pattern] kwArgs>)`:
            for (/(KeywordArgument[Pattern]) `<Name n> = <Pattern _>` := kwArgs) addFieldUse(e, n);
        case st:(StructuredType) `<BasicType _>[<{TypeArg ","}+ args>]`: {
            if (just(AType tp) := getFact(tm, st.src)) {
                addDef(<tm.scopes[parentScope(st.src, tm)], "<st>", "<st>", aliasId(), st.src, defType(tp)>);
                for (TypeArg arg <- args) addCollectionFieldDef(st, arg);
            }
        }
        case (Expression) `<Expression e>\<<{Field ","}+ fields>\>`:
            for (name(Name n) <- fields) addFieldUse(e, n);
        case (Expression) `<Expression e>[<Name n> = <Expression _>]`: addFieldUse(e, n);
    }
    return tm;
}

// Positional fields
tuple[type[Tree] as, str desc] asType(fieldId()) = <#NonterminalLabel, "field name">;

// Keyword fields
tuple[type[Tree] as, str desc] asType(keywordFieldId()) = <#Name, "keyword field name">;
