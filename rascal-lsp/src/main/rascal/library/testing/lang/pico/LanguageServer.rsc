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

data Command = testValueEncoding();

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

private set[LanguageService] amendContributions(set[LanguageService] contributions, set[LanguageService] replacements)
    = replacements + {c | c <- contributions, getName(c) notin byName}
    when byName := {getName(r) | r <- replacements};

set[LanguageService] testingLanguageServer(bool allowRecovery)
    = amendContributions(picoLanguageServer(allowRecovery), {
        execution(picoExecutionService + testingExecutionService)
    });

set[LanguageService] testingLanguageServerSlowSummary(bool allowRecovery)
    = amendContributions(picoLanguageServerSlowSummary(allowRecovery), {});

set[LanguageService] testingLanguageServer() = testingLanguageServer(false);
set[LanguageService] testingLanguageServerWithRecovery() = testingLanguageServer(true);

set[LanguageService] testingLanguageServerSlowSummary() = testingLanguageServerSlowSummary(false);
set[LanguageService] testingLanguageServerSlowSummaryWithRecovery() = testingLanguageServerSlowSummary(true);

void register(bool errorRecovery=false) {
    registerLanguage(
        language(
            pathConfig(),
            "Pico",
            {"pico", "pico-new"},
            "testing::lang::pico::LanguageServer",
            errorRecovery ? "testingLanguageServerWithRecovery" : "testingLanguageServer"
        )
    );
    registerLanguage(
        language(
            pathConfig(),
            "Pico",
            {"pico", "pico-new"},
            "testing::lang::pico::LanguageServer",
            errorRecovery ? "testingLanguageServerSlowSummaryWithRecovery" : "testingLanguageServerSlowSummary"
        )
    );
}
