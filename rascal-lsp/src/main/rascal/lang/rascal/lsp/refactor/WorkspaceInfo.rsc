module lang::rascal::lsp::refactor::WorkspaceInfo

import IO;
import Relation;

import analysis::typepal::TModel;

import lang::rascalcore::check::Checker;

import util::FileSystem;
import util::Reflective;

data WorkspaceInfo (
    rel[loc use, loc def] useDef = {},
    set[Define] defines = {},
    set[loc] modules = {}
) = workspaceInfo(set[loc] folders, PathConfig pcfg);

private WorkspaceInfo loadModel(WorkspaceInfo ws, TModel tm) {
    ws.useDef += tm.useDef;
    ws.defines += tm.defines;

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
set[Define] getDefines(WorkspaceInfo ws, set[loc] defs) = {def | d <- defs, def:<_, _, _, _, d, _> <- ws.defines};
set[Define] getDefines(WorkspaceInfo ws, loc useDef) = getDefines(ws, getDefs(ws, useDef) + useDef);
