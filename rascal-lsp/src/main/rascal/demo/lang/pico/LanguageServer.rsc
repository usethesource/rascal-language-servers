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
module demo::lang::pico::LanguageServer

import util::LanguageServer;
import util::IDEServices;
import ParseTree;
import util::Reflective;
import lang::pico::\syntax::Main;
import IO;

set[LanguageService] picoLanguageContributor() = {
    parser(parser(#start[Program])),
    outliner(picoOutliner),
    lenses(picoLenses),
    executor(picoCommands),
    inlayHinter(picoHinter),
    definer(lookupDef)
};

set[LanguageService] picoLanguageContributorSlowSummary() = {
    parser(parser(#start[Program])),
    analyzer(picoAnalyzer, providesImplementations = false),
    builder(picoBuilder)
};

list[DocumentSymbol] picoOutliner(start[Program] input)
  = [symbol("<input.src>", DocumentSymbolKind::\file(), input.src, children=[
      *[symbol("<var.id>", \variable(), var.src) | /IdType var := input]
  ])];

Summary picoAnalyzer(loc l, start[Program] input) = picoSummarizer(l, input, analyze());

Summary picoBuilder(loc l, start[Program] input) = picoSummarizer(l, input, build());

data picoSummarizerMode = analyze() | build();

Summary picoSummarizer(loc l, start[Program] input, picoSummarizerMode mode) {
    Summary s = summary(l);

    rel[str, loc] defs = {<"<var.id>", var.src> | /IdType var := input};
    rel[loc, str] uses = {<id.src, "<id>"> | /Id id := input};
    rel[loc, str] docs = {<var.src, "*variable* <var>"> | /IdType var := input};

    // Provide errors (cheap to compute) both in analyze mode and in build mode
    s.messages += {<src, error("<id> is not defined", src)> | <src, id> <- uses, id notin defs<0>};
    s.references += (uses o defs)<1,0>;
    s.definitions += uses o defs;
    s.documentation += (uses o defs) o docs;

    // Provide warnings (expensive to compute) only in build mode
    if (build() := mode) {
        rel[loc, str] asgn = {<id.src, "<id>"> | /Statement stmt := input, (Statement) `<Id id> := <Expression _>` := stmt};
        s.messages += {<src, warning("<id> is not assigned", src)> | <id, src> <- defs, id notin asgn<1>};
    }

    return s;
}

set[loc] lookupDef(loc _, start[Program] input, Tree cursor) =
    { d.src | /IdType d := input, cursor := d.id};


data Command
  = renameAtoB(start[Program] program);

rel[loc,Command] picoLenses(start[Program] input) = {<input@\loc, renameAtoB(input, title="Rename variables a to b.")>};


list[InlayHint] picoHinter(start[Program] input) {
    typeLookup = ( "<name>" : "<tp>" | /(IdType)`<Id name> : <Type tp>` := input);
    return [
        hint(name.src, " : <typeLookup["<name>"]>", \type(), atEnd = true) | /(Expression)`<Id name>` := input, "<name>" in typeLookup
    ];
}

list[DocumentEdit] getAtoBEdits(start[Program] input)
   = [changed(input@\loc.top, [replace(id@\loc, "b") | /id:(Id) `a` := input])];

value picoCommands(renameAtoB(start[Program] input)) {
    applyDocumentsEdits(getAtoBEdits(input));
    return ("result": true);
}

void main() {
    registerLanguage(
        language(
            pathConfig(),
            "Pico",
            {"pico", "pico-new"},
            "demo::lang::pico::LanguageServer",
            "picoLanguageContributor"
        )
    );
    registerLanguage(
        language(
            pathConfig(),
            "Pico",
            {"pico", "pico-new"},
            "demo::lang::pico::LanguageServer",
            "picoLanguageContributorSlowSummary"
        )
    );
}
