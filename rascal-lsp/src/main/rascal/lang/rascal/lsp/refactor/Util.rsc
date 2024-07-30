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
import String;

import util::Maybe;
import util::Reflective;

import lang::rascal::\syntax::Rascal;

Maybe[loc] findSmallestContaining(set[loc] wrappers, loc l) =
    (nothing() | (it == nothing() || (just(itt) := it && w < itt)) && isContainedIn(l, w) ? just(w) : it | w <- wrappers);

loc min(list[loc] locs) =
    (getFirstFrom(locs) | l < l ? l : it | l <- locs);

loc trim(loc l, int removePrefix = 0, int removeSuffix = 0) {
    assert l.begin.line == l.end.line :
        "Cannot trim a multi-line location";
    return l[offset = l.offset + removePrefix]
            [length = l.length - removePrefix - removeSuffix]
            [begin = <l.begin.line, l.begin.column + removePrefix>]
            [end = <l.end.line, l.end.column - removeSuffix>];
}

bool isPrefixOf(loc prefix, loc l) = l.scheme == prefix.scheme
                                  && l.authority == prefix.authority
                                  && startsWith(l.path, endsWith(prefix.path, "/") ? prefix.path : prefix.path + "/");

start[Module] parseModuleWithSpacesCached(loc l) {
    @memo{expireAfter(minutes=5)} start[Module] parseModuleWithSpacesCached(loc l, datetime _) = parseModuleWithSpaces(l);
    return parseModuleWithSpacesCached(l, lastModified(l));
}

Maybe[&B] flatMap(nothing(), Maybe[&B](&A) _) = nothing();
Maybe[&B] flatMap(just(&A a), Maybe[&B](&A) f) = f(a);

str toString(error(msg, l)) = "[error] \'<msg>\' at <l>";
str toString(error(msg)) = "[error] \'<msg>\'";
str toString(warning(msg, l)) = "[warning] \'<msg>\' at <l>";
str toString(info(msg, l)) = "[info] \'<msg>\' at <l>";

str toString(list[Message] msgs, int indent = 1) =
    intercalate("\n", ([] | it + "<for (_ <- [0..indent]) {> <}>- <toString(msg)>" | msg <- msgs));

str toString(map[str, list[Message]] moduleMsgs) =
    intercalate("\n", ([] | it + "Messages for <m>:\n<toString(moduleMsgs[m])>" | m <- moduleMsgs));

rel[&K, &V] groupBy(set[&V] s, &K(&V) pred) =
    {<pred(v), v> | v <- s};
