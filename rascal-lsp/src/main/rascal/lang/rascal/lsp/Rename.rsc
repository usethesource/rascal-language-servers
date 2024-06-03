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

import IO;
import ParseTree;
import Set;
import String;

import lang::rascal::\syntax::Rascal;
import lang::rascalcore::check::Summary;
import analysis::diff::edits::TextEdits;

// For testing
import util::Reflective;

// TODO Implement
// Compute the edits for the complete workspace here
list[DocumentEdit] renameRascalSymbol(start[Module] m, Tree cursor, set[loc] workspaceFolders, PathConfig pcfg, str newName) {
    loc cursorLoc = cursor.src;

    println("Calculating renames from <cursor> to <newName> at <cursorLoc>");
    println("Workspace folders: <workspaceFolders>");

    loc moduleLoc = m.src.top;
    str qualifiedModuleName = getModuleName(moduleLoc, pcfg);
    println("Name of current module: <qualifiedModuleName>");
    ModuleSummary summary = makeSummary(qualifiedModuleName, pcfg);

    set[loc] defs = getDefinitions(summary, cursorLoc);
    set[loc] uses = getUses(summary, cursorLoc);

    // TODO Detect shadowing by rename

    println("Definition locations (excluding cursor): <defs>");
    println("Usage locations      (excluding cursor): <uses>");

    set[loc] useDefs = defs + uses + cursorLoc;
    set[loc] useDefFiles = {useDef.top | loc useDef <- useDefs};
    map[loc, set[loc]] useDefsPerFile = (file : {useDef | loc useDef <- useDefs, useDef.top == file} | loc file <- useDefFiles);
    println("Use/Defs per file: <useDefsPerFile>");

    // TODO If the cursor was a module name, we need to rename a file as well

    // TODO If the cursor was a function call, be sure to rename the definition correctly
    // It seems that the definition location points to the whole function definition (instead of the name)

    // TODO Check type/alias renames

    list[DocumentEdit] renames = [];
    list[DocumentEdit] changes = [changed(file, [replace(useDef, newName) | useDef <- useDefsPerFile[file]]) | loc file <- useDefsPerFile];

    return changes + renames;
}

void main() {
    loc f = |file:///C:/Users/toine/swat/projects/Rascal/rascal-language-servers/rascal-vscode-extension/test-workspace/test-project/src/main/rascal/Main.rsc|;
    start[Module] m = parseModuleWithSpaces(f);
    Tree cursor = [n | /FunctionBody b := m.top, /Name n := b, "<n>" == "x"][0];
    set[loc] workspaceFolders = {};

    list[DocumentEdit] edits = renameRascalSymbol(m, cursor, workspaceFolders, "y");

    println(edits);
}
