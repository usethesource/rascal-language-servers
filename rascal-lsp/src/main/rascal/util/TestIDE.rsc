module util::TestIDE

import util::LanguageServer;
import ParseTree;
import util::Reflective;
import lang::pico::\syntax::Main;

set[LanguageService] picoLanguageContributor() = {
    parser(Tree (str input, loc src) {
        return parse(#start[Program], input, src);
    }),
    outliner(picoOutliner),
    summarizer(picoSummarizer)
};

list[DocumentSymbol] picoOutliner(start[Program] input)
  = [symbol("<input.src>", \file(), input.src, children=[
      *[symbol("<var.id>", \variable(), var.src) | /IdType var := input]
  ])];

Summary picoSummarizer(loc l, start[Program] input) {
    rel[str, loc] defs = {<"<var.id>", var.src> | /IdType var  := input};
    rel[loc, str] uses = {<id.src, "<id>"> | /Id id := input};
    rel[loc, str] docs = {<var.src, "*variable* <var>"> | /IdType var := input};

    return summary(l,
        references = (uses o defs)<1,0>,
        definitions = uses o defs,
        documentation = (uses o defs) o docs
    );
}

void testPicoLanguageContribution() {
    registerLanguage(
        language(
            pathConfig(),
            "Pico",
            "pico",
            "util::TestIDE",
            "picoLanguageContributor"
        )
    );
}