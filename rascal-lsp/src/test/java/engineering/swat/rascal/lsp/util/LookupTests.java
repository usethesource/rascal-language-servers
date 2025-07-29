/*
 * Copyright (c) 2018-2025, NWO-I CWI and Swat.engineering
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package engineering.swat.rascal.lsp.util;

import static org.junit.Assert.assertSame;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.Test;
import org.rascalmpl.vscode.lsp.util.locations.impl.TreeMapLookup;

public class LookupTests {

    private TreeMapLookup<String> buildTreeLookup() {
        TreeMapLookup<String> target = new TreeMapLookup<>();
        target.put(new Range(new Position(1,5), new Position(1,8)), "hit");
        return target;
    }

    private TreeMapLookup<String> buildTreeLookup2() {
        TreeMapLookup<String> target = buildTreeLookup();
        target.put(new Range(new Position(1,9), new Position(1,12)), "hit2");
        return target;
    }

    private Range cursor(int line, int column) {
        return range(line, column, line, column);
    }

    private Range range(int startLine, int startColumn, int endLine, int endColumn) {
        return new Range(new Position(startLine, startColumn), new Position(endLine, endColumn));
    }

    @Test
    public void testSimpleLookup() {
        TreeMapLookup<String> target = new TreeMapLookup<>();
        Range use = new Range(new Position(1,5), new Position(1,7));
        target.put(use, "hit");
        assertSame("hit", target.lookup(use));
    }

    @Test
    public void testSimpleLookupInside() {
        TreeMapLookup<String> target = buildTreeLookup();
        assertSame("hit", target.lookup(new Range(new Position(1,6), new Position(1,7))));
    }

    @Test
    public void testSimpleLookupFront() {
        TreeMapLookup<String> target = buildTreeLookup();
        assertSame("hit", target.lookup(cursor(1,5)));
    }

    @Test
    public void testSimpleLookupEnd() {
        TreeMapLookup<String> target = buildTreeLookup();
        assertSame("hit", target.lookup(cursor(1,8)));
    }


    @Test
    public void testSimpleLookupInside1() {
        TreeMapLookup<String> target = buildTreeLookup2();
        assertSame("hit", target.lookup(range(1,6, 1, 7)));
        assertSame("hit", target.lookup(range(1,5, 1, 8)));
    }

    @Test
    public void testSimpleLookupFront1() {
        TreeMapLookup<String> target = buildTreeLookup2();
        assertSame("hit", target.lookup(cursor(1,5)));
    }

    @Test
    public void testSimpleLookupEnd1() {
        TreeMapLookup<String> target = buildTreeLookup2();
        assertSame("hit", target.lookup(cursor(1,8)));
    }


    @Test
    public void testSimpleLookupStart2() {
        TreeMapLookup<String> target = buildTreeLookup2();
        assertSame("hit2", target.lookup(cursor(1,9)));
    }

    @Test
    public void testSimpleLookupMiddle2() {
        TreeMapLookup<String> target = buildTreeLookup2();
        assertSame("hit2", target.lookup(cursor(1,10)));
    }



    @Test
    public void testSimpleLookupEnd2() {
        TreeMapLookup<String> target = buildTreeLookup2();
        assertSame("hit2", target.lookup(cursor(1,12)));
    }

    @Test
    public void testOverlappingRanges() {
        TreeMapLookup<String> target = new TreeMapLookup<>();
        target.put(new Range(new Position(0,1), new Position(4,5)), "big1");
        target.put(new Range(new Position(1,1), new Position(1,5)), "small1");
        target.put(new Range(new Position(4,1), new Position(4,4)), "small2");
        assertSame("big1", target.lookup(cursor(0,12)));
        assertSame("big1", target.lookup(cursor(1,12)));
        assertSame("small1", target.lookup(cursor(1,4)));
        assertSame("small2", target.lookup(cursor(4,4)));
        assertSame("big1", target.lookup(cursor(4,5)));
    }

    @Test
    public void testSingleLineTree() {
        TreeMapLookup<String> target = new TreeMapLookup<>();
        target.put(range(0, 1, 0, 10), "composite expression");
        target.put(range(0, 1, 0, 4), "first half");
        target.put(range(0, 1, 0, 2), "first quarter");
        target.put(range(0, 2, 0, 4), "second quarter");
        target.put(range(0, 6, 0, 10), "second half");
        target.put(range(0, 6, 0, 8), "third quarter");
        target.put(range(0, 8, 0, 10), "fourth quarter");
        assertSame("composite expression", target.lookup(cursor(0, 5)));
        assertSame("first quarter", target.lookup(cursor(0, 1)));
        assertSame("second quarter", target.lookup(cursor(0, 4)));
        assertSame("third quarter", target.lookup(cursor(0, 6)));
        assertSame("fourth quarter", target.lookup(cursor(0, 10)));
    }

    @Test
    public void testMultiLineTree() {
        TreeMapLookup<String> target = new TreeMapLookup<>();
        target.put(range(0, 1, 2, 10), "composite expression");
        target.put(range(0, 1, 1, 4), "first half");
        target.put(range(1, 6, 2, 10), "second half");
        assertSame("composite expression", target.lookup(cursor(1, 5)));
        assertSame("first half", target.lookup(cursor(0, 1)));
        assertSame("first half", target.lookup(cursor(1, 4)));
        assertSame("second half", target.lookup(cursor(1, 6)));
        assertSame("second half", target.lookup(cursor(2, 10)));
    }

    @Test
    public void randomRanges() {
        TreeMapLookup<String> target = new TreeMapLookup<>();
        Map<Range, String> ranges = new HashMap<>();
        Random r = new Random();
        int tries = 1000;
        for (int i = 0; i < tries; i++) {
            int startLine = r.nextInt(10);
            int startColumn = r.nextInt(10);
            int endLine = startLine + r.nextInt(3);
            int endColumn;
            if (endLine == startLine) {
                endColumn = startColumn + r.nextInt(3);
            }
            else {
                endColumn = r.nextInt(10);
            }
            ranges.put(range(startLine, startColumn, endLine, endColumn), "" + i);
        }
        ranges.forEach(target::put);
        int i = 0;
        for (var e : ranges.entrySet()) {
            var found = target.lookup(e.getKey());
            var foundKeys = ranges.entrySet().stream().filter(range -> range.getValue().equals(found)).collect(Collectors.toSet());
            assertSame("Entry " + e + " should be found, but found " + foundKeys +" (attempt " + i++ + "/" + tries + ")", e.getValue(), found);
        }
    }

}
