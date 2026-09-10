module lang::xml::PomAnalyzer

import lang::xml::DOM;
import lang::xml::IO;
import analysis::diff::edits::ExecuteTextEdits;
import analysis::diff::edits::TextEdits;

import Boolean;
import Exception;
import IO;
import List;
import Node;
import String;
import util::IDEServices;
import util::Maybe;
import util::Reflective;

data Dependencies(loc src=|unknown:///|)
    = dependencies(list[Dependency] dependencies)
    | missing()
    ;

data Dependency(loc src=|unknown:///|)
    = dependency(list[Coordinate] coordinates)
    ;

data Coordinate(loc src=|unknown:///|)
    = groupId(str groupId)
    | artifactId(str artifactId)
    | version(str version)
    | classifier(str classifier)
    | \type(str \type)
    | optional(bool optional)
    | scope(str scope)
    ;

Coordinate implode(c:"groupId"(str groupId)) = Coordinate::groupId(groupId, src=src) when loc src := c.src;
Coordinate implode(c:"artifactId"(str artifactId)) = Coordinate::artifactId(artifactId, src=src) when loc src := c.src;
Coordinate implode(c:"version"(str version)) = Coordinate::version(version, src=src) when loc src := c.src;
Coordinate implode(c:"classifier"(str classifier)) = Coordinate::classifier(classifier, src=src) when loc src := c.src;
Coordinate implode(c:"type"(str \type)) = Coordinate::\type(\type, src=src) when loc src := c.src;
Coordinate implode(c:"optional"(str optional)) = Coordinate::optional(fromString(optional), src=src) when loc src := c.src;
Coordinate implode(c:"scope"(str scope)) = Coordinate::scope(scope, src=src) when loc src := c.src;
default Coordinate implode(value v) { throw IllegalArgument("Unexpected coordinate <v>"); }

str yield(groupId(groupId)) = "\<groupId\><groupId>\</groupId\>";
str yield(artifactId(artifactId)) = "\<artifactId\><artifactId>\</artifactId\>";
str yield(version(version)) = "\<version\><version>\</version\>";
str yield(classifier(groupId)) = "\<classifier\><groupId>\</classifier\>";
str yield(\type(\type)) = "\<type\><\type>\</type\>";
str yield(optional(optional)) = "\<optional\><optional>\</optional\>";
str yield(scope(scope)) = "\<scope\><scope>\</scope\>";
default str yield(Coordinate c) { throw IllegalArgument("Unexpected coordinate <c>"); }

Dependency implode(node n)
    = dependency([implode(coordinate) | node coordinate <- getChildren(n)], src=src)
    when getName(n) == "dependency", loc src := n.src;

Dependencies implodeDependencies(node n)
    = dependencies([implode(dependency) | node dependency <- getChildren(n)], src=src)
    when getName(n) == "dependencies", loc src := n.src;


node getChildNode(node n, str name) {
    if (node child <- getChildren(n), name := getName(child)) {
        return child;
    }
    throw "No child with name \'<name>\' in \'<getName(n)>\': <[getName(c) | node c <- getChildren(n)]>";
}

Dependencies getDependencies(node pom) {
    try {
        if (project := getChildNode(pom, "project"), dependencies := getChildNode(project, "dependencies")) {
            return implodeDependencies(dependencies);
        }
    } catch value _:;
    return missing();
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

str inferNewline(str pomSrc, list[str] pomLines) {
    return pomSrc[size(pomLines[0])+1..findFirst(pomSrc, pomLines[1])];
}

node readPom(loc l) {
    if (node pom := readXML(l, trackOrigins=true, includeEndTags=true)) {
        return pom;
    }
    throw IllegalArgument("No pom at <l>");
}

Maybe[Dependency] getDependency(node pom, str groupId, str artifactId) {
    if (dep <- getDependencies(pom).dependencies, Coordinate::groupId(groupId) <- dep.coordinates, Coordinate::artifactId(artifactId) <- dep.coordinates) {
        return just(dep);
    }
    return nothing();
}

Maybe[Coordinate] getDependencyVersion(node pom, str groupId, str artifactId) {
    if (just(Dependency dep) := getDependency(pom, groupId, artifactId), v:Coordinate::version(_) <- dep.coordinates) {
        return just(v);
    }
    return nothing();
}

Maybe[str] getRascalVersionFromPom(loc pom) {
    return getRascalVersionFromPom(readPom(pom));
}

Maybe[str] getRascalVersionFromPom(node pom) {
    if (just(Coordinate::version(version)) := getDependencyVersion(pom, "org.rascalmpl", "rascal")) {
        return just(version);
    }
    return nothing();
}

Maybe[str] getRascalLspVersionFromPom(loc pom) {
    return getRascalLspVersionFromPom(readPom(pom));
}

Maybe[str] getRascalLspVersionFromPom(node pom) {
    if (just(Coordinate::version(version)) := getDependencyVersion(pom, "org.rascalmpl", "rascal-lsp")) {
        return just(version);
    }
    return nothing();
}

TextEdit upgradeRascalVersion(loc pomLoc, str newVersion) {
    pom = readPom(pomLoc);
    if (just(Coordinate version) := getDependencyVersion(pom, "org.rascalmpl", "rascal")) {
        return replace(version.src, "\<version\><newVersion>\</version\>");
    };
    throw IllegalArgument("No Rascal dependency found in <pomLoc>");
}

TextEdit addDependency(loc pomLoc, str groupId, str artifactId, str version) {
    pom = readPom(pomLoc);
    pomSrc = readFile(pomLoc);
    pomLines = readFileLines(pomLoc);

    indentation = inferIndentation(pom, pomLines);
    newline = inferNewline(pomSrc, pomLines);

    gId = Coordinate::groupId(groupId);
    aId = Coordinate::artifactId(artifactId);
    v = Coordinate::version(version);

    str makeDependencyXml(str baseIndentation)
        = "<baseIndentation>\<dependency\><newline><baseIndentation><indentation><yield(gId)><newline><baseIndentation><indentation><yield(aId)><newline><baseIndentation><indentation><yield(v)><newline><baseIndentation>\</dependency\><newline>";

    deps = getDependencies(pom);
    if (deps is missing) {
        if (node project := getChildNode(pom, "project"), node finalChild := getChildren(project)[-1], loc finalChildLoc := finalChild.src) {
            baseIndentation = pomLines[finalChildLoc.begin.line-1][..finalChildLoc.begin.column];
            println("baseIndentation: `<baseIndentation>`");
            return insertAfter(finalChildLoc, "<newline><newline><baseIndentation>\<dependencies\><newline><makeDependencyXml(baseIndentation + indentation)><baseIndentation>\</dependencies\>");
        } else {
            throw IllegalArgument("Invalid pom.xml at <pomLoc>");
        }
    }
    if ([dep, *_] := deps.dependencies) {
        // A dependencies block with dependencies
        baseIndentation = pomLines[dep.src.begin.line-1][..dep.src.begin.column];
        l = dep.src.top(dep.src.offset-dep.src.begin.column, 0, <dep.src.begin.line, 0>, <dep.src.begin.line, 0>);
        return replace(l, makeDependencyXml(baseIndentation));
    } else {
        // A dependencies block without dependencies
        baseIndentation = pomLines[deps.src.begin.line-1][..deps.src.begin.column];
        l = deps.src;
        return replace(l, "\<dependencies\><newline><makeDependencyXml(baseIndentation + indentation)><baseIndentation>\</dependencies\>");
    }
}

TextEdit addRascalDependency(loc pomLoc) {
    return addDependency(pomLoc, "org.rascalmpl", "rascal", getRascalVersion());
}

TextEdit addRascalLspDependency(loc pomLoc) {
    return addDependency(pomLoc, "org.rascalmpl", "rascal-lsp", "2.22.5");
}
