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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.rascalmpl.vscode.lsp.util.locations.impl.TreeSearch.computeFocusList;

import org.junit.BeforeClass;
import org.junit.Test;
import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.values.parsetrees.TreeAdapter;
import org.rascalmpl.vscode.lsp.util.RascalServices;

public class TreeSearchTests {
    private static final IRascalValueFactory VF = IRascalValueFactory.getInstance();
    private static final String URI = "unknown:///";
    private static final String CONTENTS = fromLines(
        "module TreeTest"           // 1
      , ""                          // 2
      , "int f() {"                 // 3
      , "  int x = 8;"              // 4
      , "  int y = 54;"             // 5
      , "  int z = -1;"             // 6
      , ""                          // 7
      , "  return x + y + z;"       // 8
      , "}"                         // 9
    );

    private static ITree tree;

    private static String fromLines(String... lines) {
        final var builder = new StringBuilder();
        for (var line : lines) {
            builder.append(line);
            builder.append("\n");
        }
        return builder.toString();
    }

    @BeforeClass
    public static void setUpSuite() {
        tree = RascalServices.parseRascalModule(VF.sourceLocation(URI), CONTENTS.toCharArray());
    }

    @Test
    public void focusStartsWithLexical() {
        final var focus = computeFocusList(tree, 8, 13);
        final var first = (ITree) focus.get(0);
        assertTrue(TreeAdapter.isLexical(first));
    }

    @Test
    public void focusEndsWithModule() {
        final var focus = computeFocusList(tree, 6, 4);
        final var last = (ITree) focus.get(focus.length() - 1);
        assertEquals(tree, last);
    }

    @Test
    public void listRangePartial() {
        final var focus = computeFocusList(tree, 5, 8, 6, 8);
        final var selection = (ITree) focus.get(0);
        final var originalList = (ITree) focus.get(1);

        assertValidListWithLength(selection, 2);
        assertValidListWithLength(originalList, 4);
    }

    @Test
    public void listRangeStartsWithWhitespace() {
        final var focus = computeFocusList(tree, 7, 0, 8, 15);
        final var selection = (ITree) focus.get(0);
        final var originalList = (ITree) focus.get(1);

        assertValidListWithLength(selection, 1);
        assertValidListWithLength(originalList, 4);
    }

    @Test
    public void listRangeEndsWithWhitespace() {
        final var focus = computeFocusList(tree, 4, 3, 7, 0);
        final var selection = (ITree) focus.get(0);
        final var originalList = (ITree) focus.get(1);

        assertValidListWithLength(selection, 3);
        assertValidListWithLength(originalList, 4);
    }

    private static void assertValidListWithLength(final ITree list, int length) {
        assertTrue(String.format("Not a list: %s", TreeAdapter.getType(list)), TreeAdapter.isList(list));
        assertEquals(TreeAdapter.yield(list), length, TreeAdapter.getListASTArgs(list).size());

        // assert no layout padding
        final var args = TreeAdapter.getArgs(list);
        assertFalse("List tree malformed: starts with layout", TreeAdapter.isLayout((ITree) args.get(0)));
        assertFalse("List tree malformed: ends with layout", TreeAdapter.isLayout((ITree) args.get(args.length() - 1)));
    }
}
