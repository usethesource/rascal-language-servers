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
module lang::rascal::lsp::Templates

import IO;
import Location;
import ParseTree;
import String;
import util::PathConfig;
import util::Reflective;

import analysis::diff::edits::TextEdits;

import lang::rascal::\syntax::Rascal;

LanguageFileConfig rascalConfig = fileConfig();

list[FileSystemChange] newModuleTemplates(list[loc] newFiles, PathConfig(loc) getPathConfig) {
    list[FileSystemChange] edits = [];
    for (f <- newFiles) {
        try {
            // check if file is empty
            if (!(f.extension == "rsc" && exists(f) && isBlank(f))) {
                continue;
            }

            template = moduleTemplate(f, getPathConfig);
            edits += changed([replace(resetRange(f), template)]);
        } catch str s: {
            // We're probably outside of a source directory
            ; // Since the IDE has a warning for this, do nothing
            println("Ignored error: <s>");
        } catch e: {
            println("Error while processing new file <f>: <e>");
        }
    }

    return edits;
}

loc resetRange(loc l)
 = l[offset = 0][length = 0][begin = <1, 0>][end = <1, 0>];

bool isBlank(loc l) = isBlank(readFile(l));

bool isBlank(str s)
    = isEmpty(trim(replaceAll(replaceAll(s, "\n", ""), "\r", "")));

str moduleTemplate(loc f, PathConfig(loc) getPathConfig) {
    pcfg = getPathConfig(f);

    // If this throws, we're most likely outside of a source directory.
    // Let this bubble up to our caller.
    qname = srcsModule(f, pcfg, rascalConfig);
    try {
        // Test if the qualified name is valid
        parse(#QualifiedName, qname);
        // VS Code automatically converts newlines to the editor setting on save
        return "module <qname>\n\n";
    } catch ParseError(_): {
        return "module Invalid\n\n// \'<qname>\' is not a valid module name!\n// TODO Fix the path and module name.";
    }
}
