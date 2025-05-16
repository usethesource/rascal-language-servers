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
import lang::rascalcore::check::BuiltinFields;
import lang::rascalcore::check::Import;

import lang::rascal::lsp::refactor::rename::Constructors;
import lang::rascal::lsp::refactor::rename::Types;

import lang::rascal::\syntax::Rascal;

import analysis::typepal::Collector;
import analysis::typepal::TModel;
import analysis::diff::edits::TextEdits;

import Map;
import util::Maybe;

set[IdRole] fieldRoles = {fieldId(), keywordFieldId(), keywordFormalId()};
bool isFieldRole(IdRole role) = role in fieldRoles;

set[Define] findAdditionalDefinitions(set[Define] cursorDefs, Tree tr, TModel tm, Renamer r) {
    if (any(role <- cursorDefs.idRole, !isFieldRole(role)) || {} := cursorDefs) fail findAdditionalDefinitions;

    adtDefs = {tm.definitions[d] | loc d <- (tm.defines<idRole, defined, defined>)[dataOrSyntaxRoles, cursorDefs.scope]};
    adtDefs += findAdditionalDefinitions(adtDefs, tr, tm, r);

    // Find all fields with the same name in these ADT definitions
    return getFieldDefinitions(adtDefs, cursorDefs<idRole, id>, r.getConfig().tmodelForLoc);
}

@synopsis{Collect all definitions for field <fieldName> in ADT/collection/tuple by definition.}
set[Define] getFieldDefinitions(set[Define] containerDefs, rel[IdRole, str] fields, TModel(loc) getModel)
    = flatMapPerFile(containerDefs, set[Define](loc f, set[Define] localContainerDefs) {
        localTm = getModel(f);
        candidateDefs = {*(localTm.defines<idRole, id, scope, defined>[role, name]) | <role, name> <- fields};
        return {localTm.definitions[d] | loc d <- candidateDefs[localContainerDefs.defined]};
    });

@synopsis{Collect all definitions for the field <fieldName> by ADT/constructor type.}
set[Define] getFieldDefinitions(set[AType] containerTypes, str fieldName, TModel tm, set[loc] reachableFromUse, TModel(loc) getModel) {
    rel[AType, IdRole, Define] definesByType = {<d.defInfo.atype, d.idRole, d> | d <- tm.defines, d.defInfo.atype?};
    // Find all type-like definitions (but omit variable definitions etc.)
    set[Define] containerTypeDefs = definesByType[containerTypes, dataOrSyntaxRoles];
    if (dataId() in containerTypeDefs.idRole) {
        // Find overloads
        for (loc modScope <- reachableFromUse) {
            containerTypeDefs += findAdditionalDataLikeDefinitions(containerTypeDefs, getModel(modScope.top), getModel);
        }
    }
    // Since we do not know (based on tree) what kind of field role (positional, keyword) we are looking for, select them all
    return getFieldDefinitions(containerTypeDefs, {<role, fieldName> | role <- fieldRoles}, getModel);
}

@synopsis{Collect all definitions for the field <fieldName> in ADT/collection/tuple by tree.}
set[Define] getFieldDefinitions(Tree container, str fieldName, TModel tm, TModel(loc) getModel) {
    if (defs:{_, *_} := tm.useDef[container.src]) {
        return flatMapPerFile(defs, set[Define](loc f, set[loc] localContainerDefs) {
            fileTm = getModel(f);

            set[Define] containerDefs = {fileTm.definitions[d] | loc d <- localContainerDefs, fileTm.definitions[d]?};
            // Find the type of the container. For a constructor value, the type is its ADT type.
            set[AType] containerDefTypes = {acons(AType adt, _, _) := di.atype ? adt : di.atype | DefInfo di <- containerDefs.defInfo};
            return getFieldDefinitions(containerDefTypes, fieldName, fileTm, rascalGetReflexiveModulePaths(tm).to, getModel);
        });
    } else if (just(AType containerType) := getFact(tm, container.src)) {
        return getFieldDefinitions({containerType}, fieldName, tm, rascalGetReflexiveModulePaths(tm).to, getModel) ;
    }

    return {};
}

