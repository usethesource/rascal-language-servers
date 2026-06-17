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
module lang::rascal::lsp::Analyzer

import IO;
import String;
import ParseTree;
import util::IDEServices;
import util::PathConfig;
import lang::rascal::\syntax::Rascal;
import lang::rascal::lsp::Actions;


@synopsis{A fast analyzer, is run on most parse trees, so it should be fast}
list[Message] analyze(start[Module] tree, PathConfig(loc file) getPathConfig) {
    result = [];

    annotationAlreadyReported = false;
    void reportAnnotationDeprecation(Tree t) {
        if (!annotationAlreadyReported) {
            annotationAlreadyReported = true;
            if (isWritable(t.src.top)) {
                // only reporting issues for code we can rewrite
                result += warning(
                    "annotations are no longer supported and will soon be removed, please use our build-in quick-fix to refactor them into keyword parameters",
                    t.src, fixes=[
                        action(
                            command=upgradeAnnotations(getPathConfig(t.src.top)),
                            title="Upgrade all annotations to keyword fields in this project (annotation syntax is no longer supported)."
                        )
                    ]
                );
            }
        }
        // since the single quickfix fixes the whole project, it's not useful to report it multiple times
        // especially since a user might click "fix all" and then the upgrade would be run several times.
        // instead we ignore all follow up cases of the same error
    }

    visit (tree) {
        case l:(LocationLiteral)`|lib://<PathPart _>`:
            result += error("lib scheme is not supported anymore. In most cases it can be replaced by either |project://|, |mvn://| or IO::getResource", l.src);


        // annotation cases
        case t:(Declaration) `<Tags _> <Visibility _> anno <Type _> <Type _> @ <Name _>;`: reportAnnotationDeprecation(t);
        case t:(Expression)`<Expression _>[@ <Name _> = <Expression _>]`: reportAnnotationDeprecation(t);
        case t:(Expression) `<Expression _>@<Name _>`: reportAnnotationDeprecation(t);
        case t:(Assignable) `<Assignable _>@<Name _>`: reportAnnotationDeprecation(t);
        case t:(Expression) `delAnnotations(<Expression _>)`: reportAnnotationDeprecation(t);
        case t:(Expression) `delAnnotationsRec(<Expression _>)`: reportAnnotationDeprecation(t);
        case t:(Expression) `delAnnotation(<Expression _>, <Expression _>)`: reportAnnotationDeprecation(t);
        case t:(Catch) `catch NoSuchAnnotation(<Pattern _>) : <Statement _>`: reportAnnotationDeprecation(t);
    }
    return result;
}

