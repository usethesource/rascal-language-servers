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
@synopsis{Demonstrates the API for defining and registering IDE language services for Programming Languages and Domain Specific Languages.}
@description{
The core functionality of this module is built upon these concepts:
* ((registerLanguage)) for enabling your language services for a given file extension _in the current IDE_.
* ((Language)) is the data-type for defining a language, with meta-data for starting a new LSP server.
* A ((LanguageService)) is a specific feature for an IDE. Each service comes with one Rascal function that implements it.
}
module demo::lang::pico::LanguageServer

import util::LanguageServer;
import util::IDEServices;
import ParseTree;
import util::ParseErrorRecovery;
import util::Reflective;
import lang::pico::\syntax::Main;
import DateTime;
import IO;
import String;

private Tree (str _input, loc _origin) picoParser(bool allowRecovery) {
    return ParseTree::parser(#start[Program], allowRecovery=allowRecovery, filters=allowRecovery ? {createParseErrorFilter(false)} : {});
}

@synopsis{A language server is simply a set of ((LanguageService))s.}
@description{
Each ((LanguageService)) for pico is implemented as a function.
Here we group all services such that the LSP server can link them
with the ((Language)) definition later.
}
set[LanguageService] picoLanguageServer(bool allowRecovery) = {
    parsing(picoParser(allowRecovery), usesSpecialCaseHighlighting = false),
    documentSymbol(picoDocumentSymbolService),
    codeLens(picoCodeLenseService),
    execution(picoExecutionService),
    inlayHint(picoInlayHintService),
    definition(picoDefinitionService),
    codeAction(picoCodeActionService),
    rename(picoRenamingService, prepareRenameService = picoRenamePreparingService),
    didRenameFiles(picoFileRenameService),
    selectionRange(picoSelectionRangeService),
    formatting(picoParser(allowRecovery), picoFormattingService)
};

str picoFormattingService(Tree input, set[FormattingOption] opts) {
     if (tabSize(int tabSize) <- opts) {
        println("Warning; `tabSize` (<tabSize>) is ignored");
    }
    if (insertSpaces() <- opts) {
        println("Warning; `insertSpaces` is ignored");
    }

    return "<input>";
}

set[LanguageService] picoLanguageServer() = picoLanguageServer(false);
set[LanguageService] picoLanguageServerWithRecovery() = picoLanguageServer(true);

@synopsis{This set of contributions runs slower but provides more detail.}
@description{
((LanguageService))s can be registered asynchronously and incrementally,
such that quicky loaded features can be made available while slower to load
tools come in later.
}
set[LanguageService] picoLanguageServerSlowSummary(bool allowRecovery) = {
    parsing(picoParser(allowRecovery), usesSpecialCaseHighlighting = false),
    analysis(picoAnalysisService, providesImplementations = false),
    build(picoBuildService)
};

set[LanguageService] picoLanguageServerSlowSummary() = picoLanguageServerSlowSummary(false);
set[LanguageService] picoLanguageServerSlowSummaryWithRecovery() = picoLanguageServerSlowSummary(true);

@synopsis{The documentSymbol service maps pico syntax trees to lists of DocumentSymbols.}
@description{
Here we list the symbols we want in the outline view, and which can be searched using
symbol search in the editor.
}
list[DocumentSymbol] picoDocumentSymbolService(start[Program] input)
  = [symbol("<input.src>", DocumentSymbolKind::\file(), input.src, children=[
      *[symbol("<var.id>", \variable(), var.src) | /IdType var := input, var.id?]
  ])];

@synopsis{The analyzer maps pico syntax trees to error messages and references}
Summary picoAnalysisService(loc l, start[Program] input) = picoSummaryService(l, input, analyze());

@synopsis{The builder does a more thorough analysis then the analyzer, providing more detail}
Summary picoBuildService(loc l, start[Program] input) = picoSummaryService(l, input, build());

@synopsis{A simple "enum" data type for switching between analysis modes}
data PicoSummarizerMode
    = analyze()
    | build()
    ;

@synopsis{Translates a pico syntax tree to a model (Summary) of everything we need to know about the program in the IDE.}
Summary picoSummaryService(loc l, start[Program] input, PicoSummarizerMode mode) {
    Summary s = summary(l);

    // definitions of variables
    rel[str, loc] defs = {<"<var.id>", var.src> | /IdType var := input, var.id?};

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

@synopsis{Looks up the declaration for any variable use using a list match into a ((Focus))}
@pitfalls{
This demo actually finds the declaration rather than the definition of a variable in Pico.
}
set[loc] picoDefinitionService([*_, Id use, *_, start[Program] input]) = { def.src | /IdType def := input, use := def.id};

@synopsis{If a variable is not defined, we list a fix of fixes to replace it with a defined variable instead.}
list[CodeAction] prepareNotDefinedFixes(loc src,  rel[str, loc] defs)
    = [action(title="Change to <existing<0>>", edits=[changed(src.top, [replace(src, existing<0>)])]) | existing <- defs];

@synopsis{Finds a declaration that the cursor is on and proposes to remove it.}
list[CodeAction] picoCodeActionService([*_, IdType x, *_, start[Program] program])
    = [action(command=removeDecl(program, x, title="remove <x>"))];

default list[CodeAction] picoCodeActionService(Focus _focus) = [];

@synsopsis{Defines three example commands that can be triggered by the user (from a code lens, from a diagnostic, or just from the cursor position)}
data Command
  = renameAtoB(start[Program] program)
  | removeDecl(start[Program] program, IdType toBeRemoved)
  ;

@synopsis{Adds an example lense to the entire program.}
lrel[loc,Command] picoCodeLenseService(start[Program] input)
    = [<input@\loc, renameAtoB(input, title="Rename variables a to b.")>];

@synopsis{Generates inlay hints that explain the type of each variable usage.}
list[InlayHint] picoInlayHintService(start[Program] input) {
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
value picoExecutionService(renameAtoB(start[Program] input)) {
    applyDocumentsEdits(getAtoBEdits(input));
    return ("result": true);
}

@synopsis{Command handler for the removeDecl command}
value picoExecutionService(removeDecl(start[Program] program, IdType toBeRemoved)) {
    applyDocumentsEdits([changed(program@\loc.top, [replace(toBeRemoved@\loc, "")])]);
    return ("result": true);
}

@synopsis{Prepares the rename service by checking if the id can be renamed}
loc picoRenamePreparingService(Focus _:[Id id, *_]) {
    if ("<id>" == "fixed") {
        throw "Cannot rename id <id>";
    }
    return id.src;
}

@synopsis{Renaming service implementation, unhappy flow.}
tuple[list[DocumentEdit], set[Message]] picoRenamingService(Focus focus, "error") = <[], {error("Test of error detection during renaming.", focus[0].src.top)}>;

@synopsis{Renaming service implementation, happy flow.}
default tuple[list[DocumentEdit], set[Message]] picoRenamingService(Focus focus, str newName) = <[changed(focus[0].src.top, [
    replace(id.src, newName)
    | cursor := focus[0]
    , /Id id := focus[-1]
    , id := cursor
])], {}>;

@synposis{Handle renames of files in the IDE.}
tuple[list[DocumentEdit],set[Message]] picoFileRenameService(list[DocumentEdit] fileRenames) {
    // Iterate over fileRenames

    list[DocumentEdit] edits = [];
    for (renamed(loc from, loc to) <- fileRenames) {
        // Surely there is a better way to do this?
        toBegin = to[offset=0][length=0][begin=<1,0>][end=<1,0>];
        edits = edits + changed(to, [insertBefore(toBegin, "%% File moved from <from> to <to> at <now()>\n", separator="")]);
    }
    return <edits, {info("<size(edits)> moves succeeded!", |unknown:///|)}>;
}

list[loc] picoSelectionRangeService(Focus focus)
    = dup([t@\loc | t <- focus]);

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
void main(bool errorRecovery=false) {
    registerLanguage(
        language(
            pathConfig(),
            "Pico",
            {"pico", "pico-new"},
            "demo::lang::pico::LanguageServer",
            errorRecovery ? "picoLanguageServerWithRecovery" : "picoLanguageServer"
        )
    );
    registerLanguage(
        language(
            pathConfig(),
            "Pico",
            {"pico", "pico-new"},
            "demo::lang::pico::LanguageServer",
            errorRecovery ? "picoLanguageServerSlowSummaryWithRecovery" : "picoLanguageServerSlowSummary"
        )
    );
}
