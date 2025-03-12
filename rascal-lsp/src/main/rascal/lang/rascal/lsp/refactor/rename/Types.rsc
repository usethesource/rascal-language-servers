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

import util::Maybe;

tuple[set[loc], set[loc], set[loc]] findOccurrenceFilesUnchecked(set[Define] defs:{<_, _, _, dataId(), _, _>, *_}, list[Tree] cursor, str newName, Tree(loc) getTree, Renamer r) =
    findDataLikeOccurrenceFilesUnchecked(defs, cursor, newName, getTree, r);

public tuple[set[loc], set[loc], set[loc]] findDataLikeOccurrenceFilesUnchecked(set[Define] defs, list[Tree] cursor, str newName, Tree(loc) getTree, Renamer r) {
    if ({_, _, *_} := defs.id) {
        r.error(cursor[0], "Cannot find files for ADT definitions with multiple names (<defs.id>)");
        return <{}, {}, {}>;
    }

    <curAdtFiles, newFiles> = filterFiles(getSourceFiles(r), allNameSortsFilter("<cursor[0]>", newName), getTree);

    consIds = {consId
        | Define _:<_, _, _, _, loc dataDef, defType(AType adtType)> <- defs
        , tm := r.getConfig().tmodelForTree(getTree(dataDef.top))
        , Define _:<_, str consId, _, constructorId(), _, defType(acons(adtType, _, _))> <- tm.defines
    };

    consFiles = {*filterFiles(getSourceFiles(r), allNameSortsFilter(consId), getTree) | consId <- consIds};

    return <curAdtFiles + consFiles, curAdtFiles, newFiles>;
}

set[Define] findAdditionalDefinitions(set[Define] cursorDefs:{<_, _, _, dataId(), _, _>, *_}, Tree tr, TModel tm, Renamer r) =
    findAdditionalDataLikeDefinitions(cursorDefs, tr, tm, r);

public set[Define] findAdditionalDataLikeDefinitions(set[Define] cursorDefs, Tree tr, TModel tm, Renamer r) {
    reachable = reachableModuleScopes(tm);
    loc currentLoc = tr.src.top;

    if ({IdRole role} := cursorDefs.idRole, role in dataOrSyntaxRoles) {
        if ({str id} := cursorDefs.id) {
            set[Define] defs = {};
            if ({_, *_} := (cursorDefs.scope & reachable)) {
                definitions = (d.defined: d | d <- cursorDefs) + tm.definitions;
                defs += {d
                    | Define d:<_, id, _, role, _, _> <- tm.defines
                    , rascalMayOverloadSameName(cursorDefs.defined + d.defined, definitions)
                };

                for (modScope <- reachable, modScope.top != currentLoc) {
                    fileTr = r.getConfig().parseLoc(modScope.top);
                    fileTm = r.getConfig().tmodelForTree(fileTr);
                    defs += findAdditionalDataLikeDefinitions(cursorDefs + defs, fileTr, fileTm, r);
                }
            }
            return defs;
        } else {
            for (loc d <- cursorDefs.defined) {
                r.error(d, "Cannot find overloads of definitions with names (<cursorDefs.id>)");
            }
        }
    } else {
        for (loc d <- cursorDefs.defined) {
            r.error(d, "Cannot find overload for definitions with roles (<cursorDefs.idRole>)");
        }
    }
    return {};
}

tuple[type[Tree] as, str desc] asType(aliasId()) = <#Name, "type name">;
tuple[type[Tree] as, str desc] asType(annoId()) = <#Name, "annotation name">;
tuple[type[Tree] as, str desc] asType(dataId()) = <#Name, "ADT name">;
