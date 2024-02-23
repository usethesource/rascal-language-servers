/*
 * Copyright (c) 2018-2023, NWO-I CWI and Swat.engineering
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

import java.util.concurrent.atomic.AtomicReference;

import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.values.parsetrees.ITree;

/**
 * This class keeps together an object (e.g., a list of diagnostics messages)
 * and the syntax tree that corresponds to the object (e.g., the tree for which
 * the diagnostics were computed).
 */
public class WithTree<T> {
    private final ITree tree;
    private final T object;

    public WithTree(T object) {
        this(IRascalValueFactory.getInstance().character(0), object);
    }

    public WithTree(ITree tree, T object) {
        this.tree = tree;
        this.object = object;
    }

    public WithTree(Versioned<ITree> tree, T object) {
        this(tree.get(), object);
    }

    public ITree tree() {
        return tree;
    }

    public T get() {
        return object;
    }

    @Override
    public String toString() {
        return String.format("%s, with tree: %s", object, tree);
    }

    public static final ITree DEFAULT_TREE = IRascalValueFactory.getInstance().character(0);

    public static <T> WithTree<T> withDefaultTree(T object) {
        return new WithTree<>(DEFAULT_TREE, object);
    }

    public static <T> Versioned<ITree> getVersionedTree(Versioned<WithTree<T>> versioned) {
        return new Versioned<>(versioned.version(), versioned.get().tree());
    }

    public static <T> T unwrap(AtomicReference<Versioned<WithTree<T>>> ref) {
        return unwrap(ref.get());
    }

    public static <T> T unwrap(Versioned<WithTree<T>> versioned) {
        return versioned
            .get()  // Unwrap `Versioned` to get `WithTree<T>`
            .get(); // Unwrap `WithTree` to get `T`
    }
}
