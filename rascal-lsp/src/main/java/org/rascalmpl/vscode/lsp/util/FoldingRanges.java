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

import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeKind;
import org.rascalmpl.values.RascalValueFactory;
import org.rascalmpl.values.ValueFactoryFactory;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.values.parsetrees.ProductionAdapter;
import org.rascalmpl.values.parsetrees.TreeAdapter;
import org.rascalmpl.values.parsetrees.visitors.TreeVisitor;

import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IValueFactory;

public class FoldingRanges {

    private FoldingRanges() { /* hide implicit public constructor */ }

    public static List<FoldingRange> getFoldingRanges(ITree tree) {
        List<FoldingRange> result = new ArrayList<>();
        tree.accept(new TreeVisitor<RuntimeException>() {
            @Override
            public @Nullable ITree visitTreeAppl(ITree arg) throws RuntimeException {
                if (TreeAdapter.isComment(arg)) {
                    addFoldingRegion(result, TreeAdapter.getLocation(arg), FoldingRangeKind.Comment);
                } else if (arg.asWithKeywordParameters().getParameter("foldable") != null) {
                    addFoldingRegion(result, TreeAdapter.getLocation(arg), FoldingRangeKind.Region);
                } else {
                    IValueFactory vf = ValueFactoryFactory.getValueFactory();
                    if (ProductionAdapter.hasAttribute(TreeAdapter.getProduction(arg), vf.constructor(RascalValueFactory.Attr_Tag, vf.node("Foldable")))) {
                        addFoldingRegion(result, TreeAdapter.getLocation(arg), FoldingRangeKind.Region);
                    }
                }
                for (IValue child : TreeAdapter.getArgs(arg)) {
                    child.accept(this);
                }
                return null;
            }

            @Override
            public @Nullable ITree visitTreeAmb(ITree arg) throws RuntimeException {
                return null;
            }

            @Override
            public @Nullable ITree visitTreeChar(ITree arg) throws RuntimeException {
                return null;
            }

            @Override
            public @Nullable ITree visitTreeCycle(ITree arg) throws RuntimeException {
                return null;
            }

        });
        return result;
    }

    static void addFoldingRegion(List<FoldingRange> ranges, ISourceLocation src, String kind) {
        //subtract 1 due to line numbers being 0-based
        int beginLine = src.getBeginLine() - 1;
        int endLine = src.getEndLine() - (src.getEndColumn() == 0 ? 2 : 1); //do not include last line if folding range ends before the first character
        if (endLine > beginLine) {
            FoldingRange range = new FoldingRange();
            range.setStartLine(beginLine);
            range.setEndLine(endLine);
            range.setKind(kind);
            ranges.add(range);
        }
    }
}
