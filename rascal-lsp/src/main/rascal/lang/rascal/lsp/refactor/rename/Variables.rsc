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
module lang::rascal::lsp::refactor::rename::Variables

extend framework::Rename;
extend lang::rascal::lsp::refactor::rename::Common;

import lang::rascal::\syntax::Rascal;
import analysis::typepal::TModel;
import lang::rascalcore::check::BasicRascalConfig;

import util::FileSystem;
import util::Maybe;

import IO;

tuple[set[loc], set[loc]] findOccurrenceFiles(set[Define] defs:{<_, _, _, moduleVariableId(), _, _>, *_}, list[Tree] cursor, Tree(loc) getTree, Renamer r) =
    findVarNameOccurrences(cursor, getTree, r);

tuple[set[loc], set[loc]] findOccurrenceFiles(set[Define] defs:{<_, _, _, variableId(), _, _>, *_}, list[Tree] cursor, Tree(loc) getTree, Renamer r) =
    findVarNameOccurrences(cursor, getTree, r);

private tuple[set[loc], set[loc]] findVarNameOccurrences(list[Tree] cursor, Tree(loc) getTree, Renamer r) {
    set[loc] defFiles = {};
    set[loc] useFiles = {};

    str cursorName = "<cursor[0]>";
    for (wsFolder <- r.getConfig().workspaceFolders
       , loc f <- find(printlnExp("Checking folder: ", wsFolder), "rsc")) {
        visit (getTree(printlnExp("  Checking file: ", f))) {
            case Name n:
                if ("<n>" == cursorName) {
                    defFiles += f;
                    useFiles += f;
                }
            case QualifiedName qn:
                if ("<qn.names[-1]>" == cursorName) {
                    // qualified name can only be a use
                    useFiles += f;
                }
        }
    }

    return <defFiles, useFiles>;
}

Maybe[loc] nameLocation(Name n, set[Define] _) = just(n.src);
Maybe[loc] nameLocation(QualifiedName qn, set[Define] _: {<_, _, _, moduleVariableId(), _, _>, *_}) = just(qn.names[-1].src);

tuple[type[Tree] as, str desc] asType(variableId()) = <#Name, "variable name">;
tuple[type[Tree] as, str desc] asType(moduleVariableId()) = <#Name, "variable name">;
tuple[type[Tree] as, str desc] asType(patternVariableId()) = <#Name, "pattern variable name">;
