@license{
Copyright (c) 2018-2025, NWO-I CWI and Swat.engineering
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
@bootstrapParser
module lang::rascal::lsp::refactor::rename::Common

extend framework::Rename;
import framework::TextEdits;

import analysis::typepal::TModel;
import lang::rascal::\syntax::Rascal;
import lang::rascalcore::check::ATypeBase;
import lang::rascalcore::check::Import;
import lang::rascalcore::check::RascalConfig;
import lang::rascalcore::check::BasicRascalConfig;
import util::refactor::WorkspaceInfo;

import List;
import Location;
import Map;
import ParseTree;
import Relation;
import Set;
import String;
import util::FileSystem;
import util::Maybe;
import util::Reflective;
import util::Util;

data RenameConfig(
    set[loc] workspaceFolders = {}
  , PathConfig(loc) getPathConfig = PathConfig(loc l) { throw "No path config for <l>"; }
);

bool isContainedInScope(loc l, loc scope, TModel tm) {
    // lexical containment
    if (isContainedIn(l, scope)) return true;

    // via import/extend
    set[loc] reachableFrom = (tm.paths<pathRole, to, from>)[{importPath(), extendPath()}, scope];
    return any(loc fromScope <- reachableFrom, isContainedIn(l, fromScope));
}

private set[str] reservedNames = getRascalReservedIdentifiers();

str forceUnescapeNames(str name) = replaceAll(name, "\\", "");
str forceEscapeSingleName(str name) = startsWith(name, "\\") ? name : "\\<name>";
str escapeReservedNames(str name, str sep = "::") = intercalate(sep, [n in reservedNames ? forceEscapeSingleName(n) : n | n <- split(sep, name)]);
str unescapeNonReservedNames(str name, str sep = "::") = intercalate(sep, [n in reservedNames ? n : forceUnescapeNames(n) | n <- split(sep, name)]);
str reEscape(str name) = escapeReservedNames(forceUnescapeNames(name));

Tree parseAsOrEmpty(type[&T <: Tree] T, str name) =
    just(Tree t) := tryParseAs(T, name) ? t : char(0);

private tuple[Tree, Tree] escapePair(type[&T <: Tree] T, str n) = <parseAsOrEmpty(T, n), parseAsOrEmpty(T, forceEscapeSingleName(n))>;

bool(Tree) allNameSortsFilter(str name) {
    escName = reEscape(name);

    <n1, en1> = escapePair(#Name, escName);
    <nt1, ent1> = escapePair(#Nonterminal, escName);
    <ntl1, entl1> = escapePair(#NonterminalLabel, escName);
    qn1 = parseAsOrEmpty(#QualifiedName, escName);

    return bool(Tree tr) {
        visit (tr) {
            case n1: return true;
            case en1: return true;

            case nt1: return true;
            case ent1: return true;

            case ntl1: return true;
            case entl1: return true;

            case qn1: return true;

            case QualifiedName qn: {
                if (reEscape("<qn>") == escName) return true;
            }
        }

        return false;
    };
}

tuple[bool, bool](Tree) allNameSortsFilter(str name1, str name2) {
    sname1 = reEscape(name1);
    sname2 = reEscape(name2);

    <n1, en1> = escapePair(#Name, sname1);
    <nt1, ent1> = escapePair(#Nonterminal, sname1);
    <ntl1, entl1> = escapePair(#NonterminalLabel, sname1);
    qn1 = parseAsOrEmpty(#QualifiedName, sname1);

    <n2, en2> = escapePair(#Name, sname2);
    <nt2, ent2> = escapePair(#Nonterminal, sname2);
    <ntl2, entl2> = escapePair(#NonterminalLabel, sname2);
    qn2 = parseAsOrEmpty(#QualifiedName, sname2);

    return tuple[bool, bool](Tree tr) {
        bool has1 = false;
        bool has2 = false;
        visit (tr) {
            case n1: has1 = true;
            case en1: has1 = true;

            case nt1: has1 = true;
            case ent1: has1 = true;

            case ntl1: has1 = true;
            case entl1: has1 = true;

            case n2: has2 = true;
            case en2: has2 = true;

            case nt2: has2 = true;
            case ent2: has2 = true;

            case ntl2: has2 = true;
            case entl2: has2 = true;

            case qn1: has1 = true;
            case qn2: has2 = true;

            case QualifiedName qn: {
                escQn = reEscape("<qn>");
                if (escQn == sname1) has1 = true;
                if (escQn == sname2) has2 = true;
            }
        }

        return <has1, has2>;
    };
}

set[loc] filterFiles(set[loc] fs, bool(Tree) treeFilter, Tree(loc) getTree) = {f | loc f <- fs, treeFilter(getTree(f))};

tuple[set[loc], set[loc]] filterFiles(set[loc] fs, tuple[bool, bool](Tree) treeFilter, Tree(loc) getTree) {
    set[loc] fs1 = {};
    set[loc] fs2 = {};
    for (loc f <- fs) {
        tr = getTree(f);
        <l, r> = treeFilter(tr);
        if (l) fs1 += f;
        if (r) fs2 += f;
    }
    return <fs1, fs2>;
}

default tuple[set[loc], set[loc], set[loc]] findOccurrenceFilesUnchecked(set[Define] defs, list[Tree] cursor, str newName, Tree(loc) getTree, Renamer r) {
    if ({str id} := defs.id) {
        <curFiles, newFiles> = filterFiles(getSourceFiles(r), allNameSortsFilter("<cursor[0]>", newName), getTree);
        return <curFiles, curFiles, newFiles>;
    }

    r.error(cursor[0], "Cannot find occurrences for defs with multiple names.");
    return <{}, {}, {}>;
}

// Workaround to be able to pattern match on the emulated `src` field
data Tree (loc src = |unknown:///|(0,0,<0,0>,<0,0>));

set[loc] getSourceFiles(Renamer r) {
    c = r.getConfig();
    return {*find(srcFolder, "rsc")
        | wsFolder <- c.workspaceFolders
        , srcFolder <- c.getPathConfig(wsFolder).srcs
    };
}

Maybe[AType] getFact(TModel tm, loc l) = l in tm.facts ? just(tm.facts[l]) : nothing();

bool rascalMayOverloadSameName(set[loc] defs, map[loc, Define] definitions) {
    if (l <- defs, !definitions[l]?) return false;
    set[Define] defines = {definitions[d] | d <- defs};

    if (size(defines.id) > 1) return false;
    if (size(defines) == 0) return false;
    return rascalMayOverload(defs, definitions);
}

@memo{maximumSize(1), expireAfter(minutes=5)}
rel[loc from, loc to] rascalGetTransitiveReflexiveScopes(TModel tm) = toRel(tm.scopes)*;

@memo{maximumSize(100), expireAfter(minutes=5)}
rel[loc from, loc to] rascalGetReflexiveModulePaths(TModel tm) =
    ident(range(getModuleScopes(tm)))
  + (tm.paths<pathRole, from, to>)[importPath()]
  + (tm.paths<pathRole, from, to>)[extendPath()];

set[&T] flatMapPerFile(set[loc] locs, set[&T](loc, set[loc]) func) {
    rel[loc file, loc l] fs = {<l.top, l> | loc l <- locs};
    return {*func(f, fs[f]) | loc f <- fs.file};
}

set[&T] flatMapPerFile(set[&U] us, set[&T](loc, set[&U]) func, rel[&U, loc] locOf) =
    flatMapPerFile(locOf[us], set[&T](loc f, set[loc] ls) {
        return func(f, invert(locOf)[ls]);
    });

set[&T] flatMapPerFile(set[Define] defs, set[&T](loc, set[Define]) func) =
    flatMapPerFile(defs, func, {<d, d.defined> | d <- defs});

default void renameAdditionalUses(set[Define] defs, str newName, TModel tm, Renamer r) {}

default bool isUnsupportedCursor(list[Tree] cursor, Renamer _) = false;
