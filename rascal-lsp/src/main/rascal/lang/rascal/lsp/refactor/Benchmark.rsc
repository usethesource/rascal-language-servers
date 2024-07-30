@license{
Copyright (c) 2018-2023, NWO-I CWI and Swat.engineering
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

module lang::rascal::lsp::refactor::Benchmark

import IO;
import Set;
import lang::rascal::\syntax::Rascal;

import util::Benchmark;
import util::FileSystem;
import util::Reflective;

void main() {
    name = "val";

    println("Collecting files...");
    fs = find(|home:///swat/projects/Rascal/rascal/src|, "rsc");

    println("Parsing <size(fs)> modules...");
    trees = {parseModuleWithSpaces(f) | f <- fs};

    println("Benchmarking <size(trees)> trees");
    iprintln(benchmark((
        "Deep match (once)": void() { deepMatchOnce(trees, name); },
        "Deep match (twice)": void() { deepMatchTwice(trees, name); }
    )));
}

void deepMatchTwice(set[start[Module]] trees, str name) {
    int matches = 0;

    reg = [Name] name;
    esc = [Name] "\\<name>";

    for (tree <- trees) {
        if (/reg := tree) {
            matches += 1;
        }
        else if (/esc := tree) {
            matches += 1;
        }
    }
    println("[Twice] # of matches: <matches>");
}
void deepMatchOnce(set[start[Module]] trees, str name) {
    int matches = 0;

    reg = [Name] name;
    esc = [Name] "\\<name>";

    for (tree <- trees) {
        if (/Name n := tree, n := reg || n := esc) {
            matches += 1;
        }
    }
    println("[Once]  # of matches: <matches>");
}
