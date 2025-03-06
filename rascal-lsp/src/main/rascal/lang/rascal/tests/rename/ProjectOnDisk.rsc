@license{
Copyright (c) 2018-2025, NWO-I CWI and Swat.engineering
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice,
this list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
POSSIBILITY OF SUCH DAMAGE.
}
module lang::rascal::tests::rename::ProjectOnDisk

import lang::rascal::lsp::refactor::Rename;
import lang::rascal::lsp::refactor::Util;
import lang::rascal::tests::rename::TestUtils;
import util::Reflective;
import lang::rascalcore::check::Checker;

Edits testProjectOnDisk(loc projectDir, str file, str oldName, int occurrence = 0, str newName = "<oldName>_new") {
    PathConfig pcfg;
    if (projectDir.file == "rascal-core") {
        pcfg = getRascalCorePathConfig(projectDir);
    } else if (projectDir.file == "rascal") {
        pcfg = pathConfig(
            srcs = [ projectDir + "src/org/rascalmpl/library"
                   , projectDir + "test/org/rascalmpl/benchmark"
                   , projectDir + "test/org/rascalmpl/test/data"],
            bin = projectDir + "target/classes",
            generatedSources = projectDir + "target/generated-sources/src/main/java/",
            generatedTestSources = projectDir + "target/generated-test/sources/src/main/java/",
            resources = projectDir + "target/generated-resources/src/main/java/"
        );
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
