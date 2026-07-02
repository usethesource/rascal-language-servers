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
    parsing(jsonParser(true), usesSpecialCaseHighlighting = false),
    completion(jsonCompletion, additionalTriggerCharacters = [",", "{", "[", ":"])
};

list[CompletionItem] builtinLiteralCompletion(int cc)
    = [completionItem(constant(), completionEdit(cc, cc, cc, l), l) | l <- {"true", "false"}]
    + [completionItem(constant(), completionEdit(cc, cc, cc, "null"), "null")]
    ;

list[CompletionItem] usedLiteralCompletion(Tree t, int cc)
    = [completionItem(constant(), completionEdit(cc, cc, cc, v), v) | v <- collectLiterals(t)]
    ;

list[CompletionItem] usedKeyCompletion(Tree t, int cc)
    = [completionItem(key(), completionEdit(cc, cc, cc, v), v) | v <- collectKeys(t)]
    ;

list[CompletionItem] objectCompletion(int cc)
    = [completionItem(struct(), completionEdit(cc, cc, cc, "{${1:\"key\"}: ${2:\"value\"}}$0", snippet=true), "object")]
    ;

list[CompletionItem] jsonCompletion(Focus focus, int cursorOffset, character(":"))
    = objectCompletion(cc)
    + builtinLiteralCompletion(cc)
    + usedLiteralCompletion(focus[-1], cc)
    when cc := focus[0].src.begin.column + cursorOffset;

list[CompletionItem] jsonCompletion(Focus focus, int cursorOffset, character(","))
    = usedKeyCompletion(focus[-1], cc)
    when isCompletingObject(focus)
       , cc := focus[0].src.begin.column + cursorOffset;

list[CompletionItem] jsonCompletion(Focus focus, int cursorOffset, character(","))
    = objectCompletion(cc)
    + builtinLiteralCompletion(cc)
    + usedLiteralCompletion(focus[-1], cc)
    when !isCompletingObject(focus)
       , cc := focus[0].src.begin.column + cursorOffset;

list[CompletionItem] jsonCompletion(Focus focus, int cursorOffset, character("{"))
    = [completionItem(key(), completionEdit(cc, cc, cc, "\"$1\"", snippet=true), "key")]
    + usedKeyCompletion(focus[-1], cc)
    when cc := focus[0].src.begin.column + cursorOffset;

default list[CompletionItem] jsonCompletion(Focus _focus, int cursorOffset, CompletionTrigger trigger) = [];

bool isCompletingObject([*_, Object _, *_, Array _, *_]) = true;
bool isCompletingObject([*_, Array _, *_, Object _, *_]) = false;
bool isCompletingObject([*_, Object _, *_]) = true;
bool isCompletingObject([*_, Array _, *_]) = false;
default bool isCompletingObject(Focus _) = false;

set[str] collectLiterals(Tree t)
    = {"<v>" | /NumericLiteral v := t}
    + {"<v>" | /StringLiteral v := t}
    ;

set[str] collectKeys(Tree t)
    = {"<k>" | /(Member) `<StringLiteral k>: <Value _>` := t};

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
    unregisterLanguage("JSON2", {"json2"});
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
