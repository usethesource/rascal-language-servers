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
module util::Util

import List;
import Location;
import Message;
import ParseTree;
import String;

import util::Maybe;
import util::Reflective;

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
    Try to parse string `name` as reified type `begin` and return whether this succeeded.
}
Maybe[&T] tryParseAs(type[&T <: Tree] begin, str name, bool allowAmbiguity = false) {
    try {
        return just(parse(begin, name, allowAmbiguity = allowAmbiguity));
    } catch ParseError(_): {
        return nothing();
    }
}

set[&T] flatMap(set[&U] us, set[&T](&U) f) =
    {*ts | u <- us, ts := f(u)};

str describeTree(appl(p, _)) = printSymbol(p.def, false);
str describeTree(cycle(sym, length)) = "cycle[<length>] of <printSymbol(sym, false)>";
str describeTree(amb(alts)) = intercalate("|", [describeTree(alt) | alt <- alts]);
str describeTree(char(int c)) = "char <c>";

list[str] describeTrees(list[Tree] trees) = [describeTree(t) | t <- trees];

set[&T] toSet(tuple[&T, &T] ts) = {ts<0>, ts<1>};
