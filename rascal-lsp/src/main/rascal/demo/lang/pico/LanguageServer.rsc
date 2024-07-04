@license{
Copyright (c) 2018-2024, NWO-I CWI and Swat.engineering
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
import Location;
import IO;

@synopsis{Provides each contribution (IDE feature) as a callback element of the set of LanguageServices.}
set[LanguageService] picoLanguageContributor() = {
    parser(parser(#start[Program])),
    outliner(picoOutliner),
    lenses(picoLenses),
    executor(picoCommands),
    inlayHinter(picoHinter),
    definer(lookupDef),
    codeActionContributor(contributeActions)
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
    // We also connect quick-fixes immediately to every error
    s.messages += {<src, error("<id> is not defined", src, fixes=[changeToFix(src, existing<0>, title="Change to <existing<0>>") | existing <- defs])> 
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

@synopsis{Finds a declaration that the cursor is on and proposes to remove it.}
list[Command] contributeActions(loc src, start[Program] program, Tree focus) {
    println("src: <src>
            'program: <program>
            'focus: <focus>");
    return [ removeDecl(program, x, title="remove <x>") | /IdType x := focus, isOverlapping(src, x@\loc)];
}

set[loc] lookupDef(loc _, start[Program] input, Tree cursor) =
    { d.src | /IdType d := input, cursor := d.id};

@synsopsis{Defines three example commands that can be triggered by the user (from a code lens, from a diagnostic, or just from the cursor position)}
data Command
  = renameAtoB(start[Program] program)
  | changeToFix(loc place, str replacement)
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

@synopsis{Command handler for the changeToFix command}
value picoCommands(changeToFix(loc src, str newName)) {
    applyDocumentsEdits([changed(src.top, [replace(src, newName)])]);
    return ("result": true);
}

@synopsis{Command hamlder for the removeDecl command}
value picoCommands(removeDecl(start[Program] program, IdType toBeRemoved)) {
    // we use concrete tree transformation here for demo purposes
    // newProgram = visit(program) {
    //     case (Program) `begin 
    //                    'declare 
    //                    '  <{IdType ","}* pre> 
    //                    '  <IdType x>
    //                    '  <{IdType ","}* post> 
    //                    '<{Statement ";"}* body> 
    //                    'end` 
    //       => (Program) `begin
    //                    'declare
    //                    '  <{IdType ","} * pre>
    //                    '  <{IdType ","} * post>
    //                    '<{Statement ";"}* body>
    //                    'end`
    //     when x := toBeRemoved
    // }

    // // replace the whole program
    // applyDocumentsEdits([changed(program@\loc.top, [replace(program@\loc, "<newProgram>")])]);
    return ("result": true);
}

@synopsis{The main function registers the Pico language with the IDE}
@description{
What _registerLanguage_ does for Pico here:
1. First the registerLanguage identifies the meta data for the language; the name, the file extension, the location of the code to load and run.
2. Second the code is run to create and link the call-backs that implement the server side of the language server protocol (picoLanguageContributor).
3. The LSP server from now on responds to calls from the IDE client and supplies highlights, references, documentation, code actions, etc.

There are _two_ calls to registerLanguage here to demonstrate that sequential calls to registerLanguage can have an incremental
registration effect:
1. First the cheaper/faster language features are registered, giving the user already quick feedback on their code.
2. The second (asynchronous) registration may be slower to load and execute, but it merges with the previous registrations after
the loading and pre-fetching of indexes is finished. 
3. Now more detailed, more complete or more accurate information is also available.

For Pico these two steps are unnecessary, but for larger language implementations with longer loading times,
it is essential to split up the registration process. Typically this is done when experimentation is finished
and we are fine-tuning for a smooth deployment of a VScode extension.

:::tip
Before registering a language, it is advisable to _use the terminal and first run each contribution_ at least once
on an example parse tree for an example file. Possible static or run-time errors would be reported directly in the terminal,
instead of inside one of VScode's many log files. Fixing those minor issues also has a faster turn-around cycle
when testing from the terminal. 
:::
}
void main() {
    registerLanguage(
        language(
            pathConfig(srcs=[|lib://rascal-lsp/|,|std:///|],libs=[|lib://rascal-lsp/|,|std:///|],classpath=[|lib://rascal|,|lib://rascal-lsp|]),
            "Pico",
            {"pico", "pico-new"},
            "demo::lang::pico::LanguageServer",
            "picoLanguageContributor"
        )
    );
    registerLanguage(
        language(
            pathConfig(srcs=[|lib://rascal-lsp/|,|std:///|],lib=[|lib://rascal-lsp/|,|std:///|], classpath=[|lib://rascal|,|lib://rascal-lsp|]),
            "Pico",
            {"pico", "pico-new"},
            "demo::lang::pico::LanguageServer",
            "picoLanguageContributorSlowSummary"
        )
    );
}
