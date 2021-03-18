module util::TestIDE

import util::IDE;

start syntax ExampleLanguage = ("example" | " ")+;

set[Contribution] exampleLanguageContributor() = {
    parserFor(#start[ExampleLanguage])
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