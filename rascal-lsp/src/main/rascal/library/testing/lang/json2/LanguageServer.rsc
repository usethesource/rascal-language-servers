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
module testing::lang::json2::LanguageServer

import lang::json::\syntax::JSON;

import Exception;
import IO;
import ParseTree;
import util::LanguageServer;
import util::ParseErrorRecovery;
import util::PathConfig;
import util::Reflective;

private Tree (str _input, loc _origin) jsonParser(bool allowRecovery) {
    return ParseTree::parser(#start[JSONText], allowRecovery=allowRecovery, filters=allowRecovery ? {createParseErrorFilter(false)} : {});
}

set[LanguageService] jsonLanguageServer() = {
    parsing(jsonParser(true), usesSpecialCaseHighlighting = false)
};

PathConfig getJsonPathConfig() {
    loc root;
    try {
        // Try to resolve the LSP project.
        root = resolveLocation(|project://rascal-lsp|);
    } catch SchemeNotSupported(_): {
        // Otherwise, we are in the nested pico workspace. Resolve the LSP project from there.
        root = resolveLocation(|cwd:///../../../rascal-lsp|);
    }
    return getProjectPathConfig(root, mode=interpreter());
}

void register() {
    pcfg = getJsonPathConfig();
    registerLanguage(
        language(
            pcfg,
            "JSON2",
            {"json2"},
            "testing::lang::json2::LanguageServer",
            "jsonLanguageServer"
        )
    );
}
