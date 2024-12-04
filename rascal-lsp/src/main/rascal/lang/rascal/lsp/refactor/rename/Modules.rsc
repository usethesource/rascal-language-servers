module lang::rascal::lsp::refactor::rename::Modules

import lang::rascal::lsp::refactor::TextEdits;
import lang::rascal::lsp::refactor::Util;

import lang::rascal::\syntax::Rascal;

import IO;
import List;
import Location;
import ParseTree;
import Set;
import String;

import util::FileSystem;
import util::Reflective;

private tuple[str, loc] fullQualifiedName(QualifiedName qn) = <"<qn>", qn.src>;
private tuple[str, loc] qualifiedPrefix(QualifiedName qn) {
    list[Name] names = [n | n <- qn.names];
    if (size(names) <= 1) return <"", |unknown:///|>;

    str fullName = "<qn>";
    str namePrefix = substring(fullName, 0, findLast(fullName, "::"));
    loc prefixLoc = cover([n.src | Name n <- prefix(names)]);

    return <namePrefix, prefixLoc>;
}

private bool isReachable(PathConfig toProject, PathConfig fromProject) =
    toProject == fromProject           // Both configs belong to the same project
 || toProject.bin in fromProject.libs; // The using project can import the declaring project

list[TextEdit] getChanges(loc f, PathConfig wsProject, rel[str oldName, str newName, PathConfig pcfg] qualifiedNameChanges) {
    list[TextEdit] changes = [];

    start[Module] m = parseModuleWithSpacesCached(f);
    for (/QualifiedName qn := m) {
        for (<oldName, l> <- {fullQualifiedName(qn), qualifiedPrefix(qn)}
           , {<newName, projWithRenamedMod>} := qualifiedNameChanges[oldName]
           , isReachable(projWithRenamedMod, wsProject)
           ) {
            changes += replace(l, newName);
        }
    }

    return changes;
}

Edits propagateModuleRenames(rel[str oldName, str newName, PathConfig pcfg] qualifiedNameChanges, set[loc] workspaceFolders, PathConfig(loc) getPathConfig) {
    set[PathConfig] projectWithRenamedModule = qualifiedNameChanges.pcfg;
    set[DocumentEdit] edits = flatMap(workspaceFolders, set[DocumentEdit](loc wsFolder) {
        PathConfig wsFolderPcfg = getPathConfig(wsFolder);

        // If this workspace cannot reach any of the renamed modules, no need to continue looking for references to renamed modules here at all
        if (!any(PathConfig changedProj <- projectWithRenamedModule, isReachable(changedProj, wsFolderPcfg))) return {};

        return {changed(file, changes)
            | loc file <- find(wsFolder, "rsc")
            , changes := getChanges(file, wsFolderPcfg, qualifiedNameChanges)
            , changes != []
        };
    });

    return <toList(edits), ()>;
}
