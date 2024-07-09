module lang::rascal::lsp::refactor::WorkspaceInfo

import Relation;

import analysis::typepal::TModel;

import lang::rascalcore::check::Checker;

import util::FileSystem;
import util::Reflective;

data Cursor = use(loc l)
            | def(loc l)
            | field(loc l)
            ;

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
        tm = convertTModel2PhysicalLocs(ms.tmodels[m]);
        ws = loadModel(ws, tm);
        ws.modules += {ms.moduleLocs[m].top};
    }

    return ws;
}

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

set[loc] getRelatedDefs(WorkspaceInfo ws, use(cursor), MayOverloadFun mayOverloadF) =
    getOverloadedDefs(ws, getDefs(ws, cursor), mayOverloadF);

set[loc] getRelatedDefs(WorkspaceInfo ws, def(cursor), MayOverloadFun mayOverloadF) =
    getOverloadedDefs(ws, {cursor}, mayOverloadF);
