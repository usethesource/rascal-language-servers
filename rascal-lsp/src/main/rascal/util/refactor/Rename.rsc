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
module util::refactor::Rename

import ParseTree;
import Set;
import util::Maybe;
import util::Reflective;

import util::refactor::Exception;
import util::refactor::TextEdits;
import util::refactor::WorkspaceInfo;

import analysis::typepal::TModel;

@synopsis{ To be extended by implementations. }
data Cursor;

@synopsis{ Return type for rename refactoring. }
alias Edits = tuple[list[DocumentEdit], map[ChangeAnnotationId, ChangeAnnotation]];
@synopsis{ Return type for rename validity checks. }
alias CheckResult = set[IllegalRenameReason];
@synopsis {Get a path config for a project folder. }
alias PathConfigF = PathConfig(loc);
@synopsis { A rename validity check that does not require type check information. }
alias PreCheckF = CheckResult(Tree cursorT, str newName, set[loc] workspaceFolders, PathConfigF getPathConfig);
@synopsis{ A function that checks whether the produced rename candidates are valid. }
alias PostCheckF = CheckResult(TModel tm, loc moduleLoc, str newName, set[RenameLocation] defs, set[RenameLocation] uses);
@synopsis{ A function that selects project files to load. }
alias PreFilesF = ProjectFiles(Tree cursorT, set[loc] workspaceFolders, PathConfigF getPathConfig);
alias AllFilesF = ProjectFiles(Cursor cur, set[loc] workspaceFolders, PathConfigF getPathConfig);
@synopsis{ A function that, given a set of file locs and a path config, returns a set of TModels. }
alias TModelsF = set[TModel](set[loc] fs, PathConfig pcfg);
@synopsis{ A function that, given a TModel and the Tree under the cursor, returns a Cursor. }
alias CursorF = Cursor(TModel tm, Tree cursorT);
@synopsis{ A function that determines rename candidates. }
alias DefsUsesF = DefsUsesRenames(TModel tm, Cursor cur, ChangeAnnotationRegister registerChangeAnnotation, PathConfigF getPathConfig);
@synopsis{ A function that finds the location of names in candidates. }
alias FindNamesF = rel[loc, loc](loc l, set[loc] candidates);
@synopsis{ Escape a name. }
alias EscapeNameF = str(str name);
@synopsis{ The type of the rename function, as expected by the language server. }
alias RenameSymbolF = Edits(Tree cursorT, str newName, set[loc] workspaceFolders, PathConfigF getPathConfig);

// TODO Make parameter
str MANDATORY_CHANGE_DESCRIPTION = "These changes are required for a correct renaming. They can be previewed here, but it is not advised to disable them.";