@synopsis{Add artificial definitions and use/def relations for fields, until they exist in the TModel.}
TModel augmentFieldUses(Tree tr, TModel tm, TModel(loc) getModel) {
    // Make sure that everyone receives the (partially) augmented TModel from here on
    TModel getAugmentedModel(loc l) = (l == tr.src.top) ? tm : getModel(l);

    void addDef(Define d) { tm = tm[defines = tm.defines + d][definitions = tm.definitions + (d.defined: d)]; }
    void addUseDef(loc use, loc def) { tm = tm[useDef = tm.useDef + <use, def>]; }
    void removeUseDef(loc use, loc def) { tm = tm[useDef = tm.useDef - <use, def>]; }

    void addFieldUse(Tree container, Tree fieldName) {
        // Common/ADT keyword field uses currently point to their scope (i.e. parent ADT definition) instead of the field definition
        // https://github.com/usethesource/rascal/issues/2172?issue=usethesource%7Crascal%7C2186
        for (Define field <- getFieldDefinitions(container, "<fieldName>", tm, getAugmentedModel)) {
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
        case (Expression) `<Expression e>.<Name n>`: addFieldUse(e, n);
        case (Assignable) `<Assignable rec>.<Name n>`: addFieldUse(rec, n);
        case (Expression) `<Expression e>(<{Expression ","}* _> <KeywordArguments[Expression] kwArgs>)`:
            for (/(KeywordArgument[Expression]) `<Name n> = <Expression _>` := kwArgs) addFieldUse(e, n);
        case (Pattern) `<Pattern e>(<{Pattern ","}* _> <KeywordArguments[Pattern] kwArgs>)`:
            for (/(KeywordArgument[Pattern]) `<Name n> = <Pattern _>` := kwArgs) addFieldUse(e, n);
        case st:(StructuredType) `<BasicType _>[<{TypeArg ","}+ args>]`: {
            if (just(AType tp) := getFact(tm, st.src)) {
                // It is convenient to wrap the collection's fields in a 'type'definition, like with ADTs
                // This definition serves (only) that purpose
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

bool isUnsupportedCursor(list[Tree] _: [*_, Name n1, *_, (Expression) `<Expression _> has <Name n2>`, *_], Renamer _) = (n1 := n2);

bool isUnsupportedCursor(list[Tree] _: [*_, Name n1, *_, (Expression) `<Expression e>.<Name n2>`,*_], TModel tm, Renamer r) {
    builtinFields = getBuiltinFieldMap();
    if (just(AType lhsType) := getFact(tm, e.src), builtinFields[lhsType]?) {
        for (fieldName <- domain(builtinFields[lhsType])) {
            if (n1 := [Name] fieldName, n2 := n1) {
                r.error(n1, "Cannot rename builtin field \'<fieldName>\'");
                return true;
            }
        }
    }
    return false;
}

void renameAdditionalUses(set[Define] fieldDefs, str newName, TModel tm, Renamer r) {
    if (any(role <- fieldDefs.idRole, !isFieldRole(role)) || {} := fieldDefs) fail renameAdditionalUses;

    loc mloc = getModuleScopes(tm)[tm.modelName].top;
    Tree tr = r.getConfig().parseLoc(mloc);
    visit (tr) {
        case (Expression) `<Expression e> has <Name n>`: {
            eFieldDefs = getFieldDefinitions(e, "<n>", tm, r.getConfig().tmodelForLoc);
            if (size(fieldDefs & eFieldDefs) > 0) {
                fieldName = "<n>";
                if (fieldName in fieldDefs.id) {
                    r.textEdit(replace(n.src, newName, annotation = changeAnnotation("Use of `has <fieldName>` on value of <describeFact(getFact(tm, e.src))>", "Due to the dynamic nature of these names, please review these suggested changes.", needsConfirmation = true)));
                }
            }
        }
    }
}
