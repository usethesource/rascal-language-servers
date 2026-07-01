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
module testing::lang::pico::LanguageServer

extend demo::lang::pico::LanguageServer;

import Node;


// JSON serialization

data Command
  = testValueEncoding()
  | browseRascalSite()
  | editPico(loc uri)
  | addTodo(loc at)
  | removeTodo(loc at)
  | showWarning(str message, loc at)
  | showContents(str contents)
  | copyFileContents(loc from, loc to)
  ;

@synopsis{Command handler to test JSON serialization of various Rascal value types.}
value testingExecutionService(testValueEncoding()) = (
    "result": [ // list
        ("a": true), // map, str, bool
        {8, 1r2, 3.14, 10e3}, // set, int, rat, real
        char(0), // ADT constructor
        reposition(parse(#IdType, "x: string"), file = |test:///expectation|), // Tree
        |memory://authority/file.ext|, // loc
        $2026-03-19T11:55:54.121+0100$, // datetime
        <[1..3], #int> // tuple, range, reified type
    ]
);

@synopsis{Command handler from the ((browseRascal)) command}
value testingExecutionService(browseRascalSite()) {
    browse(|https://www.rascal-mpl.org|, title="Rascal MPL");
    return ("result": true);
}

@synopsis{Command handler from the ((editPico)) command}
value picoExecutionService(editPico(loc uri)) {
    edit(uri[file = uri.file == "calls.pico" ? "testing.pico" : "calls.pico"]);
    return ("result": true);
}

@synopsis{Command handler from the ((registerDiagnostics)) command}
value testingExecutionService(addTodo(loc at)) {
    registerDiagnostics([info("TODO", at)]);
    return ("result": true);
}

@synopsis{Command handler from the ((unregisterDiagnostics)) command}
value testingExecutionService(removeTodo(loc at)) {
    unregisterDiagnostics([at]);
    return ("result": true);
}

value testingExecutionService(showWarning(str msg, loc at)) {
    showMessage(warning(msg, at));
    logMessage(error("LOG " + msg, at));
    return ("result": true);
}

value testingExecutionService(showContents(str contents)) {
    showInteractiveContent(plainText(contents));
    return ("result": true);
}

value testingExecutionService(copyFileContents(loc from, loc to)) {
    applyDocumentsEdits([changed([replace(to, readFile(from))])]);
    return ("result": true);
}

private loc declOffset(start[Program] input, int off)
    = input.top.decls.decls[off]?
    ? input.top.decls.decls[off].src
    : |unknown:///|;

lrel[loc, Command] testingCodeLensService(start[Program] input)
    = picoCodeLenseService(input)
    // Since
    + [
        <declOffset(input, 0), browseRascalSite(title="Browse Rascal site")>,
        <declOffset(input, 0), editPico(input.src.top, title="Edit another file")>,
        <declOffset(input, 0), addTodo(input.src, title="Register TODO")>,
        <declOffset(input, 0), removeTodo(input.src, title="Unregister TODO")>,
        <declOffset(input, 0), showWarning("Test warning", input.src, title="Show warning")>,
        <declOffset(input, 0), showContents("Some text", title="Show some text")>,
        <declOffset(input, 1), copyFileContents(input.top.src.parent.parent + "json2" + "example.json2", input.top.src.parent.parent + "json2" + "example-copy.json2", title="Copy contents of example.json2")>
    ];

private set[LanguageService] amendContributions(set[LanguageService] contributions, set[LanguageService] replacements)
    = replacements + {c | c <- contributions, getName(c) notin byName}
    when byName := {getName(r) | r <- replacements};

set[LanguageService] testingLanguageServer(bool allowRecovery)
    = amendContributions(picoLanguageServer(), {
        parsing(picoParser(allowRecovery), usesSpecialCaseHighlighting = false),
        execution(picoExecutionService + testingExecutionService),
        codeLens(testingCodeLensService)
    });

set[LanguageService] testingLanguageServerSlowSummary(bool allowRecovery)
    = amendContributions(picoLanguageServerSlowSummary(), {
        parsing(picoParser(allowRecovery), usesSpecialCaseHighlighting = false)
    });

set[LanguageService] testingLanguageServer() = testingLanguageServer(false);
set[LanguageService] testingLanguageServerWithRecovery() = testingLanguageServer(true);

set[LanguageService] testingLanguageServerSlowSummary() = testingLanguageServerSlowSummary(false);
set[LanguageService] testingLanguageServerSlowSummaryWithRecovery() = testingLanguageServerSlowSummary(true);

void register(bool errorRecovery=false) {
    pcfg = getPicoPathConfig();

    // Since there might be an existing registration with a different error recovery setting, we unregister it here first.
    // Note that in a typical usage scenario, `unregisterLanguage` should not be used.
    unregisterLanguage("Pico", {"pico", "pico-new"});
    registerLanguage(
        language(
            pcfg,
            "Pico",
            {"pico", "pico-new"},
            "testing::lang::pico::LanguageServer",
            errorRecovery ? "testingLanguageServerWithRecovery" : "testingLanguageServer"
        )
    );
    registerLanguage(
        language(
            pcfg,
            "Pico",
            {"pico", "pico-new"},
            "testing::lang::pico::LanguageServer",
            errorRecovery ? "testingLanguageServerSlowSummaryWithRecovery" : "testingLanguageServerSlowSummary"
        )
    );
}
