module lang::rascal::lsp::refactor::WorkspaceInfo

import IO;
import Relation;

import analysis::typepal::TModel;

import lang::rascalcore::check::Checker;

import util::FileSystem;
import util::Reflective;

alias MayOverloadFun = bool(set[loc] defs, map[loc, Define] defines);

data WorkspaceInfo (
    rel[loc use, loc def] useDef = {},
    set[Define] defines = {},
    set[loc] modules = {},
    map[loc, Define] definitions = ()
) = workspaceInfo(set[loc] folders, PathConfig pcfg);

private WorkspaceInfo loadModel(WorkspaceInfo ws, TModel tm) {
    ws.useDef += tm.useDef;
    ws.defines += tm.defines;
    ws.definitions += tm.definitions;

    return ws;
}

WorkspaceInfo gatherWorkspaceInfo(set[loc] folders, PathConfig pcfg) {
    ws = workspaceInfo(folders, pcfg);

    mods = [m | f <- folders, m <- find(f, "rsc")];
    ms = rascalTModelForLocs(mods, getRascalCoreCompilerConfig(pcfg), dummy_compile1);

    for (m <- ms.tmodels) {
        ws = loadModel(ws, ms.tmodels[m]);
        ws.modules += {ms.moduleLocs[m].top};
    }

    return ws;
}

set[loc] getUses(WorkspaceInfo ws, loc def) = invert(ws.useDef)[def];

set[loc] getDefs(WorkspaceInfo ws, loc use) = ws.useDef[use];

set[Define] getOverloadedDefines(WorkspaceInfo ws, set[loc] defs, MayOverloadFun mayOverloadF) {
    set[loc] overloadedLocs = defs;
    set[Define] overloadedDefs = {ws.definitions[l] | l <- defs};

    // Pre-condition
    assert mayOverloadF(overloadedLocs, ws.definitions):
        "Initial defs are invalid overloads!";

    for (loc d <- ws.definitions) {
        if (mayOverloadF(defs + d, ws.definitions)) {
            overloadedLocs += d;
            overloadedDefs += ws.definitions[d];
        }
    }

    return overloadedDefs;
}

set[Define] getOverloadedDefines(WorkspaceInfo ws, loc useDef, MayOverloadFun mayOverloadF) =
    getOverloadedDefines(ws, getDefs(ws, useDef) != {} ? getDefs(ws, useDef) : {useDef}, mayOverloadF);
