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
// @deprecated{This demo has been superseded by ((NewLanguageServer)) which avoids the use of deprecated API.}
module demo::lang::pico::LanguageServer

import util::LanguageServer;
import util::IDEServices;
import ParseTree;
import util::Reflective;
import lang::pico::\syntax::Main;

@synopsis{Provides each contribution (IDE feature) as a callback element of the set of LanguageServices.}
set[LanguageService] picoLanguageContributor() = {
    parser(parser(#start[Program])),
    outliner(picoOutliner),
    lenses(picoLenses),
    executor(picoCommands),
    inlayHinter(picoHinter),
    definer(lookupDef),
    actions(picoActions)
};

@synopsis{This set of contributions runs slower but provides more detail.}
set[LanguageService] picoLanguageContributorSlowSummary() = {
    parser(parser(#start[Program])),
    analyzer(picoAnalyzer, providesImplementations = false),
    builder(picoBuilder)
};

@synopsis{The outliner maps pico syntax trees to lists of DocumentSymbols.}
list[DocumentSymbol] picoOutliner(start[Program] input)
  = [symbol("<input.src>", DocumentSymbolKind::\file(), input.src, children=[
      *[symbol("<var.id>", \variable(), var.src) | /IdType var := input]
  ])];

@synopsis{The analyzer maps pico syntax trees to error messages and references}
Summary picoAnalyzer(loc l, start[Program] input) = picoSummarizer(l, input, analyze());

@synopsis{The builder does a more thorough analysis then the analyzer, providing more detail}
Summary picoBuilder(loc l, start[Program] input) = picoSummarizer(l, input, build());

@synopsis{A simple "enum" data type for switching between analysis modes}
data PicoSummarizerMode
    = analyze()
    | build()
    ;

@synopsis{Translates a pico syntax tree to a model (Summary) of everything we need to know about the program in the IDE.}
Summary picoSummarizer(loc l, start[Program] input, PicoSummarizerMode mode) {
    Summary s = summary(l);

    // definitions of variables
    rel[str, loc] defs = {<"<var.id>", var.src> | /IdType var := input};

    // uses of identifiers
    rel[loc, str] uses = {<id.src, "<id>"> | /Id id := input};

    // documentation strings for identifier uses
    rel[loc, str] docs = {<var.src, "*variable* <var>"> | /IdType var := input};

    // Provide errors (cheap to compute) both in analyze mode and in build mode.
    s.messages += {<src, error("<id> is not defined", src, fixes=prepareNotDefinedFixes(src, defs))>
                  | <src, id> <- uses, id notin defs<0>};

    // "references" are links for loc to loc (from def to use)
    s.references += (uses o defs)<1,0>;

    // "definitions" are also links from loc to loc (from use to def)
    s.definitions += uses o defs;

    // "documentation" maps locations to strs
    s.documentation += (uses o defs) o docs;

    // Provide warnings (expensive to compute) only in build mode
    if (build() := mode) {
        rel[loc, str] asgn = {<id.src, "<id>"> | /Statement stmt := input, (Statement) `<Id id> := <Expression _>` := stmt};
        s.messages += {<src, warning("<id> is not assigned", src)> | <id, src> <- defs, id notin asgn<1>};
    }

    return s;
}

@synopsis{Looks up the declaration for any variable use using the / deep match}
set[loc] lookupDef(loc _, start[Program] input, Tree cursor) =
    {d.src | /IdType d := input, cursor := d.id};

@synopsis{If a variable is not defined, we list a fix of fixes to replace it with a defined variable instead.}
list[CodeAction] prepareNotDefinedFixes(loc src,  rel[str, loc] defs)
    = [action(title="Change to <existing<0>>", edits=[changed(src.top, [replace(src, existing<0>)])]) | existing <- defs];

@synopsis{Finds a declaration that the cursor is on and proposes to remove it.}
list[CodeAction] picoActions([*Tree _, IdType x, *Tree _, start[Program] program])
    = [action(command=removeDecl(program, x, title="remove <x>"))];

default list[CodeAction] picoActions(Focus _focus) = [];

@synsopsis{Defines three example commands that can be triggered by the user (from a code lens, from a diagnostic, or just from the cursor position)}
data Command
  = renameAtoB(start[Program] program)
  | removeDecl(start[Program] program, IdType toBeRemoved)
  ;

@synopsis{Adds an example lense to the entire program.}
rel[loc,Command] picoLenses(start[Program] input)
    = {<input@\loc, renameAtoB(input, title="Rename variables a to b.")>};

@synopsis{Generates inlay hints that explain the type of each variable usage.}
list[InlayHint] picoHinter(start[Program] input) {
    typeLookup = ( "<name>" : "<tp>" | /(IdType)`<Id name> : <Type tp>` := input);

    return [
        hint(name.src, " : <typeLookup["<name>"]>", \type(), atEnd = true)
        | /(Expression)`<Id name>` := input
        , "<name>" in typeLookup
    ];
}

@synopsis{Helper function to generate actual edit actions for the renameAtoB command}
list[DocumentEdit] getAtoBEdits(start[Program] input)
   = [changed(input@\loc.top, [replace(id@\loc, "b") | /id:(Id) `a` := input])];

@synopsis{Command handler for the renameAtoB command}
value picoCommands(renameAtoB(start[Program] input)) {
    applyDocumentsEdits(getAtoBEdits(input));
    return ("result": true);
}

@synopsis{Command handler for the removeDecl command}
value picoCommands(removeDecl(start[Program] program, IdType toBeRemoved)) {
    applyDocumentsEdits([changed(program@\loc.top, [replace(toBeRemoved@\loc, "")])]);
    return ("result": true);
}

@synopsis{The main function registers the Pico language with the IDE}
@description{
Register the Pico language and the contributions that supply the IDE with features.

((registerLanguage)) is called twice here:
1. first for fast and cheap contributions
2. asynchronously for the full monty that loads slower
}
@benefits{
* You can run each contribution on an example in the terminal to test it first.
Any feedback (errors and exceptions) is faster and more clearly printed in the terminal.
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
