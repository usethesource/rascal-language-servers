module util::TestIDE

import util::IDE;
import lang::pico::\syntax::Main;

set[Contribution] picoLanguageContributor() = {
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

    return summary(l, 
        references = uses o defs
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