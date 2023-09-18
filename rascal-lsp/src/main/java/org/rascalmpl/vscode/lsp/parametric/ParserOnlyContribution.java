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
package org.rascalmpl.vscode.lsp.parametric;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.rascalmpl.exceptions.RuntimeExceptionFactory;
import org.rascalmpl.interpreter.Configuration;
import org.rascalmpl.interpreter.Evaluator;
import org.rascalmpl.interpreter.asserts.Ambiguous;
import org.rascalmpl.interpreter.staticErrors.UndeclaredNonTerminal;
import org.rascalmpl.interpreter.utils.JavaBridge;
import org.rascalmpl.parser.gtd.IGTD;
import org.rascalmpl.parser.gtd.exception.ParseError;
import org.rascalmpl.parser.gtd.exception.UndeclaredNonTerminalException;
import org.rascalmpl.parser.gtd.recovery.IRecoverer;
import org.rascalmpl.parser.gtd.result.out.DefaultNodeFlattener;
import org.rascalmpl.parser.uptr.UPTRNodeFactory;
import org.rascalmpl.parser.uptr.action.NoActionExecutor;
import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.values.parsetrees.SymbolAdapter;
import org.rascalmpl.values.parsetrees.TreeAdapter;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.ParserSpecification;
import org.rascalmpl.vscode.lsp.util.concurrent.InterruptibleFuture;
import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IValueFactory;

public class ParserOnlyContribution implements ILanguageContributions {

    private final String name;
    private final String extension;
    private final @Nullable Exception loadingParserError;
    private final @Nullable Class<IGTD<IConstructor, ITree, ISourceLocation>> parserClass;
    private final String parserMethodName;
    private final boolean allowAmbiguity;

    public ParserOnlyContribution(String name, String extension, ParserSpecification spec) {
        this.name = name;
        this.extension = extension;
        Class<IGTD<IConstructor, ITree, ISourceLocation>> clzz = null;
        Exception err = null;
        try {
            clzz = loadParserClass(spec);
        } catch (ClassNotFoundException | IOException e) {
            err = e;
        }
        this.parserClass = clzz;
        this.loadingParserError = err;
        this.parserMethodName = (spec.getTerminalIsStart() ? "start__" : "") + spec.getTerminalName();
        this.allowAmbiguity = spec.getAllowAmbiguity();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getExtension() {
        return extension;
    }

    @Override
    public CompletableFuture<ITree> parseSourceFile(ISourceLocation loc, String input) {
        if (parserClass == null || loadingParserError != null) {
            return CompletableFuture.failedFuture(new RuntimeException("ParserClass did not load", loadingParserError));
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                IGTD<IConstructor, ITree, ISourceLocation> parser
                    = parserClass.getDeclaredConstructor().newInstance();
                return (ITree)parser.parse(
                    parserMethodName, loc.getURI(), input.toCharArray(), new NoActionExecutor(),
                    new DefaultNodeFlattener<>(),
                    new UPTRNodeFactory(allowAmbiguity),
                    (IRecoverer<IConstructor>) null
                );
            }
            catch (ParseError pe) {
                ISourceLocation errorLoc = pe.getLocation();
                throw RuntimeExceptionFactory.parseError(errorLoc);
            }
            catch (Ambiguous e) {
                ITree tree = e.getTree();
                IValueFactory vf = IRascalValueFactory.getInstance();
                throw RuntimeExceptionFactory.ambiguity(e.getLocation(), vf.string(SymbolAdapter.toString(TreeAdapter.getType(tree), false)), vf.string(TreeAdapter.yield(tree)));
            }
            catch (UndeclaredNonTerminalException e){
                throw new UndeclaredNonTerminal(e.getName(), e.getClassName(), loc);
            }
            catch (ReflectiveOperationException | IllegalArgumentException | SecurityException e) {
                throw new CompletionException("Error with loaded parser", e);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static Class<IGTD<IConstructor, ITree, ISourceLocation>> loadParserClass(ParserSpecification spec) throws ClassNotFoundException, IOException {
        var bridge = new JavaBridge(
            Collections.singletonList(Evaluator.class.getClassLoader()),
            IRascalValueFactory.getInstance(),
            new Configuration());
        return (Class<IGTD<IConstructor, ITree, ISourceLocation>>)bridge.loadClass(new FileInputStream(spec.getParserFile()));
    }


    private static UnsupportedOperationException disabledFunction() {
        return new UnsupportedOperationException("ParserOnly contribution");
    }

    @Override
    public InterruptibleFuture<IList> outline(ITree input) {
        throw disabledFunction();
    }

    @Override
    public InterruptibleFuture<IConstructor> summarize(ISourceLocation loc, ITree input) {
        throw disabledFunction();
    }

    @Override
    public InterruptibleFuture<ISet> lenses(ITree input) {
        throw disabledFunction();
    }

    @Override
    public InterruptibleFuture<@Nullable IValue> executeCommand(String command) {
        throw disabledFunction();
    }

    @Override
    public InterruptibleFuture<IList> inlayHint(@Nullable ITree input) {
        throw disabledFunction();
    }

    @Override
    public InterruptibleFuture<ISet> documentation(ISourceLocation loc, ITree input, ITree cursor) {
        throw disabledFunction();
    }

    @Override
    public InterruptibleFuture<ISet> defines(ISourceLocation loc, ITree input, ITree cursor) {
        throw disabledFunction();
    }

    @Override
    public InterruptibleFuture<ISet> references(ISourceLocation loc, ITree input, ITree cursor) {
        throw disabledFunction();
    }

    @Override
    public InterruptibleFuture<ISet> implementations(ISourceLocation loc, ITree input, ITree cursor) {
        throw disabledFunction();
    }

    @Override
    public CompletableFuture<Boolean> hasDedicatedDocumentation() {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletableFuture<Boolean> hasDedicatedDefines() {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletableFuture<Boolean> hasDedicatedReferences() {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletableFuture<Boolean> hasDedicatedImplementations() {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletableFuture<Boolean> hasOutline() {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletableFuture<Boolean> hasSummarize() {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletableFuture<Boolean> hasLenses() {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletableFuture<Boolean> hasExecuteCommand() {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletableFuture<Boolean> hasInlayHint() {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletableFuture<Boolean> askSummaryForDocumentation() {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletableFuture<Boolean> askSummaryForDefinitions() {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletableFuture<Boolean> askSummaryForReferences() {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletableFuture<Boolean> askSummaryForImplementations() {
        return CompletableFuture.completedFuture(false);
    }

}
