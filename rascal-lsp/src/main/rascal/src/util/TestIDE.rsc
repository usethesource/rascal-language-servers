module util::TestIDE

import util::IDE;
import lang::pico::\syntax::Main;

set[Contribution] picoLanguageContributor() = {
    parser(Tree (str input, loc src) {
        return parse(#start[Program], input, src);
    })
};

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