package org.rascalmpl.vscode.lsp.util;

import java.util.List;
import java.util.stream.Collectors;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SelectionRange;
import org.rascalmpl.vscode.lsp.util.locations.ColumnMaps;
import org.rascalmpl.vscode.lsp.util.locations.Locations;

import io.usethesource.vallang.IList;
import io.usethesource.vallang.ISourceLocation;

public class SelectionRanges {
    private SelectionRanges() { /* hide implicit constructor */ }

    public static @Nullable SelectionRange toSelectionRange(IList ranges, ColumnMaps columns) {
        return toSelectionRange(ranges.stream()
            .map(ISourceLocation.class::cast)
            .map(l -> Locations.toRange(l, columns))
            .collect(Collectors.toList()));
    }

    /**
     * Folds a {@link List} of {@link Range}s into a single, nested {@link SelectionRange}.
     * @param ranges The range hierarchy. Should be ordered parent-before-child, where any range contains the next one.
     * @return A range with optional parent ranges
     */
    public static @Nullable SelectionRange toSelectionRange(List<Range> ranges) {
        // assumes parent-before-child ordering
        SelectionRange selectionRange = null;
        for (var r : ranges) {
            selectionRange = new SelectionRange(r, selectionRange);
        }
        return selectionRange;
    }
}
