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

import IO;
import List;
import Map;
import Message;
import Node;
import ParseTree;
import Relation;
import Set;

alias RenameResult = tuple[list[DocumentEdit], set[Message]];

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
      , bool debug = true
    );

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
    TModel getTModelCached(Tree t) = config.tmodelForTree(t);

    @memo{maximumSize(50)}
    Tree parseLocCached(loc l) {
        // We already have the parse tree of the module under cursor
        if (l == cursor[-1].src.top) {
            return cursor[-1];
        }

        return config.parseLoc(l);
    }

    // Make sure user uses cached functions
    cachedConfig = config[parseLoc = parseLocCached][tmodelForTree = getTModelCached];

    // Messages
    set[FailMessage] messages = {};
    bool errorReported() = messages != {} && any(m <- messages, m is error);
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

    printDebug("Renaming <cursor[0].src> to \'<newName>\'");

    printDebug("+ Finding definitions for cursor at <cursor[0].src>");
    defs = getCursorDefinitions(cursor, parseLocCached, getTModelCached, r);

    if (defs == {}) r.error(cursor[0].src, "No definitions found");
    if (errorReported()) return <docEdits, getMessages()>;

    printDebug("+ Finding occurrences of cursor");
    <maybeDefFiles, maybeUseFiles> = findOccurrenceFiles(defs, cursor, parseLocCached, r);

    if (maybeDefFiles != {}) {
        printDebug("+ Finding additional definitions");
        set[Define] additionalDefs = {};
        for (loc f <- maybeDefFiles) {
            tr = parseLocCached(f);
            tm = getTModelCached(tr);
            fileAdditionalDefs = findAdditionalDefinitions(defs, tr, tm);
            printDebug("  - ... (<size(fileAdditionalDefs)>) in <f>");
            additionalDefs += fileAdditionalDefs;
        }
        defs += additionalDefs;
    }

    defFiles = {d.defined.top | d <- defs};

    printDebug("+ Renaming definitions across <size(defFiles)> files");
    for (loc f <- defFiles) {
        fileDefs = {d | d <- defs, d.defined.top == f};
        printDebug("  - ... <size(fileDefs)> in <f>");

        tr = parseLocCached(f);
        tm = getTModelCached(tr);

        for (d <- defs, d.defined.top == f) {
            renameDefinition(d, newName, tr, tm, r);
        }
    }

    printDebug("+ Renaming uses across <size(maybeUseFiles)> files");
    for (loc f <- maybeUseFiles) {
        printDebug("  - ... in <f>");

        tr = parseLocCached(f);
        tm = getTModelCached(tr);

        renameUses(defs, newName, tr, tm, r);
    }

    set[Message] convertedMessages = getMessages();

    printDebug("+ Done!");
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

    return <docEdits, convertedMessages>;
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

default tuple[set[loc] defFiles, set[loc] useFiles] findOccurrenceFiles(set[Define] cursorDefs, list[Tree] cursor, Tree(loc) _, Renamer r) {
    loc f = cursor[0].src.top;
    if (any(d <- cursorDefs, f != d.defined.top)) {
        r.error(cursor[0].src, "Rename not implemented for cross-file definitions. Please overload `findOccurrenceFiles`.");
        return <{}, {}>;
    }

    return <{f}, {f}>;
}

default set[Define] findAdditionalDefinitions(set[Define] cursorDefs, Tree tr, TModel tm) = {};

default void renameDefinition(Define d, str newName, Tree _, TModel tm, Renamer r) {
    r.textEdit(replace(d.defined, newName));
}

default void renameUses(set[Define] defs, str newName, Tree _, TModel tm, Renamer r) {
    for (loc u <- invert(tm.useDef)[defs.defined] - defs.defined) {
        r.textEdit(replace(u, newName));
    }
}
