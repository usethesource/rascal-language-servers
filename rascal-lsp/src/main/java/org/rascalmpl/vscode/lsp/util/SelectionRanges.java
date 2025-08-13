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
