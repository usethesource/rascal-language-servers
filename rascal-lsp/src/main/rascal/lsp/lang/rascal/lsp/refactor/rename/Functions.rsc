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
module lang::rascal::lsp::refactor::rename::Functions

extend analysis::typepal::refactor::Rename;
import lang::rascal::lsp::refactor::rename::Common;
import lang::rascal::lsp::refactor::rename::Constructors;

import lang::rascal::\syntax::Rascal;
import analysis::typepal::TModel;

import lang::rascalcore::check::ATypeBase;
import lang::rascalcore::check::BasicRascalConfig;

import util::Maybe;

bool isUnsupportedCursor(list[Tree] cursor, set[Define] cursorDefs:{<_, _, _, functionId(), _, _>, *_}, TModel _, Renamer r) {
    bool unsupported = false;
    for (d <- cursorDefs, d.defInfo.atype is afunc, "java" in d.defInfo.modifiers) {
        unsupported = true;
        r.msg(error(d.defined, "Unsupported: renaming a function implemented in Java."));
    }
    return unsupported;
}

set[Define] findAdditionalDefinitions(set[Define] cursorDefs:{<_, _, _, functionId(), _, _>, *_}, Tree tr, TModel tm, Renamer r)
    = findAdditionalFunctionDefinitions(cursorDefs, tm)
    + findAdditionalConstructorDefinitions(cursorDefs, tr, tm, r)
    ;

set[Define] findAdditionalFunctionDefinitions(set[Define] cursorDefs, TModel tm) =
    {tm.definitions[d] | loc d <- (tm.defines<idRole, defined>)[functionId()], rascalMayOverloadSameName(cursorDefs.defined + d, tm.definitions)};

tuple[type[Tree] as, str desc] asType(functionId(), _) = <#Name, "function name">;
