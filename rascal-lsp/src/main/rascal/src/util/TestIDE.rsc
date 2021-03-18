module util::TestIDE

import util::IDE;

start syntax ExampleLanguage = ("example" | " ")+;

set[Contribution] exampleLanguageContributor() = {
    parser(Tree (str input, loc src) {
        return parse(#start[ExampleLanguage], input, src);
    })
};

void testExampleLanguageContribution() {
    registerLanguage(
        language(
            pathConfig(), 
            "Example language", 
            "example", 
            "util::TestIDE", 
            "exampleLanguageContributor"
        )
    );
}