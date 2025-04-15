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
module lang::rascal::tests::rename::Benchmark

import lang::rascal::tests::rename::ProjectOnDisk;

import IO;
import util::Benchmark;

loc rascalProj(loc projDir) = projDir + "rascal";
loc typepalProj(loc projDir) = projDir + "typepal";
loc birdProj(loc projDir) = projDir + "../bird/bird-core";

void() run(loc proj, str file, str oldName, str newName = "<oldName>2", int occ = 0, list[str] srcDirs = ["src/main/rascal"]) = void() {
    println("Rename \'<oldName>\' in <proj + file>");
    try {
        testProjectOnDisk(proj, file, oldName, newName = newName, occurrence = occ, srcDirs = srcDirs);
    } catch e: {
        println("Renaming \'<oldName>\' to \'<newName>\' in <proj + file> resulted in error:");
        println(e);
    }
};

map[str, num] benchmarks(loc projDir) {
    results = benchmark((
        // "[typepal] local var": run(typepalProj(projDir), "src/analysis/typepal/Solver.rsc", "facts", srcDirs = ["src"])
        "[bird] nonterminal": run(birdProj(projDir), "src/main/rascal/lang/bird/Syntax.rsc", "TopLevelDecl")
      , "[bird] formal param": run(birdProj(projDir), "src/main/rascal/lang/bird/Checker.rsc", "typeFormals")
      , "[bird] global function": run(birdProj(projDir), "src/main/rascal/lang/bird/Checker.rsc", "collectAnnos")
      , "[bird] local var": run(birdProj(projDir), "src/main/rascal/lang/bird/Checker.rsc", "imported")
      , "[typepal] module name": run(typepalProj(projDir), "src/analysis/typepal/Version.rsc", "analysis::typepal::Version", srcDirs = ["src"])
      , "[typepal] constructor": run(typepalProj(projDir), "src/analysis/typepal/Collector.rsc", "collector", srcDirs = ["src"])
      , "[typepal] global var": run(typepalProj(projDir), "src/analysis/typepal/Version.rsc", "currentTplVersion", srcDirs = ["src"])
    //   , "[rascal] function": run(rascalProj(projDir), "src/org/rascalmpl/compiler/lang/rascalcore/check/ATypeUtils.rsc", "prettyAType")
    //     "[rascal] local var": run(rascalProj(projDir), "src/org/rascalmpl/compiler/lang/rascalcore/check/Checker.rsc", "msgs")
    //   , "[rascal] type param": run(rascalProj(projDir), "src/org/rascalmpl/library/Map.rsc", "K")
    //   , "[rascal] grammar constructor": run(rascalProj(projDir), "src/org/rascalmpl/library/lang/rascal/syntax/Rascal.rsc", "transitiveReflexiveClosure")
    ));
    iprintln(results);

    return results;
}
