package org.rascalmpl.vscode.lsp.util.locations.impl;

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
            // then make sure we are not before the start
            return loc.getBeginColumn() <= column;
        }

        if (line == loc.getEndLine()) {
            // then make sure we are not beyond the end
            return column <= loc.getEndColumn();
        }

        // otherwise we are in the middle of the lines 
        // and the column is irrelevant

        return true;
    }
    
    /**
     * Produces a list of trees that are "in focus" at given line and column offset (UTF-24).
     * 
     * This log(filesize) algorithm quickly collects the trees along a spine from the
     * root to the smallest lexical or context-free node. The list is returned in
     * reverse order such that you can select the "most specific" tree by starting
     * at the start of the list.
     * 
     * @param tree   parse tree
     * @param line   search term in lines (1-based)
     * @param column search term in columns (0-based, UTF-32 codepoints)
     * @return list of tree that are around the given line/column position, ordered from child to parent.
     */
    public static IList computeFocusList(ITree tree, int line, int column) {
        var lw = IRascalValueFactory.getInstance().listWriter();
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
}
