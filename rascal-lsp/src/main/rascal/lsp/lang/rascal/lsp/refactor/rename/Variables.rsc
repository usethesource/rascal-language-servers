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

extend analysis::typepal::refactor::Rename;
extend lang::rascal::lsp::refactor::rename::Common;
import lang::rascalcore::check::BasicRascalConfig;

import lang::rascal::\syntax::Rascal;
import analysis::typepal::TModel;

import util::Maybe;

 // Variables
tuple[type[Tree] as, str desc] asType(variableId(), _) = <#Name, "variable name">;

tuple[set[loc], set[loc], set[loc]] findOccurrenceFilesUnchecked(set[Define] _:{<loc scope, _, _, variableId(), _, _>}, list[Tree] cursor, str newName, Tree(loc) _, Renamer _) =
    <{scope.top}, {scope.top}, singleNameFilter(newName)(cursor[-1]) ? {scope.top} : {}>;

// Global variables
tuple[type[Tree] as, str desc] asType(moduleVariableId(), _) = <#Name, "variable name">;

tuple[set[loc], set[loc], set[loc]] findOccurrenceFilesUnchecked(set[Define] _:{<loc scope, _, _, moduleVariableId(), _, defType(_, vis=privateVis())>}, list[Tree] cursor, str newName, Tree(loc) getTree, Renamer r) {
    <curUseFiles, newFiles> = filterFiles(getSourceFiles(r), "<cursor[0]>", newName, getTree);
    return <{scope.top}, curUseFiles, newFiles>;
}

// Pattern variables
tuple[type[Tree] as, str desc] asType(patternVariableId(), _) = <#Name, "pattern variable name">;

tuple[set[loc], set[loc], set[loc]] findOccurrenceFilesUnchecked(set[Define] _:{<loc scope, _, _, patternVariableId(), _, _>}, list[Tree] cursor, str newName, Tree(loc) _, Renamer _) =
    <{scope.top}, {scope.top}, singleNameFilter(newName)(cursor[-1]) ? {scope.top} : {}>;
