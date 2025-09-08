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
package org.rascalmpl.vscode.lsp.util.locations.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.values.parsetrees.TreeAdapter;

import io.usethesource.vallang.IList;
import io.usethesource.vallang.IListWriter;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValue;

/**
 * Utilities for finding sub-trees based on UTF-32 line/column indexing.
 */
public class TreeSearch {

    private static final Logger logger = LogManager.getLogger(TreeSearch.class);
    private static final IRascalValueFactory VF = IRascalValueFactory.getInstance();

    private TreeSearch() {}

    /**
     * Helper to test if a line/column position is within the scope of a source location's
     * begin line/column and end line/column scope. The first and the last lines are
     * where the column information is used.
     *
     * @param loc     location with line/column information that acts as a range
     * @param line    line to check if its within the range
     * @param column  column to see if we are within the range
     * @return true iff the given `loc` range spans around the line/column position, inclusively.
     */
    private static boolean inside(ISourceLocation loc, int line, int column) {
        if (!loc.hasLineColumn()) {
            return false;
        }

        if (line < loc.getBeginLine() || line > loc.getEndLine()) {
            return false;
        }

        if (line == loc.getBeginLine()) {
            if (line < loc.getEndLine()) {
                // then make sure we are not before the start
                return loc.getBeginColumn() <= column;
            }
            else {
                assert line == loc.getEndLine();

                // then we need to be right in between
                return loc.getBeginColumn() <= column
                    && column <= loc.getEndColumn();
            }
        }
        else if (line == loc.getEndLine()) {
            // then make sure we are not beyond the end
            return column <= loc.getEndColumn();
        }

        // otherwise we are in the middle of the lines
        // and the column is irrelevant

        return true;
    }

    private static boolean rightOf(ISourceLocation loc, int line, int column) {
        if (!loc.hasLineColumn()) {
            return false;
        }

        if (line > loc.getEndLine()) {
            return true;
        }
        return line == loc.getEndLine() && column > loc.getEndColumn();
    }

    /**
     * Produces a list of trees that are "in focus" at given line and column offset (UTF-32).
     *
     * This log(filesize) algorithm quickly collects the trees along a spine from the
     * root to the largest lexical or, if that does not exist, the smallest context-free node.
     * The list is returned in reverse order such that you can select the "most specific" tree by
     * starting at the start of the list.
     *
     * @param tree   parse tree
     * @param line   search term in lines (1-based)
     * @param column search term in columns (0-based, UTF-32 codepoints)
     * @return list of tree that are around the given line/column position, ordered from child to parent.
     */
    public static IList computeFocusList(ITree tree, int line, int column) {
        var lw = VF.listWriter();
        computeFocusList(lw, tree, line, column);
        return lw.done();
    }

    private static boolean computeFocusList(IListWriter focus, ITree tree, int line, int column) {
        ISourceLocation l = TreeAdapter.getLocation(tree);

        if (l == null) {
            // inside a layout, literal or character
            return false;
        }

        if (TreeAdapter.isLexical(tree)) {
            if (inside(l, line, column)) {
                focus.append(tree);
                // stop and return success
                return true;
            }
            else {
                // stop and return failure
                return false;
            }
        }

        if (TreeAdapter.isAmb(tree) && !tree.getAlternatives().isEmpty()) {
            // pick any tree to make the best of it.
            return computeFocusList(focus, (ITree) tree.getAlternatives().iterator().next(), line, column);
        }

        if (TreeAdapter.isAppl(tree)) {
            IList children = TreeAdapter.getASTArgs(tree); // this skips layout trees

            for (IValue child : children) {
                ISourceLocation childLoc = TreeAdapter.getLocation((ITree) child);

                if (childLoc == null) {
                    continue;
                }

                if (inside(childLoc, line, column)) {
                    boolean result = computeFocusList(focus, (ITree) child, line, column);

                    if (result) {
                        break;
                    }
                }
            }

            if (inside(l, line, column)) {
                focus.append(tree);
                return true;
            }
        }

        // cycles and characters do not have locations
        return false;
    }

    public static IList computeFocusList(ITree tree, int startLine, int startColumn, int endLine, int endColumn) {
        // Compute the focus for both the start end end positions.
        // These foci give us information about the structure of the selection.
        final var startList = computeFocusList(tree, startLine, startColumn);
        final var endList = computeFocusList(tree, endLine, endColumn);

        final var commonSuffix = startList.intersect(endList);
        if (commonSuffix.equals(startList) || commonSuffix.equals(endList)) {
            // We do not have enough information to extend the focus
            return commonSuffix;
        }
        // The range spans multiple subtrees. The easy way out is not to focus farther down than
        // their smallest common subtree (i.e. `commonSuffix`) - let's see if we can do any better.
        if (TreeAdapter.isList((ITree) commonSuffix.get(0))) {
            return computeListRangeFocus(commonSuffix, startLine, startColumn, endLine, endColumn);
        }

        return commonSuffix;
    }

    private static IList computeListRangeFocus(final IList commonSuffix, int startLine, int startColumn, int endLine, int endColumn) {
        final var parent = (ITree) commonSuffix.get(0);
        logger.trace("Computing focus list for {} at range [{}:{}, {}:{}]", TreeAdapter.getType(parent), startLine, startColumn, endLine, endColumn);
        final var elements = TreeAdapter.getListASTArgs(parent);
        final int nElements = elements.length();

        logger.trace("Smallest common tree is a {} with {} elements", TreeAdapter.getType(parent), nElements);
        if (inside(TreeAdapter.getLocation((ITree) elements.get(0)), startLine, startColumn) &&
            inside(TreeAdapter.getLocation((ITree) elements.get(nElements - 1)), endLine, endColumn)) {
            // The whole list is selected
            return commonSuffix;
        }

        // Find the elements in the list that are (partially) selected.
        final var selected = elements.stream()
            .map(ITree.class::cast)
            .dropWhile(t -> !inside(TreeAdapter.getLocation(t), startLine, startColumn))
            .takeWhile(t -> rightOf(TreeAdapter.getLocation(t), endLine, endColumn))
            .collect(VF.listWriter());
        final int nSelected = selected.length();

        logger.trace("Range covers {} (of {}) elements in the parent list", nSelected, nElements);
        final var firstSelected = TreeAdapter.getLocation((ITree) selected.get(0));
        final var lastSelected = TreeAdapter.getLocation((ITree) selected.get(nSelected - 1));

        final int totalLength = lastSelected.getOffset() - firstSelected.getOffset() + lastSelected.getLength();
        final var selectionLoc = VF.sourceLocation(firstSelected, firstSelected.getOffset(), totalLength,
            firstSelected.getBeginLine(), lastSelected.getEndLine(), firstSelected.getBeginColumn(), lastSelected.getEndColumn());
        final var artificialParent = TreeAdapter.setLocation(VF.appl(TreeAdapter.getProduction(parent), selected), selectionLoc);

        // Build new focus list
        var lw = VF.listWriter();
        lw.append(artificialParent);
        lw.appendAll(commonSuffix);
        return lw.done();
    }
}
