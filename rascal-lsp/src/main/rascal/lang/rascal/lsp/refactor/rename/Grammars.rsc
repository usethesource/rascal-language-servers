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
module lang::rascal::lsp::refactor::rename::Grammars

extend framework::Rename;
import lang::rascal::lsp::refactor::Rename;

import lang::rascal::\syntax::Rascal;
import lang::rascalcore::check::BasicRascalConfig;

data Tree;

void renameDefinitionUnchecked(Define d: <_, _, _, nonterminalId(), _, _>, loc _, str _, Tree _, TModel _, Renamer _) {
    // Do not register an edit for the definition, as it will appear as a use again
    // TODO Rascal Core: why register the name of a production definition as a use of itself?
}

void renameDefinitionUnchecked(Define d: <_, _, _, lexicalId(), _, _>, loc _, str _, Tree _, TModel _, Renamer _) {
    // Do not register an edit for the definition, as it will appear as a use again
    // TODO Rascal Core: why register the name of a production definition as a use of itself?
}

tuple[type[Tree] as, str desc] asType(nonterminalId()) = <#Nonterminal, "production name">;
tuple[type[Tree] as, str desc] asType(lexicalId()) = <#Nonterminal, "production name">;
