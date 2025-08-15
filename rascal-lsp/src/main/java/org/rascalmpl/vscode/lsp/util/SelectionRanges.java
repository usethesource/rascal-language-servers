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

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Collectors;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SelectionRange;
import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.values.parsetrees.TreeAdapter;
import org.rascalmpl.vscode.lsp.util.locations.ColumnMaps;
import org.rascalmpl.vscode.lsp.util.locations.Locations;

import io.usethesource.vallang.IList;
import io.usethesource.vallang.ISourceLocation;

public class SelectionRanges {
    private SelectionRanges() { /* hide implicit constructor */ }

    /**
     * Folds a {@link IList} of {@link ISourceLocation}s into a single, nested {@link SelectionRange}.
     * @param ranges The range hierarchy. Should be ordered child-before-parent, where any source location is contained by the next.
     * @param columns The editor's column map.
     * @return A range with optional parent ranges.
     */
    public static @Nullable SelectionRange toSelectionRange(IList ranges, ColumnMaps columns) {
        return toSelectionRange(ranges.stream()
            .map(ISourceLocation.class::cast)
            .map(l -> Locations.toRange(l, columns))
            .collect(Collectors.toList()));
    }

    /**
     * Folds a {@link List} of {@link Range}s into a single, nested {@link SelectionRange}.
     * @param ranges The range hierarchy. Should be ordered child-before-parent, where any range is contained by the next.
     * @return A range with optional parent ranges
     */
    public static @Nullable SelectionRange toSelectionRange(List<Range> ranges) {
        // assumes child-before-parent ordering
        SelectionRange selectionRange = null;
        for (var r : reverse(ranges)) {
            selectionRange = new SelectionRange(r, selectionRange);
        }
        return selectionRange;
    }

    private static <T> Iterable<T> reverse(List<T> list) {
        return () -> new Iterator<T>() {
            int current = list.size() - 1;

            @Override
            public boolean hasNext() {
                return current >= 0;
            }

            @Override
            public T next() {
                try {
                    return list.get(current--);
                } catch (IndexOutOfBoundsException e) {
                    throw new NoSuchElementException();
                }
            }
        };
    }

    public static IList defaultImplementation(IList focus) {
        return focus.stream()
            .filter(Objects::nonNull)
            .filter(ITree.class::isInstance)
            .map(ITree.class::cast)
            .map(TreeAdapter::getLocation)
            .distinct()
            .collect(IRascalValueFactory.getInstance().listWriter());
    }
}
