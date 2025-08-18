module util::Format

import List;
import Map;
import Set;
import String;

list[str] newLineCharacters = [
    "\u000A", // LF
    "\u000B", // VT
    "\u000C", // FF
    "\u000D", // CR
    "\u000D\u000A", // CRLF
    "\u0085", // NEL
    "\u2028", // LS
    "\u2029" // PS
];

@synopsis{Comparator to sort strings by length (ascending).}
private bool bySize(str a, str b) = size(a) < size(b);

@synopsis{Comparator to sort strings by relative position in a reference list.}
private bool(str, str) byIndex(list[str] indices) {
    return bool(str a, str b) {
        return indexOf(indices, a) < indexOf(indices, b);
    };
}

@synopsis{Determine the most-used newline character in a string.}
str mostUsedNewline(str input, list[str] lineseps = newLineCharacters, str(list[str]) tieBreaker = getFirstFrom) {
    linesepCounts = (nl: 0 | nl <- lineseps);
    for (nl <- sort(lineseps, bySize)) {
        int count = size(findAll(input, nl));
        linesepCounts[nl] = count;
        // subtract all occurrences of substrings of newline characters that we counted before
        for (str snl <- substrings(nl), linesepCounts[snl]?) {
            linesepCounts[snl] = linesepCounts[snl] - count;
        }
    }

    byCount = invert(linesepCounts);
    return tieBreaker(sort(byCount[max(domain(byCount))], byIndex(lineseps)));
}

@synopsis{Split a string to an indentation prefix and the remainder of the string.}
tuple[str indentation, str rest] splitIndentation(/^<indentation:\s*><rest:.*>/)
    = <indentation, rest>;

str(str) indentSpacesAsTabs(int tabSize) {
    str spaces = ("" | it + " " | _ <- [0..tabSize]);
    return str(str line) {
        parts = splitIndentation(line);
        return "<replaceAll(parts.indentation, spaces, "\t")><parts.rest>";
    };
}

str(str) indentTabsAsSpaces(int tabSize) {
    str spaces = ("" | it + " " | _ <- [0..tabSize]);
    return str(str line) {
        parts = splitIndentation(line);
        return "<replaceAll(parts.indentation, "\t", spaces)><parts.rest>";
    };
}

@synopsis{Compute all possible strict substrings of a string.}
set[str] substrings(str input)
    = {input[i..i+l] | int i <- [0..size(input)], int l <- [1..size(input)], i + l <= size(input)};

test bool mostUsedNewlineTestMixed()
    = mostUsedNewline("\r\n\n\r\n\t\t\t\t") == "\r\n";

test bool mostUsedNewlineTestTie()
    = mostUsedNewline("\n\n\r\n\r\n") == "\n";

test bool mostUsedNewlineTestGreedy()
    = mostUsedNewline("\r\n\r\n\n") == "\r\n";

@synopsis{If a string does not end with a newline character, append one. }
str insertFinalNewline(str input, list[str] lineseps = newLineCharacters)
    = any(nl <- lineseps, endsWith(input, nl))
    ? input
    : input + mostUsedNewline(input, lineseps=lineseps)
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

@synopsis{Remove all newlines from the end of a string.}
str trimFinalNewlines(str input, list[str] lineseps = newLineCharacters) {
    orderedSeps = reverse(sort(lineseps, bySize));
    while (nl <- orderedSeps, endsWith(input, nl)) {
        input = input[0..-size(nl)];
    }
    return input;
}

test bool trimFinalNewlineTestSimple()
    = trimFinalNewlines("a\n\n\n") == "a";

test bool trimFinalNewlineTestEndOnly()
    = trimFinalNewlines("a\n\n\nb\n\n") == "a\n\n\nb";

test bool trimFinalNewlineTestWhiteSpace()
    = trimFinalNewlines("a\n\n\nb\n\n ") == "a\n\n\nb\n\n ";

@synopsis{Split a string in <text, newline> pairs for each line.}
list[tuple[str, str]] separateLines(str input, list[str] lineseps = newLineCharacters) {
    orderedSeps = reverse(sort(lineseps, bySize));

    list[tuple[str, str]] lines = [];
    int next = 0;
    for (int i <- [0..size(input)]) {
        // greedily match line separators (longest first)
        if (i >= next, str nl <- orderedSeps, nl == input[i..i+size(nl)]) {
            lines += <input[next..i], nl>;
            next = i + size(nl); // skip to the start of the next line
        }
    }

    // last line
    if (str nl <- orderedSeps, nl == input[-size(nl)..]) {
        lines += <input[next..next+size(nl)], "">;
    }

    return lines;
}

@synopsis{Concatenate a list of <line, newline> pairs to form a single string.}
str mergeLines(list[tuple[str, str]] lines)
    = ("" | it + line + sep | <line, sep> <- lines);

@synopsis{Process the text of a string per line, maintaining the original newline characters.}
str perLine(str input, str(str) lineFunc, list[str] lineseps = newLineCharacters)
    = mergeLines([<lineFunc(l), nl> | <l, nl> <- separateLines(input, lineseps=lineseps)]);

test bool perLineTest()
    = perLine("a\nb\r\nc\n\r\n", str(str line) { return line + "x"; }) == "ax\nbx\r\ncx\nx\r\nx";

@synopsis{Trim trailing non-newline whitespace from each line in a multi-line string.}
str trimTrailingWhitespace(str input) {
    str trimLineTrailingWs(/^<nonWhiteSpace:.*\S>\s*$/) = nonWhiteSpace;
    default str trimLineTrailingWs(/^\s*$/) = "";

    return perLine(input, trimLineTrailingWs);
}

test bool trimTrailingWhitespaceTest()
    = trimTrailingWhitespace("a  \nb\t\n  c  \n") == "a\nb\n  c\n";
