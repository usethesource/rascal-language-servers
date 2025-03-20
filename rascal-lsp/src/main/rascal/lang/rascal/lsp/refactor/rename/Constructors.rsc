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
module lang::rascal::lsp::refactor::rename::Constructors

extend framework::Rename;
import lang::rascal::lsp::refactor::rename::Common;
import lang::rascal::lsp::refactor::rename::Functions;

import lang::rascalcore::check::BasicRascalConfig;

import lang::rascal::lsp::refactor::rename::Types;

import lang::rascal::\syntax::Rascal;
import analysis::typepal::TModel;

import Location;

tuple[set[loc], set[loc], set[loc]] findOccurrenceFilesUnchecked(set[Define] defs:{<_, _, _, constructorId(), _, _>, *_}, list[Tree] cursor, str newName, Tree(loc) getTree, Renamer r) {
    if (size(defs.id) > 1) {
        r.error(cursor[0], "Cannot find files for constructor definitions with multiple names (<defs.id>)");
        return <{}, {}, {}>;
    }

    <curFiles, newFiles> = filterFiles(getSourceFiles(r), allNameSortsFilter("<cursor[0]>", newName), getTree);

    return <curFiles, curFiles, newFiles>;
}

set[Define] findAdditionalDefinitions(set[Define] cursorDefs:{<_, _, _, constructorId(), _, _>, *_}, Tree tr, TModel tm, Renamer r)
    = findAdditionalConstructorDefinitions(cursorDefs, tr, tm, r)
    + findAdditionalFunctionDefinitions(cursorDefs, tm)
    ;

set[Define] findAdditionalConstructorDefinitions(set[Define] cursorDefs, Tree tr, TModel tm, Renamer r) {
    if (otherRoles:{_, *_} := cursorDefs.idRole - {constructorId(), functionId()}) {
        for (<role, d> <- (cursorDefs<idRole, idRole, defined>)[otherRoles]) {
            r.error(d, "Cannot find constructor definitions that overload definition of <role>");
        }
        return {};
    }

    // Find the ADT definitions in this file that these constructors belong to
    adtDefs = {adt | Define adt:<_, _, _, dataId(), _, _> <- tm.defines, any(cons <- cursorDefs, isContainedIn(cons.defined, adt.defined))};
    // Find overloads of this ADT
    adtDefs += findAdditionalDefinitions(adtDefs, tr, tm, r);

    return {d | d <- findConstructorDefinitions(adtDefs), d.id in cursorDefs.id};
}

set[Define] findConstructorDefinitions(set[Define] adtDefs) {
    // Find all constructors with the same name in these ADT definitions
    return flatMapPerFile(adtDefs, set[Define](loc f, set[Define] fileDefs) {
        fileTm = r.getConfig().tmodelForLoc(f);
        return {fileTm.definitions[d]
            | loc d <- (fileTm.defines<idRole, defined>)[constructorId()]
            , any(adt <- fileDefs.defined, isContainedIn(d, adt))
        };
    });
}

tuple[type[Tree] as, str desc] asType(constructorId()) = <#NonterminalLabel, "constructor name">;
