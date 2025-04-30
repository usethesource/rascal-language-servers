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
import List;
import Set;
import util::Benchmark;
import util::Math;
import analysis::diff::edits::TextEdits;

loc rascalProj(loc projDir) = projDir + "rascal";
loc typepalProj(loc projDir) = projDir + "typepal";
loc birdProj(loc projDir) = projDir + "bird/bird-core"; // removed RASCAL.MF

// Local typepal build - 0.15.1, but based on Rascal 0.41.0-RC20 to work around incompatible versions
public loc typepalLib = |mvn://org.rascalmpl--typepal--0.15.1-SNAPSHOT/|;

void() run(loc proj, str file, str oldName, str newName = "<oldName>2", int occurrence = 0, list[str] srcDirs = ["src/main/rascal"], list[loc] libs = []) = void() {
    println("Renameing \'<oldName>\' to \'<newName>\' in <proj + file>");
    <edits, msgs> = testProjectOnDisk(proj, file, oldName, newName = newName, occurrence = occurrence, srcDirs = srcDirs, libs = libs);
    if (errors:{_, *_} := {msg | msg <- msgs, msg is error}) throw errors;
    if (size({r | /r:replace(_, _) := edits}) < 2) throw "Unexpected number of edits: <edits>";
};

map[str, num] benchmarks(loc projDir) = benchmark((
        "[bird] nonterminal": run(birdProj(projDir), "src/main/rascal/lang/bird/Syntax.rsc", "TopLevelDecl", libs = [typepalLib])
      , "[bird] formal param": run(birdProj(projDir), "src/main/rascal/lang/bird/Checker.rsc", "typeFormals", occurrence = 1, libs = [typepalLib])
      , "[bird] global function": run(birdProj(projDir), "src/main/rascal/lang/bird/Checker.rsc", "collectAnnos", libs = [typepalLib])
      , "[bird] local var": run(birdProj(projDir), "src/main/rascal/lang/bird/Checker.rsc", "imported", libs = [typepalLib])
      , "[bird] module name": run(birdProj(projDir), "src/main/rascal/lang/bird/Checker.rsc", "lang::bird::Checker", libs = [typepalLib])
      , "[typepal] local var": run(typepalProj(projDir), "src/analysis/typepal/Solver.rsc", "facts", srcDirs = ["src"])
      , "[typepal] constructor": run(typepalProj(projDir), "src/analysis/typepal/Collector.rsc", "collector", srcDirs = ["src"])
      , "[typepal] global var": run(typepalProj(projDir), "src/analysis/typepal/Version.rsc", "currentTplVersion", srcDirs = ["src"])
      , "[rascal] function": run(rascalProj(projDir), "src/org/rascalmpl/library/analysis/m3/AST.rsc", "astNodeSpecification")
      , "[rascal] local var": run(rascalProj(projDir), "src/org/rascalmpl/library/analysis/diff/edits/ExecuteTextEdits.rsc", "e")
      , "[rascal] type param": run(rascalProj(projDir), "src/org/rascalmpl/library/Map.rsc", "K")
      , "[rascal] grammar constructor": run(rascalProj(projDir), "src/org/rascalmpl/library/lang/rascal/syntax/Rascal.rsc", "transitiveReflexiveClosure")
      , "[rascal] nonterminal label": run(rascalProj(projDir), "src/org/rascalmpl/library/lang/rascal/syntax/Rascal.rsc", "lhs", newName = "lefthandside")
), safeRuns(3, intMedian, realTimeOf));

num(void()) safeRuns(int numRuns, num(list[num]) aggregate, int(void()) measure) = int(void() f) {
    try {
        return aggregate([measure(f) | _ <- [0..numRuns]]);
    } catch e: {
        println("[ERROR] <e>");
        return -1;
    }
};

void cleanBenchmarkTargets(loc projDir) {
    for (loc d <- {rascalProj(projDir), typepalProj(projDir), birdProj(projDir)}) {
        remove(d + "target", recursive = true);
    }
}

int intMedian(list[num] nums) = toInt(median(nums));

// Copied from analysis::statistics::Descriptive
default real median(list[num] nums:[_, *_])
	= mean(middle(nums));

real mean(list[num] nums:[_, *_]) = toReal(sum(nums)) / size(nums);

private list[&T] middle(list[&T] nums) {
	nums = sort(nums);
	n = size(nums);
	if (n % 2 == 1) {
		return [nums[n/2]];
	}
	n = n / 2;
	return nums[n-1..n+1];
}
