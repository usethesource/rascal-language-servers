module lang::rascal::tests::rename::ProjectOnDisk

import lang::rascal::lsp::refactor::Rename;
import lang::rascal::tests::rename::TestUtils;
import util::Reflective;

Edits testProjectOnDisk(loc projectDir, str file, str oldName, int occurrence = 0, str newName = "<oldName>_new") {
    PathConfig pcfg;
    if (projectDir.file == "rascal-core") {
        pcfg = getRascalCorePathConfig(projectDir);
    } else {
        pcfg = pathConfig(
            srcs = [ projectDir + "src" ],
            bin = projectDir + "target/classes",
            generatedSources = projectDir + "target/generated-sources/src/main/java/",
            generatedTestSources = projectDir + "target/generated-test/sources/src/main/java/",
            resources = projectDir + "target/generated-resources/src/main/java/",
            libs = [ |lib://rascal| ]
        );
    }
    return getEdits(projectDir + file, {projectDir}, occurrence, oldName, newName, PathConfig(_) { return pcfg; });
}
