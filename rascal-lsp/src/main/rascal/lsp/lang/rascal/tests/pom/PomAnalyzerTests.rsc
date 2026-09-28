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
module lang::rascal::tests::pom::PomAnalyzerTests

import IO;
import String;
import analysis::diff::edits::TextEdits;
import lang::xml::PomAnalyzer;

public str pomWithRascal = "\<?xml version=\"1.0\" encoding=\"UTF-8\"?\>
                           '\<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"
                           'xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\"\>
                           '  \<modelVersion\>4.0.0\</modelVersion\>
                           '
                           '  \<groupId\>org.rascalmpl\</groupId\>
                           '  \<artifactId\>test-lib\</artifactId\>
                           '  \<version\>0.1.0-SNAPSHOT\</version\>
                           '
                           '  \<dependencies\>
                           '    \<dependency\>
                           '      \<groupId\>org.rascalmpl\</groupId\>
                           '      \<artifactId\>rascal\</artifactId\>
                           '      \<version\>0.43.0-RC14\</version\>
                           '    \</dependency\>
                           '  \</dependencies\>
                           '\</project\>";

public str pomWithoutDependencies = "\<?xml version=\"1.0\" encoding=\"UTF-8\"?\>
                                    '\<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"
                                    'xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\"\>
                                    '  \<modelVersion\>4.0.0\</modelVersion\>
                                    '
                                    '  \<groupId\>org.rascalmpl\</groupId\>
                                    '  \<artifactId\>test-lib\</artifactId\>
                                    '  \<version\>0.1.0-SNAPSHOT\</version\>
                                    '
                                    '\</project\>";

public str pomWithEmptyDependenciesBlock = "\<?xml version=\"1.0\" encoding=\"UTF-8\"?\>
                                           '\<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"
                                           'xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\"\>
                                           '  \<modelVersion\>4.0.0\</modelVersion\>
                                           '
                                           '  \<groupId\>org.rascalmpl\</groupId\>
                                           '  \<artifactId\>test-lib\</artifactId\>
                                           '  \<version\>0.1.0-SNAPSHOT\</version\>
                                           '
                                           '  \<dependencies\>
                                           '  \</dependencies\>
                                           '\</project\>";

public str pomWithNonEmptyDependenciesBlock = "\<?xml version=\"1.0\" encoding=\"UTF-8\"?\>
                                              '\<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"
                                              'xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\"\>
                                              '  \<modelVersion\>4.0.0\</modelVersion\>
                                              '
                                              '  \<groupId\>org.rascalmpl\</groupId\>
                                              '  \<artifactId\>test-lib\</artifactId\>
                                              '  \<version\>0.1.0-SNAPSHOT\</version\>
                                              '
                                              '  \<dependencies\>
                                              '    \<dependency\>
                                              '      \<groupId\>org.rascalmpl\<groupId\>
                                              '      \<artifactId\>vallang\</artifactId\>
                                              '      \<version\>0.15.1\</version\>
                                              '    \</dependency\>
                                              '  \</dependencies\>
                                              '\</project\>";

// `Path.of` does not like `memory` or plain `tmp` locations
public loc pomLoc = resolveLocation(|tmp:///rascal-pomxml-test/pom.xml|);

test bool testPomWithRascal() {
    writeFile(pomLoc, pomWithRascal);
    return hasRascalDependency(pomLoc);
}

test bool testPomWithoutDependencies() {
    writeFile(pomLoc, pomWithoutDependencies);
    if (hasRascalDependency(pomLoc)) {
        return false;
    }
    edit = addRascalDependency(pomLoc);
    newline = inferNewline(pomLoc);
    expectedLoc = pomLoc(381 + 7 * size(newline),0,<8,35>,<8,35>);
    return replace(expectedLoc, / <newline><newline>  \<dependencies\><newline>    \<dependency\><newline>      \<groupId\>org.rascalmpl\<\/groupId\><newline>      \<artifactId\>rascal\<\/artifactId\><newline>      \<version\>[^\<]*\<\/version\><newline>    \<\/dependency\><newline>  \<\/dependencies\>/) := edit;
}

test bool testPomWithEmptyDependenciesBlock() {
    writeFile(pomLoc, pomWithEmptyDependenciesBlock);
    if (hasRascalDependency(pomLoc)) {
        return false;
    }
    edit = addRascalDependency(pomLoc);
    newline = inferNewline(pomLoc);
    expectedLoc = pomLoc(383 + 9 * size(newline),31 + size(newline),<10,2>,<11,17>);
    return replace(expectedLoc, /\<dependencies\><newline>    \<dependency\><newline>      \<groupId\>org.rascalmpl\<\/groupId\><newline>      \<artifactId\>rascal\<\/artifactId\><newline>      \<version\>[^\<]*\<\/version\><newline>    \<\/dependency\><newline>  \<\/dependencies\>/) := edit;
}

test bool testPomWithNonEmptyDependenciesBlock() {
    writeFile(pomLoc, pomWithNonEmptyDependenciesBlock);
    if (hasRascalDependency(pomLoc)) {
        return false;
    }
    edit = addRascalDependency(pomLoc);
    newline = inferNewline(pomLoc);
    expectedLoc = pomLoc(397 + 10 * size(newline),0,<11,0>,<11,0>);
    return replace(expectedLoc, /    \<dependency\><newline>      \<groupId\>org.rascalmpl\<\/groupId\><newline>      \<artifactId\>rascal\<\/artifactId\><newline>      \<version\>[^\<]*\<\/version\><newline>    \<\/dependency\><newline>/) := edit;
}

test bool testAddRascalLspDependency() {
    writeFile(pomLoc, pomWithNonEmptyDependenciesBlock);
    if (hasRascalLspDependency(pomLoc)) {
        return false;
    }
    edit = addRascalLspDependency(pomLoc);
    newline = inferNewline(pomLoc);
    expectedLoc = pomLoc(397 + 10 * size(newline),0,<11,0>,<11,0>);
    return replace(expectedLoc, /    \<dependency\><newline>      \<groupId\>org.rascalmpl\<\/groupId\><newline>      \<artifactId\>rascal-lsp\<\/artifactId\><newline>      \<version\>[^\<]*\<\/version\><newline>    \<\/dependency\><newline>/) := edit;
}

