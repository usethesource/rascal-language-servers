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
module lang::rascal::lsp::refactor::Util

import IO;
import List;
import Location;
import Message;
import ParseTree;
import String;

import util::Maybe;
import util::Reflective;

import lang::rascal::\syntax::Rascal;

import lang::rascal::lsp::refactor::TextEdits;

alias Edits = tuple[list[DocumentEdit], map[ChangeAnnotationId, ChangeAnnotation]];

@synopsis{
    Finds the smallest location in `wrappers` than contains `l`. If none contains `l`, returns `nothing().`
    Accepts a predicate deciding containment as an optional argument.
}
Maybe[loc] findSmallestContaining(set[loc] wrappers, loc l, bool(loc, loc) containmentPred = isContainedIn) {
    Maybe[loc] result = nothing();
    for (w <- wrappers, containmentPred(l, w)) {
        switch (result) {
            case just(loc current): if (w < current) result = just(w);
            case nothing(): result = just(w);
        }
    }
    return result;
}

@synopsis{
    Resizes a location by removing a prefix and/or suffix.
}
loc trim(loc l, int removePrefix = 0, int removeSuffix = 0) {
    assert l.begin.line == l.end.line :
        "Cannot trim a multi-line location";
    return l[offset = l.offset + removePrefix]
            [length = l.length - removePrefix - removeSuffix]
            [begin = <l.begin.line, l.begin.column + removePrefix>]
            [end = <l.end.line, l.end.column - removeSuffix>];
}

@synopsis{
    Decides if `prefix` is a prefix of `l`.
}
bool isPrefixOf(loc prefix, loc l) = l.scheme == prefix.scheme
                                  && l.authority == prefix.authority
                                  && startsWith(l.path, endsWith(prefix.path, "/") ? prefix.path : prefix.path + "/");

@synopsis{
    A cached wrapper for the Rascal whole-module parse function.
}
start[Module] parseModuleWithSpacesCached(loc l) {
    @memo{expireAfter(minutes=5)} start[Module] parseModuleWithSpacesCached(loc l, datetime _) = parseModuleWithSpaces(l);
    return parseModuleWithSpacesCached(l, lastModified(l));
}

@synopsis{
    Try to parse string `name` as reified type `begin` and return whether this succeeded.
}
bool tryParseAs(type[&T <: Tree] begin, str name, bool allowAmbiguity = false) {
    try {
        parse(begin, name, allowAmbiguity = allowAmbiguity);
        return true;
    } catch ParseError(_): {
        return false;
    }
}

str toString(error(msg, l)) = "[error] \'<msg>\' at <l>";
str toString(error(msg)) = "[error] \'<msg>\'";
str toString(warning(msg, l)) = "[warning] \'<msg>\' at <l>";
str toString(info(msg, l)) = "[info] \'<msg>\' at <l>";

str toString(set[Message] msgs, int indent = 1) =
    intercalate("\n", ([] | it + "<for (_ <- [0..indent]) {> <}>- <toString(msg)>" | msg <- msgs));

str toString(map[str, set[Message]] moduleMsgs) =
    intercalate("\n", ([] | it + "Messages for <m>:\n<toString(moduleMsgs[m])>" | m <- moduleMsgs));

rel[&K, &V] groupBy(set[&V] s, &K(&V) pred) =
    {<pred(v), v> | v <- s};

@synopsis{
    Predicate to sort locations by length.
}
bool isShorter(loc l1, loc l2) = l1.length < l2.length;

bool isShorterTuple(tuple[loc, &T] t1, tuple[loc, &T] t2) = isShorter(t1[0], t2[0]);

@synopsis{
    Predicate to sort locations by offset.
}
bool byOffset(loc l1, loc l2) = l1.offset < l2.offset;

@synopsis{
    Predicate to reverse a sort order.
}
bool(&T, &T) desc(bool(&T, &T) f) {
    return bool(&T t1, &T t2) {
        return f(t2, t1);
    };
}

set[&T] flatMap(set[&S] ss, set[&T](&S) f) = ({} | it + f(s) | s <- ss);
list[&T] flatMap(list[&S] ss, list[&T](&S) f) = ([] | it + f(s) | s <- ss);
