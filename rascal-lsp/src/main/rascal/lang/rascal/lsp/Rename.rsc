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
module lang::rascal::lsp::Rename

import Exception;
import IO;
import List;
import Node;
import ParseTree;
import Set;
import String;

import lang::rascal::\syntax::Rascal;

import lang::rascalcore::check::BasicRascalConfig;
import lang::rascalcore::check::Checker;
import analysis::typepal::TypePal;
import lang::rascalcore::check::Summary;

import analysis::diff::edits::TextEdits;

import vis::Text;

// For testing
import util::Reflective;

data RuntimeException
    = IllegalRename(loc location, str message)
    | UnsupportedRename(loc location, str message)
    | UnexpectedFailure(str message)
;

// Compute the edits for the complete workspace here
list[DocumentEdit] renameRascalSymbol(start[Module] m, Tree cursor, set[loc] workspaceFolders, PathConfig pcfg, str newName) {
    loc cursorLoc = cursor.src;

    println("Calculating renames from <cursor> to <newName> at <cursorLoc>");
    println("Workspace folders: <workspaceFolders>");

    str moduleName = getModuleName(m.src.top, pcfg);
    ModuleSummary summary = makeSummary(moduleName, pcfg);

    if (newName in summary.vocabulary) {
        // This is VERY conservative.
        // As long as we cannot deduce (from the TModel) whether a rename is type and
        // semantics preserving, we reject any new name that is already used in the module.
        throw IllegalRename(cursorLoc, newName);
    }

    if (summary == moduleSummary()) {
        throw UnexpectedFailure("No TPL file found for module <moduleName>!");
    }

    set[loc] uses = getUses(summary, cursorLoc);
    set[loc] defs = ({} | it + getDefinitions(summary, use) | loc use <- uses + cursorLoc);

    if (uses == {}) {
        uses += cursorLoc;
    } else {
        defs += cursorLoc;
    }

    // TODO Check if all definitions are user-defined;
    // i.e. we're not trying to rename something from stdlib or compiled libraries(?)

    print("Definition locations ");
    iprintln(defs);

    print("Usage locations ");
    iprintln(uses);

    set[loc] useDefs = uses + defs;

    rel[loc file, loc useDefs] useDefsPerFile = { <useDef.top, useDef> | loc useDef <- useDefs};
    println("Use/Defs per file:");
    iprintln(toMap(useDefsPerFile));

    list[DocumentEdit] changes = [changed(file, [replace(findNameLocation(m, useDef), newName) | useDef <- useDefsPerFile[file]]) | loc file <- useDefsPerFile.file];;

    // TODO If the cursor was a module name, we need to rename files as well
    list[DocumentEdit] renames = [];

    list[DocumentEdit] edits = changes + renames;

    println("Edits:");
    iprintln(edits);

    return edits;
}

// Workaround to be able to pattern match on the emulated `src` field
data Tree (loc src = |unknown:///|(0,0,<0,0>,<0,0>));

loc findNameLocation(start[Module] m, loc declLoc) {
    // we want to find the smallest tree of defined non-terminal type with source location `declLoc`
    visit(m.top) {
        case t: appl(prod(_, _, _), _, src = declLoc): {
            return findNameLocation(t);
        }
    }

    throw "No declaration at <declLoc> found within module <m>";
}

loc findNameLocation(Name n) = n.src;
loc findNameLocation(QualifiedName qn) = (qn.names[-1]).src;
loc findNameLocation(FunctionDeclaration f) = f.signature.name.src when f.visibility is \private;
loc findNameLocation(FunctionDeclaration f) {
    throw UnsupportedRename(f.src, "Renaming public functions is not supported");
}

default loc findNameLocation(Tree t) {
    println("Unsupported: cannot find name in <t.src>");
    print(prettyTree(t));
    rprintln(t);
    throw UnsupportedRename(t, "Cannot find name in <t>");
}

test bool shadowRenameWhatever() {
    try {
    renameTextFixture("
        'int foo = 8;
        '{
        '    int bar = 9;
        '}
    ");
    }
    catch IllegalArgument(_, _): {
        return true;
    }

    return false;
}

void main() {
    PathConfig pcfg = pathConfig(
        bin=|target://test-project|,
        libs=[
            |lib://rascal|,
            |target://test-lib|],
        srcs=[resolveLocation(|project://rascal-vscode-extension/test-workspace/test-project/src/main/rascal|)]);

    // PathConfig pcfg = getDefaultTestingPathConfig();
    TypePalConfig compilerConfig = rascalTypePalConfig();

    loc f = resolveLocation(|project://rascal-vscode-extension/test-workspace/test-project/src/main/rascal/RenameTest.rsc|);

    // TODO Trigger type checker
    // list[ModuleMessages] msgs = checkAll(f, compilerConfig);

    start[Module] m = parseModuleWithSpaces(f);
    Tree cursor = [n | /FunctionBody b := m.top, /Name n := b, "<n>" == "x"][0];
    set[loc] workspaceFolders = {};

    list[DocumentEdit] edits = renameRascalSymbol(m, cursor, workspaceFolders, pcfg, "y");
}
