module lang::rascal::lsp::WorkspaceInfo

import IO;
import Relation;

import analysis::typepal::TModel;

import lang::rascalcore::check::Checker;

import util::FileSystem;
import util::Reflective;

// Only needed until we move to newer type checker
// TODO Remove
import Message;
import lang::rascalcore::check::Import;
import ValueIO;

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

// ALL BELOW IS COPIED FROM THE TYPE CHECKER AND SHOULD BE REPLACED BY IMPORTS

// This is copied from the newest version of the type-checker and simplified for future compatibilty.
// TODO Remove once we migrate to a newer type checker version
data ModuleStatus =
    moduleStatus(
      map[str, list[Message]] messages,
      PathConfig pathConfig
   );

// This is copied from the newest version of the type-checker and simplified for future compatibilty.
// TODO Remove once we migrate to a newer type checker version
ModuleStatus newModuleStatus(PathConfig pcfg) = moduleStatus((), pcfg);

// This is copied from the newest version of the type-checker and simplified for future compatibilty.
// TODO Remove once we migrate to a newer type checker version
private tuple[bool, TModel, ModuleStatus] getTModelForModule(str m, ModuleStatus ms) {
    pcfg = ms.pathConfig;
    <found, tplLoc> = getTPLReadLoc(m, pcfg);
    if (!found) {
        return <found, tmodel(), moduleStatus((), pcfg)>;
    }

    try {
        tpl = readBinaryValueFile(#TModel, tplLoc);
        return <true, tpl, ms>;
    } catch e: {
        //ms.status[qualifiedModuleName] ? {} += not_found();
        return <false, tmodel(modelName=qualifiedModuleName, messages=[error("Cannot read TPL for <qualifiedModuleName>: <e>", tplLoc)]), ms>;
        //throw IO("Cannot read tpl for <qualifiedModuleName>: <e>");
    }
}
