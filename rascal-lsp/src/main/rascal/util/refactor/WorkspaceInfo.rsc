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
module util::refactor::WorkspaceInfo

import Map;
import Message;
import Relation;
import util::Maybe;

import util::refactor::Exception;
import util::refactor::TextEdits;
import util::Util;

import analysis::typepal::TModel;

alias FileRenamesF = rel[loc old, loc new](str newName);
alias RenameLocation = tuple[loc l, Maybe[ChangeAnnotationId] annotation];
alias DefsUsesRenames = tuple[set[RenameLocation] defs, set[RenameLocation] uses, FileRenamesF renames];
alias ProjectFiles = rel[loc projectFolder, bool loadModel, loc file];

data PathConfig; // util::Reflective

// Extend the TModel to include some workspace information.
data TModel (
    set[loc] projects = {},
    set[loc] sourceFiles = {}
);

set[TModel] tmodelsForProjectFiles(ProjectFiles projectFiles, set[TModel](set[loc], PathConfig) tmodelsForFiles, PathConfig(loc) getPathConfig) =
    ({} | it + tmodelsForFiles(projectFiles[pf, true], pcfg) | pf <- projectFiles.projectFolder, pcfg := getPathConfig(pf));

TModel loadLocs(TModel wsTM, ProjectFiles projectFiles, set[TModel](set[loc], PathConfig) tmodelsForFiles, PathConfig(loc) getPathConfig) {
    wsTM = (wsTM | appendTModel(it, modTM) | modTM <- tmodelsForProjectFiles(projectFiles, tmodelsForFiles, getPathConfig));

    // In addition to data from the loaded TModel, we keep track of which projects/modules we loaded.
    wsTM.sourceFiles += projectFiles.file;
    wsTM.projects += projectFiles.projectFolder;

    return wsTM;
}

TModel appendTModel(TModel to, TModel from) {
    try {
        throwAnyErrors(from);
    } catch set[Message] errors: {
        throw unsupportedRename("Cannot rename: some files in workspace have errors.\n<toString(errors)>", issues={<(error.at ? |unknown:///|), error.msg> | error <- errors});
    }

    to.useDef      += from.useDef;
    to.defines     += from.defines;
    to.definitions += from.definitions;
    to.facts       += from.facts;
    to.scopes      += from.scopes;
    to.paths       += from.paths;

    return to;
}

loc getProjectFolder(TModel ws, loc l) {
    if (project <- ws.projects, isPrefixOf(project, l)) {
        return project;
    }

    throw "Could not find project containing <l>";
}

@memo{maximumSize(1), expireAfter(minutes=5)}
rel[loc, loc] defUse(TModel ws) = invert(ws.useDef);

@memo{maximumSize(1), expireAfter(minutes=5)}
map[AType, set[loc]] factsInvert(TModel ws) = invert(ws.facts);

set[loc] getUses(TModel ws, loc def) = defUse(ws)[def];

set[loc] getUses(TModel ws, set[loc] defs) = defUse(ws)[defs];

set[loc] getDefs(TModel ws, loc use) = ws.useDef[use];

Maybe[AType] getFact(TModel ws, loc l) = l in ws.facts ? just(ws.facts[l]) : nothing();

@memo{maximumSize(1), expireAfter(minutes=5)}
rel[loc, Define] definitionsRel(TModel ws) = toRel(ws.definitions);

set[RenameLocation] annotateLocs(set[loc] locs, Maybe[ChangeAnnotationId] annotationId = nothing()) = {<l, annotationId> | l <- locs};
