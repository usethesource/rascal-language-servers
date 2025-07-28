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

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.util.Ranges;
import org.rascalmpl.vscode.lsp.util.locations.IRangeMap;


class ContainmentTree<K, V> {
    class Node {
        private K key;
        private V value;
        private ContainmentTree<K, V> subTree;

        Node(K key, V value) {
            this.key = key;
            this.value = value;
            this.subTree = new ContainmentTree<>(contains);
        }

        Node(K key, V value, Set<Node> nodes) {
            this(key, value);
            this.subTree.rootNodes.addAll(nodes);
        }

        K getKey() { return this.key; }
        V getValue() { return this.value; }
        void setValue(V value) { this.value = value; }
        ContainmentTree<K, V> getSubtree() { return this.subTree; }

        private @Nullable Node ceilingNode(K key) {
            if (getKey().equals(key)) {
                return this;
            }
            if (contains.test(getKey(), key)) {
                var subCeiling = getSubtree().ceilingNode(key);
                if (subCeiling != null) {
                    return subCeiling;
                }
                return this;
            }
            return null;
        }

        private @Nullable V get(K key) {
            if (getKey().equals(key)) {
                return getValue();
            }
            if (contains.test(getKey(), key)) {
                return subTree.get(key);
            }
            return null;
        }
    }

    private Set<Node> rootNodes;
    private BiPredicate<K, K> contains;

    public ContainmentTree(BiPredicate<K, K> containsFunc) {
        this.rootNodes = new HashSet<>();
        this.contains = containsFunc;
    }

    public ContainmentTree(Map<K, V> m, BiPredicate<K, K> containsFunc) {
        this(containsFunc);
        for (var e : m.entrySet()) {
            put(e.getKey(), e.getValue());
        }
    }

    public void put(K key, V value) {
        for (var node : rootNodes) {
            if (node.getKey().equals(key)) {
                // this node has our key
                node.setValue(value);
                return;
            } else if (contains.test(node.key, key)) {
                // this node's key contains our key; go deeper
                node.getSubtree().put(key, value);
                return;
            }
        }

        var containedNodes = rootNodes.stream()
            .filter(node -> contains.test(key, node.getKey()))
            .collect(Collectors.toSet());

        var mergedNode = new Node(key, value, containedNodes);
        this.rootNodes.removeAll(containedNodes);
        this.rootNodes.add(mergedNode);
    }

    public @Nullable Node ceilingNode(K key) {
        return rootNodes.stream()
            .map(node -> node.ceilingNode(key))
            .filter(Objects::nonNull)
            .reduce((n1, n2) -> {
                var k1 = n1.getKey();
                var k2 = n2.getKey();
                if (k1.equals(k2) || contains.test(k1, k2)) {
                    return n2;
                }
                if (contains.test(k2, k1)) {
                    return n1;
                }
                // return either here, since it should be reduced away anyway
                return n1;
            }).orElse(null);
    }

    public @Nullable V get(K key) {
        for (var node : this.rootNodes) {
            var v = node.get(key);
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    public V computeIfAbsent(K key, Function<K, V> compute ) {
        // TODO Optimize if slow; this could be 1 pass over the tree
        var value = get(key);
        if (value != null) {
            return value;
        }

        value = compute.apply(key);
        put(key, value);
        return value;
    }
}

public class TreeMapLookup<T> implements IRangeMap<T> {

    private final ContainmentTree<Range, T> data = new ContainmentTree<>(Ranges::containsRange);

    @Override
    public @Nullable T lookup(Range from) {
        var ceil = data.ceilingNode(from);
        return ceil != null ? ceil.getValue() : null;
    }

    @Override
    public @Nullable T lookup(Position at) {
        return lookup(new Range(at, at));
    }

    @Override
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
