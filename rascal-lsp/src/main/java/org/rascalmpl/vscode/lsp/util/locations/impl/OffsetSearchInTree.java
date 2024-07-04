package org.rascalmpl.vscode.lsp.util.locations.impl;

import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.values.parsetrees.TreeAdapter;

import io.usethesource.vallang.ISourceLocation;

/**
 * Utility for finding UTF-24 offsets starting from line and column information and a given ITree.
 */
public class OffsetSearchInTree {
    
    /**
     * This uses a .src annotated parse tree to find the closest offset
     * one a given line to a given column. This is useful when the LSP client
     * proposes only line/column information where the Rascal back-end requires
     * also offset/length. Since there is almost always an ITree available,
     * we can almost always derive this in log(length of file) steps by surfing
     * through the ITree's parent/child relations.
     * 
     * @param tree   parse tree
     * @param line   search term in lines (1-based)
     * @param column search term in columns (0-based, UTF-24 codepoints)
     * @return found file offset in UTF-24 codepoints
     */
    public static int offsetFromLineColumn(ITree tree, int line, int column) {
        if (tree.isAppl()) {
            ISourceLocation loc = TreeAdapter.getLocation(tree);

            if (loc == null || !loc.hasOffsetLength() || !loc.hasLineColumn()) {
                return -1; // back to parent
            }
            else {
                // our best guess for now
                int offset = loc.getOffset();
                
                // let's find a child that's even better, from top-to-bottom, left-to-right
                for (var arg : tree.getArgs()) {
                    ITree child = (ITree) arg;
                    ISourceLocation childLoc = TreeAdapter.getLocation(child);

                    if (line > childLoc.getEndLine()) {
                        // this child is to early in the file
                        continue;
                    }

                    if (column > childLoc.getEndColumn()) {
                        // this child is still to early on the line
                        continue;
                    }

                    // recurse to dig into this opportunity
                    int childOffset = offsetFromLineColumn(child, line, column);

                    if (childOffset > 0) {
                        // our current best guess can be updated
                        offset = childOffset;
                        // the child was in range so the next isn't
                        break;
                    }
                }
                
                return offset;
            }
        }
        // the rest (almost) never happens, but the code is here for robustness' sake
        // by returning -1 the parent gets to backtrack and deliver a closer approximate.
        else if (tree.isAmb()) {
            // we chose an arbitrary tree; maybe we get lucky. trees are not supposed to be ambiguous anymore at this stage.
            // but this makes it not crash just in case.
            return offsetFromLineColumn((ITree) tree.getAlternatives().iterator().next(), line, column);
        }
        else if (tree.isChar()) {
            return -1;
        }
        else if (tree.isCycle()) {
            return -1;
        }
        else {
            throw new IllegalArgumentException(tree.getClass().toString());
        }     
    }
}
