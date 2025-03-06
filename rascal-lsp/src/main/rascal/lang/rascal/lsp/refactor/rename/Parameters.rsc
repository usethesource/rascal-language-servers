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
import framework::TextEdits;
extend lang::rascal::lsp::refactor::rename::Common;

import lang::rascal::\syntax::Rascal;
import analysis::typepal::TModel;
import lang::rascalcore::check::BasicRascalConfig;

import Relation;
import Set;
import util::Maybe;

bool isFormalId(IdRole role) = role in formalRoles;

tuple[type[Tree] as, str desc] asType(IdRole idRole) = <#Name, "formal parameter name"> when isFormalId(idRole);

private set[loc] rascalGetKeywordArgs(none()) = {};
private set[loc] rascalGetKeywordArgs(\default(_, {KeywordArgument[Pattern] ","}+ keywordArgs), str argName) =
    { kwArg.name.src
    | kwArg <- keywordArgs
    , "<kwArg.name>" == argName};
private set[loc] rascalGetKeywordArgs(\default(_, {KeywordArgument[Expression] ","}+ keywordArgs), str argName) =
    { kwArg.name.src
    | kwArg <- keywordArgs
    , "<kwArg.name>" == argName};

void renameAdditionalUses(set[Define] defs:{<_, id, _, keywordFormalId(), _, _>, *_}, str newName, TModel tm, Renamer r) {
    if (size(defs.id) > 1) {
        for (loc l <- defs.defined) {
            r.error(l, "Cannot rename multiple names at once (<defs.id>)");
        }
        return;
    }

    // We get the module location from the uses. If there are no uses, this is skipped.
    // That's intended, since this function is only supposed to rename uses.
    if ({loc u, *_} := tm.useDef<0>) {
        set[Define] funcDefs = {d | d:<_, _, _, functionId(), _, _> <- tm.defines, d.defined in defs.scope};
        set[loc] funcCalls = invert(tm.useDef)[funcDefs.defined];

        // TODO Typepal: if the TModel would register kw arg names at call sites as uses, this tree visit would not be necessary
        Tree tr = r.getConfig().parseLoc(u.top);
        visit (tr) {
            case (Expression) `<Expression e>(<{Expression ","}* _> <KeywordArguments[Expression] kwArgs>)`: {
                if (e.src in funcCalls) {
                    funcCalls -= e.src;
                    for (loc ul <- rascalGetKeywordArgs(kwArgs, id)) {
                        r.textEdit(replace(ul, newName));
                    }
                }
            }
            case (Pattern) `<Pattern e>(<{Pattern ","}* _> <KeywordArguments[Pattern] kwArgs>)`: {
                if (e.src in funcCalls) {
                    funcCalls -= e.src;
                    for (loc ul <- rascalGetKeywordArgs(kwArgs, id)) {
                        r.textEdit(replace(ul, newName));
                    }
                }
            }
        }
    }
}
