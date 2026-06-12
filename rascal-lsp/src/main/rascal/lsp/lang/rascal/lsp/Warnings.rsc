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
module lang::rascal::lsp::Warnings

import ParseTree;
import util::IDEServices;
import util::PathConfig;
import lang::rascal::\syntax::Rascal;
import lang::rascal::lsp::Actions;


list[Message] rascalWarnings(start[Module] tree, PathConfig pcfg=pathConfig()) {
    result = [];
    top-down-break visit (tree) {
        case l:(LocationLiteral)`|lib://<PathPart _>`: {
            result += error("lib scheme is not supported anymore. In most cases it can be replaced by either |project://|, |mvn://| or IO::getResource", l.src);
        }
        // other warnings
        case Tree t : {
            if (isFixableAnnoSyntax(t)) {
                result += warning("annotations are no longer supported and will soon be removed, please use our build-in quick-fix to refactor them into keyword parameters", t.src, fixes=[action(command=upgradeAnnotations(pcfg), title=annoFixTitle)]);
            }
            else {
                fail;
            }
        }
    }
    return result;
}
