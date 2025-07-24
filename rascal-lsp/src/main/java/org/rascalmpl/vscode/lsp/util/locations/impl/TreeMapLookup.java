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

import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.Function;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.util.Ranges;
import org.rascalmpl.vscode.lsp.util.locations.IRangeMap;

public class TreeMapLookup<T> implements IRangeMap<T> {

    private final NavigableMap<Range, T> data = new TreeMap<>(TreeMapLookup::compareRanges);

    private static int compareRanges(Range a, Range b) {
        if (a.equals(b)) {
            return 0;
        }
        // check containment; strict since `a != b`
        // parent is always smaller than child
        if (Ranges.containsRange(a, b)) {
            return -1;
        }
        if (Ranges.containsRange(b, a)) {
            return 1;
        }

        Position aStart = a.getStart();
        Position aEnd = a.getEnd();
        Position bStart = b.getStart();
        Position bEnd = b.getEnd();

        if (aStart.getLine() != bStart.getLine()) {
            return Integer.compare(aStart.getLine(), bStart.getLine());
        }
        if (aEnd.getLine() != bEnd.getLine()) {
            return Integer.compare(aEnd.getLine(), bEnd.getLine());
        }
        // start characters cannot be equal since start/end lines are equal and neither range contains the other
        return Integer.compare(aStart.getCharacter(), bStart.getCharacter());
    }

    @Override
    public @Nullable T lookup(Range from) {
        // since we allow for overlapping ranges, it might be that we have to
        // search all the way to the "bottom" of the tree to see if we are
        // contained in something larger than the closest key
        // if we could come up with a *valid* ordering such that `data.floorKey(from)` is always
        // the smallest key containing `from` (or another key when none contain `from`), we could use `data.floorEntry` here instead of iterating
        return data.headMap(from, true).descendingMap().entrySet()
            .stream()
            .filter(e -> Ranges.containsRange(e.getKey(), from))
            .map(Entry::getValue)
            .findFirst().orElse(null);
    }

    @Override
    public @Nullable T lookup(Position at) {
        return lookup(new Range(at, at));
    }

    public void put(Range from, T to) {
        data.put(from, to);
    }

    public @Nullable T getExact(Range from) {
        return data.get(from);
    }

    public T computeIfAbsent(Range exact, Function<Range, T> compute ) {
        return data.computeIfAbsent(exact, compute);
    }

    public static <T> IRangeMap<T> emptyMap() {
        return new IRangeMap<>() {

            @Override
            public void put(Range area, T value) {
                throw new UnsupportedOperationException("Empty class map is not mutable");
            }

            @Override
            public @Nullable T lookup(Range from) {
                return null;
            }

            @Override
            public @Nullable T lookup(Position at) {
                return null;
            }

        };

    }
}
