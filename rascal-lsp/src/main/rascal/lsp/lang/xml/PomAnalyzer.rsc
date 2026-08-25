module lang::xml::PomAnalyzer

import lang::xml::DOM;
import lang::xml::IO;
import analysis::diff::edits::ExecuteTextEdits;
import analysis::diff::edits::TextEdits;

import IO;
import List;
import Node;
import String;
import util::IDEServices;
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

node getChildNode(node n, str name) {
    if (node child <- getChildren(n), name := getName(child)) {
        return child;
    }
    throw "No child with name \'<name>\' in \'<getName(n)>\': <[getName(c) | node c <- getChildren(n)]>";
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

str prettyXML(node xml) {
    prettied = xmlPretty(toXML(xml));
    // `xmlPretty` assumes the node is a document and thus adds an `<?xml ...?>` tag at the start
    return intercalate("\n", split("\n", prettied)[1..]);
}

void main(loc pomLoc = |project://rascal-vscode-extension/test-workspace/test-project/pom.xml|) {
    if (node pom := readXML(pomLoc, trackOrigins = true)) {
        // list[TextEdit] edits = [];
        if (project := getChildNode(pom, "project")) {
            if (dependenciesBlock := getChildNode(project, "dependencies")) {
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
                    insertionPoint = getFirstFrom(getChildren(dependenciesBlock));
                    rascalEdit = insertAfter(dependenciesBlock.src, prettyXML(dependencyTemplate("rascal", rascalVersion)));
                    iprintln(rascalEdit);
                    applyFileSystemEdits([changed([rascalEdit])]);
                }
            } else {
                println("No dependencies block found");
                rascalEdit = insertAfter(getChildNode(project, "version").src, xmlPretty(toXML(dependenciesTemplate(dependencyTemplate("rascal", inferRascalVersion())))));
                iprintln(rascalEdit);
                applyFileSystemEdits([changed([rascalEdit])]);
            }
        } else {
            println("Unrecoverably broken POM!");
            ; // This a very sparse POM. We can probably not recover from this.
        }
    }
}
