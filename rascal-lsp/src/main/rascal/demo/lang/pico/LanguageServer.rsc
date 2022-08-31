/*
 * Copyright (c) 2018-2021, NWO-I CWI and Swat.engineering
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
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
    summarizer(picoSummarizer, providesImplementations = false),
    lenses(picoLenses),
    executor(picoCommands),
    inlayHinter(picoHinter),
    definer(lookupDef)
};

list[DocumentSymbol] picoOutliner(start[Program] input)
  = [symbol("<input.src>", DocumentSymbolKind::\file(), input.src, children=[
      *[symbol("<var.id>", \variable(), var.src) | /IdType var := input]
  ])];

Summary picoSummarizer(loc l, start[Program] input) {
    println("Running summary for pico!");
    rel[str, loc] defs = {<"<var.id>", var.src> | /IdType var  := input};
    rel[loc, str] uses = {<id.src, "<id>"> | /Id id := input};
    rel[loc, str] docs = {<var.src, "*variable* <var>"> | /IdType var := input};

    return summary(l,
        references = (uses o defs)<1,0>,
        definitions = uses o defs,
        documentation = (uses o defs) o docs
    );
}

set[loc] lookupDef(loc l, start[Program] input, Tree cursor) =
    { d.src | /IdType d := input, cursor := d.id};


data Command
  = renameAtoB(start[Program] program);

rel[loc,Command] picoLenses(start[Program] input) = {<input@\loc, renameAtoB(input, title="Rename variables a to b.")>};


loc atEnd(loc l) = l[begin = l.end][offset=l.offset+l.length - 1][length = 1];

list[InlayHint] picoHinter(start[Program] input) {
    typeLookup = ( "<name>" : "<tp>" | /(IdType)`<Id name> : <Type tp>` := input);
    return [
        hint(atEnd(name.src), " : <typeLookup["<name>"]>", \parameter()) | /(Expression)`<Id name>` := input, "<name>" in typeLookup
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
            "pico",
            "demo::lang::pico::LanguageServer",
            "picoLanguageContributor"
        )
    );
}
