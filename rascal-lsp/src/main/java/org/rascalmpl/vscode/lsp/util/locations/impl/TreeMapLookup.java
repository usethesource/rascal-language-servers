/**
 * Copyright (c) 2018, Davy Landman, SWAT.engineering BV All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 * following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.rascalmpl.vscode.lsp.util.locations.impl;

import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.Function;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.rascalmpl.vscode.lsp.util.locations.IRangeMap;

public class TreeMapLookup<T> implements IRangeMap<T> {

    private final NavigableMap<Range, T> data = new TreeMap<>(TreeMapLookup::compareRanges);

    private static int compareRanges(Range a, Range b) {
        Position aStart = a.getStart();
        Position aEnd = a.getEnd();
        Position bStart = b.getStart();
        Position bEnd = b.getEnd();
        if (aEnd.getLine() < bStart.getLine()) {
            return -1;
        }
        if (aStart.getLine() > bEnd.getLine()) {
            return 1;
        }
        // some kind of containment, or just on the same line
        if (aStart.getLine() == bStart.getLine()) {
            // start at same line
            if (aEnd.getLine() == bEnd.getLine()) {
                // end at same line
                if (aStart.getCharacter() == bStart.getCharacter()) {
                    return Integer.compare(aEnd.getCharacter(), bEnd.getCharacter());
                }
                return Integer.compare(aStart.getCharacter(), bStart.getCharacter());
            }
            return Integer.compare(aEnd.getLine(), bEnd.getLine());
        }
        return Integer.compare(aStart.getLine(), aStart.getLine());
    }

    private static boolean rangeContains(Range a, Range b) {
        Position aStart = a.getStart();
        Position aEnd = a.getEnd();
        Position bStart = b.getStart();
        Position bEnd = b.getEnd();

        if (aStart.getLine() <= bStart.getLine()
            && aEnd.getLine() >= bEnd.getLine()) {
            if (aStart.getLine() == bStart.getLine()) {
                if (aStart.getCharacter() > bStart.getCharacter()) {
                    return false;
                }
            }
            if (aEnd.getLine() == bEnd.getLine()) {
                if (aEnd.getCharacter() < bEnd.getCharacter()) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private @Nullable T contains(@Nullable Entry<Range, T> entry, Range from) {
        if (entry != null) {
            Range match = entry.getKey();
            if (rangeContains(match, from)) {
                return entry.getValue();
            }
        }
        return null;
    }

    @Override
    public @Nullable T lookup(Range from) {
        T result = contains(data.floorEntry(from), from);
        if (result == null) {
            // could be that it's at the start of the entry
            result = contains(data.ceilingEntry(from), from);
        }
        return result;
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

}
