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
@bootstrapParser
module lang::rascal::tests::rename::ProjectOnDisk

import lang::rascal::lsp::refactor::Rename;
import lang::rascal::tests::rename::TestUtils;
import util::Reflective;
import lang::rascalcore::check::Checker;

import analysis::diff::edits::TextEdits;

Edits testProjectOnDisk(loc projectDir, str file, str oldName, int occurrence = 0, str newName = "<oldName>_new", list[str] srcDirs = ["src/main/rascal"], list[loc] libs = []) {
    PathConfig pcfg;
    if (projectDir.file == "rascal") {
        pcfg = pathConfig(
            srcs = [ projectDir + "src/org/rascalmpl/library"
                   , projectDir + "src/org/rascalmpl/compiler"
                   , projectDir + "src/org/rascalmpl/tutor"
                   , projectDir + "test/org/rascalmpl/benchmark"
                   ],
            bin = projectDir + "target/classes",
            libs = libs
        );
    } else {
        pcfg = pathConfig(
            srcs = [projectDir + dir | dir <- srcDirs],
            bin = projectDir + "target/classes",
            libs = [calculateRascalLib(), *libs]
        );
    }
    // extension for Rascal compiler
    pcfg = pcfg[resources = [pcfg.bin]];
    return getEdits(projectDir + file, {projectDir}, occurrence, oldName, newName, PathConfig(_) { return pcfg; });
}
