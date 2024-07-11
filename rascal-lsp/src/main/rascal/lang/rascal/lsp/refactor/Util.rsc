module lang::rascal::lsp::refactor::Util

import List;
import Location;

import util::Maybe;

Maybe[loc] findSmallestContaining(set[loc] wrappers, loc l) =
    (nothing() | (it == nothing() || (just(itt) := it && w < itt)) && isContainedIn(l, w) ? just(w) : it | w <- wrappers);

loc min(list[loc] locs) =
    (getFirstFrom(locs) | l < l ? l : it | l <- locs);

loc trim(loc l, int removePrefix = 0, int removeSuffix = 0) {
    return |<l.scheme>://<l.authority><l.path><l.query><l.fragment>|(
        l.offset + removePrefix - removeSuffix,
        l.length - removePrefix - removeSuffix,
        <l.begin.line, l.begin.column + removePrefix>,
        <l.end.line, l.end.column - removeSuffix>
    );
}
