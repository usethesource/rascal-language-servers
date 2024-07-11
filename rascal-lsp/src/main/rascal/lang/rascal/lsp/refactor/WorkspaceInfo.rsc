module lang::rascal::lsp::refactor::WorkspaceInfo

import Relation;

import analysis::typepal::TModel;

import lang::rascalcore::check::Checker;

import util::FileSystem;
import util::Reflective;

import lang::rascal::lsp::refactor::Exception;
import lang::rascal::lsp::refactor::Util;

import List;
import Location;
import Message;
import Set;
import String;


data CursorKind = use()
                | def()
                | typeParam();

data Cursor = cursor(CursorKind kind, loc l, str name);

alias MayOverloadFun = bool(set[loc] defs, map[loc, Define] defines);

data WorkspaceInfo (
    rel[loc use, loc def] useDef = {},
    set[Define] defines = {},
    set[loc] modules = {},
    map[loc, Define] definitions = (),
    map[loc, AType] facts = ()
) = workspaceInfo(set[loc] folders, PathConfig pcfg);

private WorkspaceInfo loadModel(WorkspaceInfo ws, TModel tm) {
    ws.useDef += tm.useDef;
    ws.defines += tm.defines;
    ws.definitions += tm.definitions;
    ws.facts += tm.facts;

    return ws;
}

private void checkNoErrors(ModuleStatus ms) {
    errors = [msg | m <- ms.messages, msg <- ms.messages[m], msg is error];
    if (errors != [])
        throw unsupportedRename(errors);
}

WorkspaceInfo gatherWorkspaceInfo(set[loc] folders, PathConfig pcfg) {
    ws = workspaceInfo(folders, pcfg);

    mods = [m | f <- folders, m <- find(f, "rsc")];
    ms = rascalTModelForLocs(mods, getRascalCoreCompilerConfig(pcfg), dummy_compile1);
    checkNoErrors(ms);

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

        useDefs = {trim(l, removePrefix=prefixLength)
                    | l <- facts<0>
                    , !ws.definitions[l]? // If there is a definition at this location, this is a formal argument name
                    , !any(ud <- ws.useDef[l], ws.definitions[ud]?) // If there is a definition for the use at this location, this is a use of a formal argument
                    , !any(flInner <- facts<0>, isStrictlyContainedIn(flInner, l)) // Filter out any facts that contain other facts
                    , prefixLength := l.length - size(cursorName)
        };

        return <defs, useDefs - defs>;
    }

    throw unsupportedRename({<cursorLoc, "Cannot find function definition which defines template variable \'<cursorName>\'">});
}
