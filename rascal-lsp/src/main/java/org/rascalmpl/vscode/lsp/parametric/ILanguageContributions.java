/*
 * Copyright (c) 2018-2021, NWO-I CWI and Swat.engineering
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
package org.rascalmpl.vscode.lsp.parametric;

import java.util.concurrent.CompletableFuture;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.vscode.lsp.util.concurrent.InterruptibleFuture;
import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;

public interface ILanguageContributions {
    public String getName();
    public String getExtension();
    public CompletableFuture<ITree> parseSourceFile(ISourceLocation loc, String input);
    public InterruptibleFuture<IList> outline(ITree input);
    public InterruptibleFuture<IConstructor> summarize(ISourceLocation loc, ITree input);
    public InterruptibleFuture<ISet> lenses(ITree input);
    public InterruptibleFuture<Void> executeCommand(String command);
    public InterruptibleFuture<IList> inlayHint(@Nullable ITree input);
    public InterruptibleFuture<ISet> documentation(ISourceLocation loc, ITree input, ITree cursor);
    public InterruptibleFuture<ISet> defines(ISourceLocation loc, ITree input, ITree cursor);
    public InterruptibleFuture<ISet> references(ISourceLocation loc, ITree input, ITree cursor);
    public InterruptibleFuture<ISet> implementations(ISourceLocation loc, ITree input, ITree cursor);

    public CompletableFuture<Boolean> hasDedicatedDocumentation();
    public CompletableFuture<Boolean> hasDedicatedDefines();
    public CompletableFuture<Boolean> hasDedicatedReferences();
    public CompletableFuture<Boolean> hasDedicatedImplementations();

    public CompletableFuture<Boolean> askSummaryForDocumentation();
    public CompletableFuture<Boolean> askSummaryForDefinitions();
    public CompletableFuture<Boolean> askSummaryForReferences();
    public CompletableFuture<Boolean> askSummaryForImplementations();
}
