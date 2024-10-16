@license{
Copyright (c) 2018-2023, NWO-I CWI and Swat.engineering
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
@synopsis{Defines both the command evaluator and the codeAction retriever for Rascal}
module lang::rascal::lsp::Actions

import lang::rascal::\syntax::Rascal;
import util::LanguageServer;
import analysis::diff::edits::TextEdits;
import ParseTree;
import String;
import lang::rascal::vis::ImportGraph;
import util::Reflective;

@synopsis{Here we list Rascal-specific code commands}
@description{
The commands must be evaluated by ((evaluateRascalCommand))
}
data Command
    = visualImportGraphCommand(PathConfig pcfg)
    ;

@synopsis{Detects (on-demand) source actions to register with specific places near the current cursor}
list[CodeAction] rascalCodeActions(Focus focus, PathConfig pcfg=pathConfig()) {
    result = [];

    if ([*_, Toplevel t, *_] := focus) {
        result += toplevelCodeActions(t);
    }

    if ([*_, Header h, *_] := focus) {
        result += [action(command=visualImportGraphCommand(pcfg), title="Visualize project import graph")];
    }

    return result;
}

@synopsis{Rewrite immediate return to expression.}
list[CodeAction] toplevelCodeActions(Toplevel t:
    (Toplevel) `<Tags tags>
               '<Visibility visibility> <Signature signature> {
               '  return <Expression e>;
               '}`) {

    result = (Toplevel) `<Tags tags>
                        '<Visibility visibility> <Signature signature> = <Expression e>;`;

    edits=[changed(t@\loc.top, [replace(t@\loc, trim("<result>"))])];

    return [action(edits=edits, title="Rewrite block return to simpler rewrite rule.", kind=refactor())];
}

default list[CodeAction] toplevelCodeActions(Toplevel _) = [];

@synopsis{Evaluates all commands and quickfixes produced by ((rascalCodeActions)) and the type-checker}
default value evaluateRascalCommand(Command _) =  ("result" : false);

value evaluateRascalCommand(visualImportGraphCommand(PathConfig pcfg)) {
    importGraph(pcfg);
    return ("result" : true);
}

