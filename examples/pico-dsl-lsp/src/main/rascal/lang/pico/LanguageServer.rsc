module lang::pico::LanguageServer

import util::LanguageServer;
import util::IDEServices;
import ParseTree;
import util::Reflective;
import lang::pico::\syntax::Main;
import IO;

// a minimal implementation of a DSL in rascal
// users can add support for more advanced features
set[LanguageService] picoContributions() = {
    parser(parser(#start[Program])), // register the parser function for the Pico language
    outliner(picoOutliner),
    summarizer(picoSummarizer, providesImplementations = false)
  };


// we build a simple outline based on the grammar
list[DocumentSymbol] picoOutliner(start[Program] input)
  = [symbol("<input.src>", DocumentSymbolKind::\file(), input.src, children=[
      *[symbol("<var.id>", \variable(), var.src) | /IdType var := input]
  ])];

// normally you would import the typechecker and run it here
// for brevity we've inlined the implementation here
Summary picoSummarizer(loc l, start[Program] input) {
    rel[str, loc] defs = {<"<var.id>", var.src> | /IdType var  := input};
    rel[loc, str] uses = {<id.src, "<id>"> | /Id id := input};
    rel[loc, str] docs = {<var.src, "*variable* <var>"> | /IdType var := input};


    return summary(l,
        messages = {<src, error("<id> is not defined", src)> | <src, id> <- uses, id notin defs<0>},
        references = (uses o defs)<1,0>,
        definitions = uses o defs,
        documentation = (uses o defs) o docs
    );
}

// run this from a REPL while developing the DSL
int main() {
    // we register a new language to Rascal's LSP multiplexer
    // the multiplexer starts a new evaluator and loads this module and function
    registerLanguage(
        language(
            pathConfig(srcs=[|project://pico-dsl-lsp/src/main/rascal|]),
            "Pico", // name of the language
            "pico", // extension
            "lang::pico::LanguageServer", // module to import
            "picoContributions"
        )
    );
    return 0;
}
