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

data PathConfig;

private tuple[str, loc] fullQualifiedName(QualifiedName qn) = <"<qn>", qn.src>;
private tuple[str, loc] qualifiedPrefix(QualifiedName qn) {
    list[Name] names = [n | n <- qn.names];
    if (size(names) <= 1) return <"", |unknown:///|>;

    str fullName = "<qn>";
    str namePrefix = substring(fullName, 0, findLast(fullName, "::"));
    loc prefixLoc = cover([n.src | Name n <- prefix(names)]);

    return <namePrefix, prefixLoc>;
}

list[TextEdit] getChanges(loc f, map[str, str] qualifiedNameChanges) {
    list[TextEdit] changes = [];

    start[Module] m = parseModuleWithSpacesCached(f);
    for (/QualifiedName qn := m) {
        if (<fullName, fullLoc> := fullQualifiedName(qn), fullName in qualifiedNameChanges) {
            changes += replace(fullLoc, qualifiedNameChanges[fullName]);
        } else if (<namePrefix, prefixLoc> := qualifiedPrefix(qn), namePrefix in qualifiedNameChanges) {
            changes += replace(prefixLoc, qualifiedNameChanges[namePrefix]);
        }
    }

    return changes;
}

Edits propagateModuleRenames(map[str, str] qualifiedNameChanges, set[loc] workspaceFolders) {
    set[loc] wsFiles = flatMap(workspaceFolders, set[loc](loc wsFolder) {
        return find(wsFolder, "rsc");
    });

    set[DocumentEdit] edits = {changed(file, changes)
        | loc file <- wsFiles
        , changes := getChanges(file, qualifiedNameChanges)
        , changes != []
    };

    return <toList(edits), ()>;
}
