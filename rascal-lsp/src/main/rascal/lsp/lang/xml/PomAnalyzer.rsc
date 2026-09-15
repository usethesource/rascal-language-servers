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
module lang::xml::PomAnalyzer

import Exception;
import IO;
import Node;
import String;

import analysis::diff::edits::TextEdits;
import lang::xml::IO;
import util::Reflective;

node getChildNode(node n, str name) {
    if (node child <- getChildren(n), name := getName(child)) {
        return child;
    }
    throw "No child with name \'<name>\' in \'<getName(n)>\': <[getName(c) | node c <- getChildren(n)]>";
}

str inferIndentation(node pom, list[str] pomLines) {
    try {
        str indentation = "  ";
        if (node project := getChildNode(pom, "project"), loc projectLoc := project.src,
            node groupId := getChildNode(project, "groupId"), loc groupIdLoc := groupId.src) {
                indentation = pomLines[groupIdLoc.begin.line-1][projectLoc.begin.column..groupIdLoc.begin.column];
        }
        return indentation;
    } catch value _:;
    return "  ";
}

str inferNewline(str pomSrc, list[str] pomLines)
    = [first, second, *_] := pomLines ? pomSrc[size(first)+1..findFirst(pomSrc, second)] : "";

@memo
node readPom(loc l, datetime _timestamp) {
    if (node pom := readXML(l, trackOrigins=true, includeEndTags=true)) {
        return pom;
    }
    throw IllegalArgument("No pom at <l>");
}

node readPom(loc l) {
    return readPom(l, lastModified(l));
}

TextEdit addDependency(loc pomLoc, str groupId, str artifactId, str version) {
    pom = readPom(pomLoc);
    pomSrc = readFile(pomLoc);
    pomLines = readFileLines(pomLoc);

    indentation = inferIndentation(pom, pomLines);
    newline = inferNewline(pomSrc, pomLines);

    gId = "\<groupId\><groupId>\</groupId\>";
    aId = "\<artifactId\><artifactId>\</artifactId\>";
    v = "\<version\><version>\</version\>";

    str makeDependencyXml(str baseIndentation)
        = "<baseIndentation>\<dependency\><newline><baseIndentation><indentation><gId><newline><baseIndentation><indentation><aId><newline><baseIndentation><indentation><v><newline><baseIndentation>\</dependency\><newline>";

    if (node project := getChildNode(pom, "project")) {
        try {
            if (node dependencies := getChildNode(project, "dependencies"), loc depsSrc := dependencies.src, list[node] children := getChildren(dependencies)) {
                if ([node dep, *_] := children, loc depSrc := dep.src) {
                    // A dependencies block with dependencies
                    baseIndentation = pomLines[depSrc.begin.line-1][..depSrc.begin.column];
                    l = depSrc.top(depSrc.offset-depSrc.begin.column, 0, <depSrc.begin.line, 0>, <depSrc.begin.line, 0>);
                    return replace(l, makeDependencyXml(baseIndentation));
                } else {
                    // A dependencies block without dependencies
                    baseIndentation = pomLines[depsSrc.begin.line-1][..depsSrc.begin.column];
                    l = depsSrc;
                    return replace(l, "\<dependencies\><newline><makeDependencyXml(baseIndentation + indentation)><baseIndentation>\</dependencies\>");
                }
            }
        } catch value _: {
            // No dependencies block, inserting one
            if (node finalChild := getChildren(project)[-1], loc finalChildLoc := finalChild.src) {
                baseIndentation = pomLines[finalChildLoc.begin.line-1][..finalChildLoc.begin.column];
                return insertAfter(finalChildLoc, "<newline><newline><baseIndentation>\<dependencies\><newline><makeDependencyXml(baseIndentation + indentation)><baseIndentation>\</dependencies\>");
            }
        }
    }
    throw IllegalArgument("Invalid pom at <pomLoc>");
}

TextEdit addRascalDependency(loc pomLoc, str version=getRascalVersion()) {
    return addDependency(pomLoc, "org.rascalmpl", "rascal", version);
}

TextEdit addRascalLspDependency(loc pomLoc, str version=getCurrentRascalLspVersion()) {
    return addDependency(pomLoc, "org.rascalmpl", "rascal-lsp", version);
}

@javaClass{org.rascalmpl.vscode.lsp.xml.PomAnalyzer}
java bool hasRascalDependency(loc pomLoc);

@javaClass{org.rascalmpl.vscode.lsp.xml.PomAnalyzer}
java bool hasRascalLspDependency(loc pomLoc);

@javaClass{org.rascalmpl.vscode.lsp.xml.PomAnalyzer}
java str getCurrentRascalLspVersion();
