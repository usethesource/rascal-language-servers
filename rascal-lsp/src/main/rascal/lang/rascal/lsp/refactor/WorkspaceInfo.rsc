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

private WorkspaceInfo loadModule(WorkspaceInfo ws, loc l) {
    WorkspaceInfo loadModel(WorkspaceInfo ws, TModel tm) {
        ws.useDef += tm.useDef;
        ws.defines += tm.defines;

        return ws;
    }

    moduleLoc = l.top;

    if (moduleLoc notin ws.modules) {
        moduleName = getModuleName(moduleLoc, ws.pcfg);

        // Only check one module at a time, to limit the amount of memory used
        ms = rascalTModelForLocs([moduleLoc], getRascalCoreCompilerConfig(ws.pcfg), dummy_compile1);

        ws = loadModel(ws, ms.tmodels[moduleName]);
        ws.modules += { moduleLoc };
    }

    return ws;
}

WorkspaceInfo gatherWorkspaceInfo(set[loc] folders, PathConfig pcfg) {
    ws = workspaceInfo(folders, pcfg);
    for (f <- folders, m <- find(f, "rsc")) {
        ws = loadModule(ws, m);
    }
    return ws;
}

set[loc] getUses(WorkspaceInfo ws, loc def) = invert(ws.useDef)[def];
set[loc] getDefs(WorkspaceInfo ws, loc use) = ws.useDef[use];
set[Define] getDefines(WorkspaceInfo ws, set[loc] defs) = {def | d <- defs, def:<_, _, _, _, d, _> <- ws.defines};
set[Define] getDefines(WorkspaceInfo ws, loc useDef) = getDefines(ws, getDefs(ws, useDef) + useDef);
