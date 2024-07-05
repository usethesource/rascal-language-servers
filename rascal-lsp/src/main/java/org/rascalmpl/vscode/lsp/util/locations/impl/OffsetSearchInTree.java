package org.rascalmpl.vscode.lsp.util.locations.impl;

import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.values.parsetrees.TreeAdapter;

import io.usethesource.vallang.IList;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValue;

/**
 * Utility for finding UTF-24 offsets starting from line and column information and a given ITree.
 */
public class OffsetSearchInTree {
    
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
     * Locates the smallest parse tree at given line and column offset (UTF-24)
     * @param tree   parse tree
     * @param line   search term in lines (1-based)
     * @param column search term in columns (0-based, UTF-24 codepoints)
     * @return found the smallest tree that has the line and column offset inside of it.
     */
    public static ITree locateDeepestTreeAtLineColumn(ITree tree, int line, int column) {
		ISourceLocation l = TreeAdapter.getLocation(tree);

		if (l == null) {
            // inside a layout, literal or character
			return null;
		}

		if (TreeAdapter.isLexical(tree)) {
			if (inside(l, line, column)) {
                // stop and return success
				return tree;
			}   
            else {
                // stop and return failure
                return null;
            }
		}

		if (TreeAdapter.isAmb(tree)) {
            // pick any tree to make the best of it.
			return locateDeepestTreeAtLineColumn((ITree) tree.getAlternatives().iterator().next(), line, column);
		}

		if (TreeAdapter.isAppl(tree)) {
			IList children = TreeAdapter.getASTArgs(tree); // this skips layout trees

			for (IValue child : children) {
				ISourceLocation childLoc = TreeAdapter.getLocation((ITree) child);

				if (childLoc == null) {
					continue;
				}

				if (inside(childLoc, line, column)) {
					ITree result = locateDeepestTreeAtLineColumn((ITree) child, line, column);

					if (result != null) {
						return result;
					}
					break;
				}
			}

            // can't find a child that fits, then return the current node
			if (inside(l, line, column)) {
				return tree;
			}
		}

        // cycles and characters do not have locations
		return null;
	}

   }
