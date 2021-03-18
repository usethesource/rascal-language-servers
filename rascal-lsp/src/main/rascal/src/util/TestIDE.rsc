module util::TestIDE

import util::IDE;
import lang::pico::\syntax::Main;

set[Contribution] picoLanguageContributor() = {
    parserFor(#start[Program]),
    outliner(picoOutliner)
};

list[DocumentSymbol] picoOutliner(start[Program] input)
  = [symbol("<input.src>", \file(), input.src, children=[
      *[symbol("<var.id>", \variable(), var.src) | /IdType var := input]
  ])];

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