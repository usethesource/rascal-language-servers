@license{
Copyright (c) 2024-2025, Swat.engineering
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
module framework::Rename

import framework::TextEdits;

import analysis::typepal::FailMessage;
import analysis::typepal::Messenger;
import analysis::typepal::TModel;

import util::Monitor;

import Exception;
import IO;
import List;
import Map;
import Message;
import Node;
import ParseTree;
import Relation;
import Set;

alias RenameResult = tuple[list[DocumentEdit], set[Message]];

// This leaves some room for more fine-grained steps should the user want to monitor that
private int WORKSPACE_WORK = 10;
private int FILE_WORK = 5;

data Renamer
    = renamer(
        void(FailMessage) msg
      , void(DocumentEdit) documentEdit
      , void(TextEdit) textEdit
      , RenameConfig() getConfig

      // Helpers
      , void(value, str) warning
      , void(value, str) info
      , void(value, str) error
    );

data RenameConfig
    = rconfig(
        Tree(loc) parseLoc
      , TModel(Tree) tmodelForTree
      , TModel(loc) tmodelForLoc = TModel(loc l) {
            return tmodelForTree(parseLoc(l));
        }
      , bool debug = true
      , str jobLabel = "Renaming"
    );

@synopsis{
    Applying edits through @link{analysis::diff::edits::ExecuteTextEdits} should happen in a specific order.
    Specifically, files should be created before they can be modified, and after renaming them, modifications/deletions should refer to the new name.
    This functions sorts edits in the following order.
        1. created
        2. changed
        3. renamed
        4. removed
}
list[DocumentEdit] sortDocEdits(list[DocumentEdit] edits) = sort(edits, bool(DocumentEdit e1, DocumentEdit e2) {
    if (e1 is created && !(e2 is created)) return true;
    if (e1 is changed && !(e2 is changed)) return !(e2 is created);
    if (e1 is renamed && !(e2 is renamed)) return (e2 is removed);
    return false;
});

RenameResult rename(
        list[Tree] cursor
      , str newName
      , RenameConfig config) {

    void printDebug(str s) {
        if (config.debug) {
            println(s);
        }
    }

    // Tree & TModel caching

    @memo{maximumSize(50)}
    TModel getTModelCached(Tree t) {
        tm = config.tmodelForTree(t);
        if (msg <- tm.messages, msg is error) registerMessage(error(t.src.top, "Renaming failed, since this file has type error(s)."));
        return tm;
    }

    @memo{maximumSize(50)}
    Tree parseLocCached(loc l) {
        // We already have the parse tree of the module under cursor
        if (l == cursor[-1].src.top) {
            return cursor[-1];
        }

        try {
            return config.parseLoc(l);
        } catch ParseError(_): {
            registerMessage(error(l, "Renaming failed, since an error occurred while parsing this file."));
            return char(-1);
        }
    }

    // Make sure user uses cached functions
    cachedConfig = config[parseLoc = parseLocCached][tmodelForTree = getTModelCached];

    // Messages
    set[FailMessage] messages = {};
    bool errorReported() = messages != {} && any(m <- messages, m is fm_error);
    void registerMessage(FailMessage msg) { messages += msg; };
    AType getType(Tree t) {
        TModel tm = getTModelCached(parseLocCached(t.src.top));
        if (tm.facts[t.src]?) {
            return tm.facts[t.src];
        }

        return tvar(|unknown:///|);
    }
    set[Message] getMessages() = {toMessage(m, getType) | m <- messages};

    // Edits
    set[loc] editsSeen = {};
    list[DocumentEdit] docEdits = [];

    void checkEdit(replace(loc range, _)) {
        if (range in editsSeen) {
            registerMessage(error(range, "Multiple replace edits for this location."));
        }
        editsSeen += range;
    }

    void checkEdit(DocumentEdit e) {
        loc file = e has file ? e.file : e.from;
        if (changed(f, tes) := e) {
            // Check contents of DocumentEdit
            for (te:replace(range, _) <- tes) {
                // Check integrity
                if (range.top != f) {
                    registerMessage(error(range, "Invalid replace edit for this location. This location is not in <f>, for which it was registered."));
                }

                // Check text edits
                checkEdit(te);
            }
        } else if (file in editsSeen) {
            registerMessage(error(file, "Multiple <getName(e)> edits for this file."));
        }

        editsSeen += file;
    }

    void registerDocumentEdit(DocumentEdit e) {
        checkEdit(e);
        docEdits += e;
    };

    void registerTextEdit(TextEdit e) {
        checkEdit(e);

        loc f = e.range.top;
        if ([*pre, c:changed(f, _)] := docEdits) {
            // If possible, merge with latest document edit
            // TODO Just assign to docEdits[-1], once this issue has been solved:
            // https://github.com/usethesource/rascal/issues/2123
            docEdits = [*pre, c[edits = c.edits + e]];
        } else {
            // Else, create new document edit
            docEdits += changed(f, [e]);
        }
    };

    Renamer r = renamer(
        registerMessage
      , registerDocumentEdit
      , registerTextEdit
      , RenameConfig() { return cachedConfig; }
      , void(value at, str s) { registerMessage(info(at, s)); }
      , void(value at, str s) { registerMessage(warning(at, s)); }
      , void(value at, str s) { registerMessage(error(at, s)); }
    );

    jobStart(config.jobLabel, totalWork = 2 * WORKSPACE_WORK);

    jobStep(config.jobLabel, "Resolving definitions of <cursor[0].src>", work = WORKSPACE_WORK);
    defs = getCursorDefinitions(cursor, parseLocCached, getTModelCached, r);

    if (defs == {}) r.error(cursor[0].src, "No definitions found");
    if (errorReported()) {
        jobEnd(config.jobLabel, success=false);
        return <sortDocEdits(docEdits), getMessages()>;
    }

    jobStep(config.jobLabel, "Looking for files with occurrences of name under cursor", work = WORKSPACE_WORK);
    <maybeDefFiles, maybeUseFiles, newNameFiles> = findOccurrenceFiles(defs, cursor, newName, parseLocCached, r);

    jobTodo(config.jobLabel, work = (size(maybeDefFiles) + size(maybeUseFiles) + size(newNameFiles)) * FILE_WORK);

    set[Define] additionalDefs = {};
    solve (additionalDefs) {
        for (loc f <- maybeDefFiles) {
            jobStep(config.jobLabel, "Looking for additional definitions in <f>", work = 0);
            tr = parseLocCached(f);
            tm = getTModelCached(tr);
            additionalDefs += findAdditionalDefinitions(defs, tr, tm, r);
        }
        defs += additionalDefs;
    }
    jobStep(config.jobLabel, "Done looking for additional definitions", work = FILE_WORK * size(maybeDefFiles));

    for (loc f <- newNameFiles) {
        jobStep(config.jobLabel, "Validating occurrences of new name \'<newName>\' in <f>", work = FILE_WORK);
        tr = parseLocCached(f);
        validateNewNameOccurrences(defs, newName, tr, r);
    }
    if (errorReported()) {
        jobEnd(config.jobLabel, success = false);
        return <sortDocEdits(docEdits), getMessages()>;
    }

    defFiles = {d.defined.top | d <- defs};
    jobTodo(config.jobLabel, work = size(defFiles) * FILE_WORK);

    for (loc f <- defFiles) {
        fileDefs = {d | d <- defs, d.defined.top == f};
        jobStep(config.jobLabel, "Renaming <size(fileDefs)> definitions in <f>", work = FILE_WORK);
        tr = parseLocCached(f);
        tm = getTModelCached(tr);

        map[Define, loc] defNames = defNameLocations(tr, fileDefs, r);
        for (d <- fileDefs) {
            renameDefinition(d, defNames[d] ? d.defined, newName, tm, r);
        }
    }

    if (errorReported()) {
        jobEnd(config.jobLabel, success=false);
        return <sortDocEdits(docEdits), getMessages()>;
    }

    for (loc f <- maybeUseFiles) {
        jobStep(config.jobLabel, "Renaming uses in <f>", work = FILE_WORK);
        tr = parseLocCached(f);
        tm = getTModelCached(tr);

        renameUses(defs, newName, tm, r);
    }

    set[Message] convertedMessages = getMessages();

    if (config.debug) {
        println("\n\n=================\nRename statistics\n=================\n");
        int nDocs = size({f | de <- docEdits, f := (de has file ? de.file : de.from)});
        int nEdits = (0 | it + ((changed(_, tes) := e) ? size(tes) : 1) | e <- docEdits);

        int nErrors = size({msg | msg <- convertedMessages, msg is error});
        int nWarnings = size({msg | msg <- convertedMessages, msg is warning});
        int nInfos = size({msg | msg <- convertedMessages, msg is info});

        println(" # of documents affected: <nDocs>");
        println(" # of text edits:         <nEdits>");
        println(" # of messages:           <size(convertedMessages)>");
        println("   (<nErrors> errors, <nWarnings> warnings and <nInfos> infos)");

        if (size(convertedMessages) > 0) {
            println("\n===============\nMessages\n===============");
            for (msg <- convertedMessages) {
                println(" ** <msg>");
            }
            println();
        }
    }

    jobEnd(config.jobLabel, success = !errorReported());
    return <sortDocEdits(docEdits), convertedMessages>;
}

// Workaround to be able to pattern match on the emulated `src` field
data Tree (loc src = |unknown:///|(0,0,<0,0>,<0,0>));

// TODO If performance bottleneck, rewrite to binary search
private map[Define, loc] defNameLocations(Tree tr, set[Define] defs, Renamer r) {
    map[loc, Define] definitions = (d.defined: d | d <- defs);
    set[loc] defsToDo = defs.defined;

    // If we have a single definition, we can put the pattern matcher to work
    if ({loc d} := defsToDo) {
        def = definitions[d];
        top-down visit (tr) {
            case t:appl(_, _, src = d):
                return (def: nameLocation(t, def));
        }
    }

    map[Define, loc] defNames = ();
    for (defsToDo != {}, /t:appl(_, _, src = loc d) := tr, d in defsToDo) {
        def = definitions[d];
        defNames[def] = nameLocation(t, def);
        defsToDo -= d;
    }

    return defNames;
}

default set[Define] getCursorDefinitions(list[Tree] cursor, Tree(loc) _, TModel(Tree) getModel, Renamer r) {
    loc cursorLoc = cursor[0].src;
    TModel tm = getModel(cursor[-1]);
    for (Tree c <- cursor) {
        if (tm.definitions[c.src]?) {
            return {tm.definitions[c.src]};
        } else if (defs: {_, *_} := tm.useDef[c.src]) {
            if (any(d <- defs, d.top != cursorLoc.top)) {
                r.error(cursorLoc, "Rename not implemented for cross-file definitions. Please overload `getCursorDefinitions`.");
                return {};
            }

            return {tm.definitions[d] | d <- defs, tm.definitions[d]?};
        }
    }

    r.error(cursorLoc, "Could not find definition to rename.");
    return {};
}

default tuple[set[loc] defFiles, set[loc] useFiles, set[loc] newNameFiles] findOccurrenceFiles(set[Define] cursorDefs, list[Tree] cursor, str newName, Tree(loc) _, Renamer r) {
    loc f = cursor[0].src.top;
    if (any(d <- cursorDefs, f != d.defined.top)) {
        r.error(cursor[0].src, "Rename not implemented for cross-file definitions. Please overload `findOccurrenceFiles`.");
        return <{}, {}, {}>;
    }

    return <{f}, {f}, any(/Tree t := f, "<t>" == newName) ? {f} : {}>;
}

default set[Define] findAdditionalDefinitions(set[Define] cursorDefs, Tree tr, TModel tm, Renamer r) = {};

default void validateNewNameOccurrences(set[Define] cursorDefs, str newName, Tree tr, Renamer r) {
    for (Define d <- cursorDefs) {
        r.error(d.defined, "Renaming this to \'<newName>\' would clash with use of \'<newName>\' in <tr.src.top>.");
    }
}

default void renameDefinition(Define d, loc nameLoc, str newName, TModel tm, Renamer r) {
    r.textEdit(replace(nameLoc, newName));
}

default void renameUses(set[Define] defs, str newName, TModel tm, Renamer r) {
    for (loc u <- invert(tm.useDef)[defs.defined] - defs.defined) {
        r.textEdit(replace(u, newName));
    }
}

default loc nameLocation(Tree t, Define d) {
    // Try to find the first sub-tree that matches the name of the definition
    for (/Tree tr := t, tr@\loc?, "<tr>" == d.id) {
        return tr@\loc;
    }
    return t@\loc;
}
