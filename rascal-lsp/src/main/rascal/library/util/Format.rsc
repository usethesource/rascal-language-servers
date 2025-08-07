module util::Format

import List;
import Map;
import Set;
import String;

set[str] newLineCharacters = {
    "\u000A", // LF
    "\u000B", // VT
    "\u000C", // FF
    "\u000D", // CR
    "\u000D\u000A", // CRLF
    "\u0085", // NEL
    "\u2028", // LS
    "\u2029" // PS
};

private bool bySize(str a, str b) = size(a) > size(b);

str mostUsedNewline(str input, set[str] lineseps = newLineCharacters, str(set[str]) tieBreaker = getFirstFrom) {
    linesepCounts = (nl: 0 | nl <- lineseps);
    for (nl <- reverse(sort(lineseps, bySize))) {
        int count = size(findAll(input, nl));
        linesepCounts[nl] = count;
        // subtract all occurrences of substrings that we counted before
        for (str snl <- substrings(nl), linesepCounts[snl]?) {
            linesepCounts[snl] = linesepCounts[snl] - count;
        }

    }
    byCount = invert(linesepCounts);
    return tieBreaker(byCount[max(domain(byCount))]);
}

set[str] substrings(str input)
    = {input[i..i+l] | int i <- [0..size(input)], int l <- [1..size(input)], i + l <= size(input)};

test bool mostUsedNewlineTestMixed()
    = mostUsedNewline("\r\n\n\r\n\t\t\t\t") == "\r\n";

test bool mostUsedNewlineTestTie()
    = mostUsedNewline("\n\n\r\n\r\n") == "\n";

test bool mostUsedNewlineTestGreedy()
    = mostUsedNewline("\r\n\r\n\n") == "\r\n";

str defaultInsertFinalNewline(str input, set[str] lineseps = newLineCharacters)
    = any(nl <- lineseps, endsWith(input, nl))
    ? input
    : input + mostUsedNewline(input)
    ;

test bool defaultInsertFinalNewlineTestSimple()
    = defaultInsertFinalNewline("a\nb")
    == "a\nb\n";

test bool defaultInsertFinalNewlineTestNoop()
    = defaultInsertFinalNewline("a\nb\n")
    == "a\nb\n";

test bool defaultInsertFinalNewlineTestMixed()
    = defaultInsertFinalNewline("a\nb\r\n")
    == "a\nb\r\n";

str defaultTrimFinalNewlines(str input, set[str] lineseps = newLineCharacters) {
    orderedSeps = sort(lineseps, bySize);
    while (nl <- orderedSeps, endsWith(input, nl)) {
        input = input[0..-size(nl)];
    }
    return input;
}

test bool defaultTrimFinalNewlinesTestSimple()
    = defaultTrimFinalNewlines("a\n\n\n") == "a";

test bool defaultTrimFinalNewlinesTestEndOnly()
    = defaultTrimFinalNewlines("a\n\n\nb\n\n") == "a\n\n\nb";

test bool defaultTrimFinalNewlinesTestWhiteSpace()
    = defaultTrimFinalNewlines("a\n\n\nb\n\n ") == "a\n\n\nb\n\n ";

str perLine(str input, str(str) lineFunc, set[str] lineseps = newLineCharacters) {
    orderedSeps = sort(lineseps, bySize);

    str result = "";
    int next = 0;
    for (int i <- [0..size(input)]) {
        // greedily match line separators (longest first)
        if (i >= next, str nl <- orderedSeps, nl == input[i..i+size(nl)]) {
            line = input[next..i];
            result += lineFunc(line) + nl;
            next = i + size(nl); // skip to the start of the next line
        }
    }

    // last line
    if (str nl <- orderedSeps, nl == input[-size(nl)..]) {
        line = input[next..next+size(nl)];
        result += lineFunc(line);
    }

    return result;
}

test bool perLineTest()
    = perLine("a\nb\r\nc\n\r\n", str(str line) { return line + "x"; }) == "ax\nbx\r\ncx\nx\r\nx";

str defaultTrimTrailingWhitespace(str input) {
    str trimLineTrailingWs(/^<nonWhiteSpace:.*\S>\s*$/) = nonWhiteSpace;
    default str trimLineTrailingWs(/^\s*$/) = "";

    return perLine(input, trimLineTrailingWs);
}

test bool defaultTrimTrailingWhitespaceTest()
    = defaultTrimTrailingWhitespace("a  \nb\t\n  c  \n") == "a\nb\n  c\n";