RenameSymbolF renameSymbolFramework(
    PreCheckF preCheck
  , PostCheckF postCheck
  , PreFilesF preFiles
  , AllFilesF allFiles
  , TModelsF getTModels
  , EscapeNameF escapeName
  , CursorF getCursor
  , DefsUsesF getDefsUses
  , FindNamesF findNames
) {
    return Edits(Tree cursorT, str newName, set[loc] workspaceFolders, PathConfigF getPathConfig) {
        // step("pre-checking rename validity", 1);
        checkResult(preCheck(cursorT, newName, workspaceFolders, getPathConfig));

        // step("preloading minimal workspace information", 1);
        TModel tm = tmodel();
        ProjectFiles preloadFiles = preFiles(cursorT, workspaceFolders, getPathConfig);
        tm = loadLocs(tm, preloadFiles, getTModels, getPathConfig);

        // step("analyzing name at cursor", 1);
        Cursor cur = getCursor(tm, cursorT);

        // step("loading required type information", 1);
        ProjectFiles allLoadFiles = allFiles(cur, workspaceFolders, getPathConfig);
        tm = loadLocs(tm, allLoadFiles, getTModels, getPathConfig);

        // step("collecting definitions and uses of \'<cursorName>\'", 1);
        map[ChangeAnnotationId, ChangeAnnotation] changeAnnotations = ();
        ChangeAnnotationRegister registerChangeAnnotation = ChangeAnnotationId(str label, str description, bool needsConfirmation) {
            ChangeAnnotationId makeKey(str label, int suffix) = "<label>_<suffix>";

            int suffix = 1;
            while (makeKey(label, suffix) in changeAnnotations) {
                suffix += 1;
            }

            ChangeAnnotationId id = makeKey(label, suffix);
            changeAnnotations[id] = changeAnnotation(label, description, needsConfirmation);

            return id;
        };

        <defs, uses, getRenames> = getDefsUses(tm, cur, registerChangeAnnotation, getPathConfig);

        rel[loc file, RenameLocation defines] defsPerFile = {<d.l.top, d> | d <- defs};
        rel[loc file, RenameLocation uses] usesPerFile = {<u.l.top, u> | u <- uses};
        set[loc] \files = defsPerFile.file + usesPerFile.file;

        // step("checking rename validity", 1);
        map[loc, tuple[set[IllegalRenameReason] reasons, list[TextEdit] edits]] fileResults =
            (file: <reasons, edits> | file <- \files, <reasons, edits> :=
                computeTextEdits(
                    tm
                  , file
                  , defsPerFile[file]
                  , usesPerFile[file]
                  , newName
                  , registerChangeAnnotation
                  , postCheck
                  , escapeName
                  , findNames
                )
            );

        if (reasons := union({fileResults[file].reasons | file <- fileResults}), reasons != {}) {
            list[str] reasonDescs = toList({describe(r) | r <- reasons});
            throw illegalRename("Rename is not valid, because:\n - <intercalate("\n - ", reasonDescs)>", reasons);
        }

        list[DocumentEdit] changes = [changed(file, fileResults[file].edits) | file <- fileResults];
        list[DocumentEdit] renames = [renamed(from, to) | <from, to> <- getRenames(newName)];

        return <changes + renames, changeAnnotations>;
    };
}
private void checkResult(CheckResult r, str msg = "Check failed") {
    if (size(r) > 0) {
        throw illegalRename(msg, r);
    }
}

private tuple[set[IllegalRenameReason] reasons, list[TextEdit] edits] computeTextEdits(
    TModel ws
  , loc moduleLoc
  , set[RenameLocation] defs
  , set[RenameLocation] uses
  , str name
  , ChangeAnnotationRegister registerChangeAnnotation
  , PostCheckF postCheck
  , EscapeNameF escapeName
  , FindNamesF findNames
) {
    if (reasons := postCheck(ws, moduleLoc, name, defs, uses), reasons != {}) {
        return <reasons, []>;
    }

    replaceName = escapeName(name);

    rel[loc l, Maybe[ChangeAnnotationId] ann, bool isDef] renames =
        {<l, a, true>  | <l, a> <- defs}
      + {<l, a, false> | <l, a> <- uses};
    rel[loc name, loc useDef] nameOfUseDef = findNames(moduleLoc, renames.l);

    ChangeAnnotationId defAnno = registerChangeAnnotation("Definitions", MANDATORY_CHANGE_DESCRIPTION, false);
    ChangeAnnotationId useAnno = registerChangeAnnotation("References", MANDATORY_CHANGE_DESCRIPTION, false);

    // Note: if the implementer of the rename logic has attached annotations to multiple rename suggestions that have the same
    // name location, one will be arbitrarily chosen here. This could mean that a `needsConfirmation` annotation is thrown away.
    return <{}, [{just(annotation), *_} := renameOpts.ann
                 ? replace(l, replaceName, annotation = annotation)
                 : replace(l, replaceName, annotation = true in renameOpts.isDef ? defAnno : useAnno)
                 | l <- nameOfUseDef.name
                 , rel[Maybe[ChangeAnnotationId] ann, bool isDef] renameOpts := renames[nameOfUseDef[l]]]>;
}
