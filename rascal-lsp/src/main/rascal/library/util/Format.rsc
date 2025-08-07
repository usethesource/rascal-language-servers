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

tuple[str indentation, str rest] splitIndentation(/^<indentation:\s*><rest:.*>/)
    = <indentation, rest>;

str(str) indentSpacesAsTabs(int tabSize) {
    str spaces = ("" | it + " " | _ <- [0..tabSize]);
    return str(str s) {
        parts = splitIndentation(s);
        return "<replaceAll(parts.indentation, spaces, "\t")><parts.rest>";
    };
}

str(str) indentTabsAsSpaces(int tabSize) {
    str spaces = ("" | it + " " | _ <- [0..tabSize]);
    return str(str s) {
        parts = splitIndentation(s);
        return "<replaceAll(parts.indentation, "\t", spaces)><parts.rest>";
    };
}

set[str] substrings(str input)
    = {input[i..i+l] | int i <- [0..size(input)], int l <- [1..size(input)], i + l <= size(input)};

test bool mostUsedNewlineTestMixed()
    = mostUsedNewline("\r\n\n\r\n\t\t\t\t") == "\r\n";

test bool mostUsedNewlineTestTie()
    = mostUsedNewline("\n\n\r\n\r\n") == "\n";

test bool mostUsedNewlineTestGreedy()
    = mostUsedNewline("\r\n\r\n\n") == "\r\n";

str insertFinalNewline(str input, set[str] lineseps = newLineCharacters)
    = any(nl <- lineseps, endsWith(input, nl))
    ? input
    : input + mostUsedNewline(input)
    ;

test bool insertFinalNewlineTestSimple()
    = insertFinalNewline("a\nb")
    == "a\nb\n";

test bool insertFinalNewlineTestNoop()
    = insertFinalNewline("a\nb\n")
    == "a\nb\n";

test bool insertFinalNewlineTestMixed()
    = insertFinalNewline("a\nb\r\n")
    == "a\nb\r\n";

str trimFinalNewline(str input, set[str] lineseps = newLineCharacters) {
    orderedSeps = sort(lineseps, bySize);
    while (nl <- orderedSeps, endsWith(input, nl)) {
        input = input[0..-size(nl)];
    }
    return input;
}

test bool trimFinalNewlineTestSimple()
    = trimFinalNewline("a\n\n\n") == "a";

test bool trimFinalNewlineTestEndOnly()
    = trimFinalNewline("a\n\n\nb\n\n") == "a\n\n\nb";

test bool trimFinalNewlineTestWhiteSpace()
    = trimFinalNewline("a\n\n\nb\n\n ") == "a\n\n\nb\n\n ";

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

str trimTrailingWhitespace(str input) {
    str trimLineTrailingWs(/^<nonWhiteSpace:.*\S>\s*$/) = nonWhiteSpace;
    default str trimLineTrailingWs(/^\s*$/) = "";

    return perLine(input, trimLineTrailingWs);
}

test bool trimTrailingWhitespaceTest()
    = trimTrailingWhitespace("a  \nb\t\n  c  \n") == "a\nb\n  c\n";
