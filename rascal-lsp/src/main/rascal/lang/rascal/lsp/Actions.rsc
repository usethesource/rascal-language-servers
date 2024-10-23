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
import util::IDEServices;
import List;
import IO;

@synopsis{Here we list Rascal-specific code commands}
@description{
The commands must be evaluated by ((evaluateRascalCommand))
}
data Command
    = visualImportGraphCommand(PathConfig pcfg)
    | sortImportsAndExtends(Header h)
    ;

@synopsis{Detects (on-demand) source actions to register with specific places near the current cursor}
list[CodeAction] rascalCodeActions(Focus focus, PathConfig pcfg=pathConfig()) {
    result = [];

    if ([*_, start[Module] top] := focus) {
        result += addLicenseAction(top, pcfg);
    }

    if ([*_, Toplevel t, *_] := focus) {
        result += toplevelCodeActions(t);
    }

    if ([*_, Header h, *_] := focus) {
        result += [action(command=visualImportGraphCommand(pcfg), title="Visualize project import graph")]
               +  [action(command=sortImportsAndExtends(h), title="Sort imports and extends")]
               ;
    }

    return result;
}

@synopsis{Add a license header if there isn't one.}
list[CodeAction] addLicenseAction(start[Module] \module, PathConfig pcfg) {
    Tags tags = \module.top.header.tags;

    if ((Tags) `<Tag*_ > @license<TagString _> <Tag* _>` !:= tags) {
        license = findLicense(pcfg);
        if (license != "") {
            license = "@license{
                      '<license>
                      '}\n";
            return [action(edits=[makeLicenseEdit(\module@\loc, license)], title="Add missing license header")];
        }
    }

    return [];
}

private str findLicense(PathConfig pcfg) {
    for (loc src <- pcfg.srcs) {
        while (!exists(src + "pom.xml") && src.path != "" && src.path != "/") {
            src = src.parent;
        }

        if (exists(src + "LICENSE")) {
            return trim(readFile(src + "LICENSE"));
        }
        else if (exists(src + "LICENSE.md")) {
            return trim(readFile(src + "LICENSE.md"));
        }
    }

    return "";
}

private DocumentEdit makeLicenseEdit(loc \module, str license)
    = changed(\module.top, [replace(\module.top(0, 0), license)]);

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

value evaluateRascalCommand(sortImportsAndExtends(Header h)) {
    extends = [trim("<i>") | i <- h.imports, i is \extend];
    imports = [trim("<i>") | i <- h.imports, i is \default];
    grammar = [trim("<i>") | i <- h.imports, i is \syntax];

    newHeader = "<for (i <- sort(extends)) {><i>
                '<}>"[..-1] +
                "<if (extends != []) {>
                '
                '<}><for (i <- sort(imports)) {><i>
                '<}>"[..-1] +
                "<if (imports != [] && grammar != []) {>
                '
                '<}><for (i <- grammar) {><i>
                '
                '<}>"[..-2];

    applyDocumentsEdits([changed(h@\loc.top, [replace(h.imports@\loc, newHeader)])]);
    return ("result":true);
}
