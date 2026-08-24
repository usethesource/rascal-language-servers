module util::PomAnalyzer

import lang::xml::DOM;
import lang::xml::IO;
import analysis::diff::edits::TextEdits;

import IO;
import List;
import Node;
import util::Maybe;

bool(node) hasProperty(str tagName, str tagValue) {
    return bool(node \node) {
        return "<tagName>"(tagValue) <- getChildren(\node);
    };
}

Maybe[&T](node) getProperty(type[&T] _, str tagName) {
    return Maybe[&T](node \node) {
        if ("<tagName>"(&T tagValue) <- getChildren(\node)) {
            return just(tagValue);
        }
        return nothing();
    };
}

bool(node) hasRascalMplGroup = hasProperty("groupid", "org.rascalmpl");

bool(node) isRascalArtifact = hasProperty("artifactid", "rascal");

bool(node) isRascalLspArtifact = hasProperty("artifactid", "rascal-lsp");

Maybe[node] getRascalDependency(list[node] dependencies) {
    if (dep <- dependencies, hasRascalMplGroup(dep), isRascalArtifact(dep)) {
        return just(dep);
    }
    return nothing();
}

Maybe[node] getRascalLspDependency(list[node] dependencies) {
    if (dep <- dependencies, hasRascalMplGroup(dep), isRascalLspArtifact(dep)) {
        return just(dep);
    }
    return nothing();
}

Maybe[str](node) getVersion = getProperty(#str, "version");

str inferRascalVersion(Maybe[str] rascalLsp = nothing()) {
    if (just(str lspVersion) := rascalLsp) {
        // Figure out a compatible version
        return "0.43.0";
    }

    // Read Rascal version from LSP dependencies
    lspPom = |project://rascal-lsp/pom.xml|;
    if (exists(lspPom), /"dependency"("groupid"("org.rascalmpl"), "artifactid"("rascal"), "version"(str rascalVersion)) := readXML(lspPom)) {
        return rascalVersion;
    }

    xml = readXML(lspPom);
    iprintln(xml);
    return "0.43.0";
}

node dependencyTemplate(str artifactId, str version, str groupId="org.rascalmpl")
    = "dependency"(
        "groupId"(groupId),
        "artifactId"(artifactId),
        "version"(version)
    );

node dependenciesTemplate(node dependencies...)
    = makeNode("dependencies", *dependencies);

void main(loc pomLoc = |project://rascal-vscode-extension/test-workspace/test-project/pom.xml|) {
    if (node pom := readXML(pomLoc, trackOrigins = true)) {
        // list[TextEdit] edits = [];
        if ("#document" := getName(pom), node project <- getChildren(pom), "project" := getName(project)) {
            projectChildren = getChildren(project);
            if (node dependenciesBlock <- projectChildren, "dependencies" := getName(dependenciesBlock)) {
                dependencies = getChildren(dependenciesBlock);
                if (just(rascal) := getRascalDependency(dependencies)) {
                    println("Found Rascal dependency: <getVersion(rascal)>");
                    // Check minimal version
                    ;
                } else {
                    // No Rascal dependency; offer to add one
                    println("No Rascal dependency found!");
                    rascalLsp = getRascalLspDependency(dependencies);
                    rascalVersion = inferRascalVersion(rascalLsp=getVersion(rascalLsp));
                    rascalEdit = insertAfter(dependenciesBlock.src, xmlPretty(toXML(dependencyTemplate("rascal", rascalVersion))));
                    iprintln(rascalEdit);
                }
            } else {
                println("No dependencies block found");
                versionBlock = getFirstFrom([c | node c <- projectChildren, "version" := getName(c)]);
                rascalEdit = insertAfter(versionBlock.src, xmlPretty(toXML(dependenciesTemplate(dependencyTemplate("rascal", inferRascalVersion())))));
                iprintln(rascalEdit);
            }
        } else {
            println("Unrecoverably broken POM!");
            ; // This a very sparse POM. We can probably not recover from this.
        }
    }
}
