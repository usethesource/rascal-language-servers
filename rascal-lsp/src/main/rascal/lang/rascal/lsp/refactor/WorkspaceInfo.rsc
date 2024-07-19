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
module lang::rascal::lsp::refactor::WorkspaceInfo

import Relation;

import analysis::typepal::TModel;

import lang::rascalcore::check::Checker;

import util::FileSystem;
import util::Monitor;
import util::Reflective;

import lang::rascal::lsp::refactor::Exception;
import lang::rascal::lsp::refactor::Util;

import List;
import Location;
import Map;
import Message;
import Set;
import String;


data CursorKind = use()
                | def()
                | typeParam()
                | collectionField()
                ;

data Cursor = cursor(CursorKind kind, loc l, str name);

alias MayOverloadFun = bool(set[loc] defs, map[loc, Define] defines);

data WorkspaceInfo (
    rel[loc use, loc def] useDef = {},
    set[Define] defines = {},
    set[loc] modules = {},
    map[loc, Define] definitions = (),
    map[loc, AType] facts = ()
) = workspaceInfo(set[loc] folders, PathConfig(loc) getPathConfig);

private WorkspaceInfo loadModel(WorkspaceInfo ws, TModel tm) {
    ws.useDef += tm.useDef;
    ws.defines += tm.defines;
    ws.definitions += tm.definitions;
    ws.facts += tm.facts;

    return ws;
}

private void checkNoErrors(ModuleStatus ms) {
    errors = (m: msgs | m <- ms.messages
                      , msgs := [msg | msg <- ms.messages[m], msg is error]
                      , msgs != []);
    if (errors != ())
        throw unsupportedRename("Cannot rename: some modules in workspace have errors.\n<toString(errors)>", issues={<(error.at ? |unknown:///|), error.msg> | m <- errors, error <- errors[m]});
}

WorkspaceInfo gatherWorkspaceInfo(set[loc] folders, PathConfig(loc) getPathConfig) = job("loading workspace information", WorkspaceInfo(void(str, int) step) {
    ws = workspaceInfo(folders, getPathConfig);

    for (f <- folders) {
        step("loading modules for project \'<f.file>\'", 1);
        PathConfig pcfg = getPathConfig(f);
        RascalCompilerConfig ccfg = getRascalCoreCompilerConfig(pcfg);

        ms = rascalTModelForLocs(toList(find(f, "rsc")), ccfg, dummy_compile1);
        checkNoErrors(ms);

        for (m <- ms.tmodels) {
            tm = convertTModel2PhysicalLocs(ms.tmodels[m]);
            ws = loadModel(ws, tm);
            ws.modules += {ms.moduleLocs[m].top};
        }
    }

    return ws;
}, totalWork = size(folders));

@memo
set[loc] getUses(WorkspaceInfo ws, loc def) = invert(ws.useDef)[def];

@memo
set[loc] getUses(WorkspaceInfo ws, set[loc] defs) = invert(ws.useDef)[defs];

@memo
set[loc] getDefs(WorkspaceInfo ws, loc use) = ws.useDef[use];

set[loc] getOverloadedDefs(WorkspaceInfo ws, set[loc] defs, MayOverloadFun mayOverloadF) {
    set[loc] overloadedLocs = defs;

    // Pre-condition
    assert mayOverloadF(overloadedLocs, ws.definitions):
        "Initial defs are invalid overloads!";

    for (loc d <- ws.definitions) {
        if (mayOverloadF(defs + d, ws.definitions)) {
            overloadedLocs += d;
        }
    }

    return overloadedLocs;
}

tuple[set[loc], set[loc]] getDefsUses(WorkspaceInfo ws, cursor(use(), l, _), MayOverloadFun mayOverloadF) {
    defs = getOverloadedDefs(ws, getDefs(ws, l), mayOverloadF);
    uses = getUses(ws, defs);
    return <defs, uses>;
}

tuple[set[loc], set[loc]] getDefsUses(WorkspaceInfo ws, cursor(def(), l, _), MayOverloadFun mayOverloadF) {
    defs = getOverloadedDefs(ws, {l}, mayOverloadF);
    uses = getUses(ws, defs);
    return <defs, uses>;
}

tuple[set[loc], set[loc]] getDefsUses(WorkspaceInfo ws, cursor(typeParam(), cursorLoc, cursorName), MayOverloadFun _) {
    AType at = ws.facts[cursorLoc];
    if(Define d: <_, _, _, _, _, defType(afunc(_, /at, _))> <- ws.defines, isContainedIn(cursorLoc, d.defined)) {
        // From here on, we can assume that all locations are in the same file, because we are dealing with type parameters and filtered on `isContainedIn`
        facts = {<l, ws.facts[l]> | l <- ws.facts
                                  , at := ws.facts[l]
                                  , isContainedIn(l, d.defined)};

        formals = {l | <l, f> <- facts, f.alabel != ""};

        // Given the location/offset of `&T`, find the location/offset of `T`
        offsets = sort({l.offset | l <- facts<0>});
        nextOffsets = toMapUnique(zip2(prefix(offsets), tail(offsets)));

        loc sentinel = |unknown:///|(0, 0, <0, 0>, <0, 0>);
        defs = {(sentinel | (it == sentinel || f.length < it.length) && f.offset == nextOffset ? f : it | f <- facts<0>) | formal <- formals, nextOffsets[formal.offset]?, nextOffset := nextOffsets[formal.offset]};

        useDefs = {trim(l, removePrefix = l.length - size(cursorName))
                    | l <- facts<0>
                    , !ws.definitions[l]? // If there is a definition at this location, this is a formal argument name
                    , !any(ud <- ws.useDef[l], ws.definitions[ud]?) // If there is a definition for the use at this location, this is a use of a formal argument
                    , !any(flInner <- facts<0>, isStrictlyContainedIn(flInner, l)) // Filter out any facts that contain other facts
        };

        return <defs, useDefs - defs>;
    }

    throw unsupportedRename("Cannot find function definition which defines template variable \'<cursorName>\'");
}
