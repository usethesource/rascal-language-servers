module demo::lang::picoish::LanguageServer

import ParseTree;
import util::LanguageServer;
import util::Reflective;
import demo::lang::picoish::Syntax;

set[LanguageService] picoishLanguageContributor() = {
    parser(parser(#start[Program]))
};

void main() {
    registerLanguage(
        language(
            pathConfig(),
            "Pico-ish",
            {"pico", "pico-new"},
            "demo::lang::picoish::LanguageServer",
            "picoishLanguageContributor"
        )
    );
}
