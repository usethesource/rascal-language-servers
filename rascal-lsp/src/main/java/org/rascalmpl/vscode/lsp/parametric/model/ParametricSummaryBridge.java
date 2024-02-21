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
package org.rascalmpl.vscode.lsp.parametric.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.MarkedString;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.values.parsetrees.TreeAdapter;
import org.rascalmpl.vscode.lsp.TextDocumentState;
import org.rascalmpl.vscode.lsp.parametric.ILanguageContributions;
import org.rascalmpl.vscode.lsp.util.Diagnostics;
import org.rascalmpl.vscode.lsp.util.Lazy;
import org.rascalmpl.vscode.lsp.util.Versioned;
import org.rascalmpl.vscode.lsp.util.concurrent.InterruptibleFuture;
import org.rascalmpl.vscode.lsp.util.concurrent.ReplaceableFuture;
import org.rascalmpl.vscode.lsp.util.locations.ColumnMaps;
import org.rascalmpl.vscode.lsp.util.locations.IRangeMap;
import org.rascalmpl.vscode.lsp.util.locations.Locations;
import org.rascalmpl.vscode.lsp.util.locations.impl.TreeMapLookup;
import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IRelation;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.ITuple;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IWithKeywordParameters;
import io.usethesource.vallang.type.Type;
import io.usethesource.vallang.type.TypeFactory;
import io.usethesource.vallang.type.TypeStore;

@SuppressWarnings({"deprecation"})
public class ParametricSummaryBridge {
    private static final Logger logger = LogManager.getLogger(ParametricSummaryBridge.class);
    private final Executor exec;
    private final ColumnMaps columns;
    private final ILanguageContributions contrib;
    private final Function<ISourceLocation, TextDocumentState> lookupState;
    private final ISourceLocation file;
    private final SummaryCalculator calculator;

    private final ReplaceableFuture<Lazy<List<Diagnostic>>> messages;
    private volatile CompletableFuture<LazyRangeMapCalculation<List<Location>>> definitions;
    private volatile CompletableFuture<LazyRangeMapCalculation<List<Location>>> implementations;
    private volatile CompletableFuture<LazyRangeMapCalculation<List<Location>>> references;
    private volatile CompletableFuture<LazyRangeMapCalculation<List<Either<String, MarkedString>>>> hovers;

    public ParametricSummaryBridge(Executor exec, ISourceLocation file, ColumnMaps columns,
        ILanguageContributions contrib, Function<ISourceLocation, TextDocumentState> lookupState, SummaryCalculator calculator) {
        this.exec = exec;
        this.file = file;
        this.columns = columns;
        this.contrib = contrib;
        this.lookupState = lookupState;
        this.calculator = calculator;
        messages = ReplaceableFuture.completed(Lazy.defer(Collections::emptyList));
        reloadContributions();
    }

    public void reloadContributions() {
        definitions = contrib
            .hasDedicatedDefines()
            .thenCombine(contrib.askSummaryForDefinitions(), LazyDefinitions::new);
        implementations = contrib
            .hasDedicatedImplementations()
            .thenCombine(contrib.askSummaryForImplementations(), LazyImplementations::new);
        references = contrib
            .hasDedicatedReferences()
            .thenCombine(contrib.askSummaryForReferences(), LazyReferences::new);
        hovers = contrib
            .hasDedicatedDocumentation()
            .thenCombine(contrib.askSummaryForDocumentation(), RelationDocumentLookupMap::new);
    }

    public void invalidate(boolean isClosing) {
        definitions.thenAccept(d -> d.invalidate(isClosing));
        implementations.thenAccept(d -> d.invalidate(isClosing));
        references.thenAccept(d -> d.invalidate(isClosing));
        hovers.thenAccept(d -> d.invalidate(isClosing));
    }

    private abstract class LazyRangeMapCalculation<T> {
        private final ReplaceableFuture<Lazy<IRangeMap<T>>> result;
        private volatile @Nullable InterruptibleFuture<Lazy<IRangeMap<T>>> lastFuture = null;

        protected final boolean dedicatedCall;
        protected final boolean checkSummary;
        protected final T empty;
        protected final String logName;
        private final boolean requestSummaryIfNeeded;
        private volatile boolean internalSummaryCalc = true;


        LazyRangeMapCalculation(String logName, boolean dedicatedCall, boolean checkSummary, boolean requestSummaryIfNeeded, T empty) {
            logger.debug("{} init, dedicated: {} checkSummary: {}", logName, dedicatedCall, checkSummary);
            this.requestSummaryIfNeeded = requestSummaryIfNeeded;
            this.logName = logName;
            this.dedicatedCall = dedicatedCall;
            this.checkSummary = checkSummary;
            this.empty = empty;
            this.result = new ReplaceableFuture<>(
                CompletableFuture.completedFuture(
                    Lazy.defer(TreeMapLookup::emptyMap)));
        }

        public void invalidate(boolean isClosing) {
            var last = lastFuture;
            if (last != null && (!isClosing || internalSummaryCalc)) {
                logger.trace("{}: Interrupting {}", logName, last);
                last.interrupt();
                lastFuture = null;
            }
        }

        public void newSummary(InterruptibleFuture<IConstructor> summary, boolean internal) {
            if (!checkSummary) {
                logger.trace("{}: Ignoring new summary, since summaries don't provide info for us", logName);
                return;
            }
            logger.trace("{}: deriving when new summary ({}) is ready", logName, summary);
            internalSummaryCalc = internal;
            replaceFuture(mapSummary(summary.thenApply(IConstructor::asWithKeywordParameters)));
        }

        private void replaceFuture(InterruptibleFuture<Lazy<IRangeMap<T>>> newFuture) {
            lastFuture = result.replace(newFuture);
        }

        abstract InterruptibleFuture<Lazy<IRangeMap<T>>> mapSummary(InterruptibleFuture<IWithKeywordParameters<? extends IConstructor>> newSummary);

        abstract InterruptibleFuture<T> requestDedicated(Position r);

        public InterruptibleFuture<T> lookup(Position cursor) {
            return lookup(cursor, contrib::analyze);
        }

        private InterruptibleFuture<T> lookup(Position cursor, SummaryCalculator calculator) {
            var activeSummary = lastFuture;
            if (activeSummary == null && dedicatedCall) {
                logger.trace("{} requesting dedicated for: {}", logName, cursor);
                return requestDedicated(cursor);
            }
            if (!checkSummary || (activeSummary == null && !requestSummaryIfNeeded)) {
                logger.trace("{}: nothing in summary, no dedicated call, so returning empty future", logName);
                // else we can't do a thing anyway
                return InterruptibleFuture.completedFuture(empty);
            }
            if (activeSummary == null/* implied:&& requestSummaryIfNeeded */ ) {
                logger.trace("{} requesting summary since we need it for: {}", logName, cursor);
                calculateSummary(true, calculator);
                activeSummary = lastFuture;
                if (activeSummary == null) {
                    logger.error("{} something went wrong with requesting a new summary for {}", logName, cursor);
                    return InterruptibleFuture.completedFuture(empty);
                }
            }

            logger.trace("{}: using summary to lookup {} (in summary: {})", logName, cursor, activeSummary);
            return activeSummary
                .thenApplyAsync(Lazy::get, exec)
                .thenApply(l -> l.lookup(new Range(cursor, cursor)))
                .thenApply(r -> r == null ? this.empty : r);
        }
    }

    @FunctionalInterface
    public interface SummaryCalculator {
        InterruptibleFuture<IConstructor> calc(ISourceLocation file, ITree tree);
    }

    @FunctionalInterface
    public interface DedicatedLookupFunction {
        InterruptibleFuture<ISet> lookup(ISourceLocation file, ITree tree, ITree cursor);
    }

    private abstract class RelationLookupMap<T> extends LazyRangeMapCalculation<List<T>> {
        private final String kwField;
        private final DedicatedLookupFunction dedicatedCalcFunc;

        RelationLookupMap(String logName, boolean dedicatedCall, boolean checkSummary, boolean requestSummaryIfNeeded, String kwField, DedicatedLookupFunction dedicatedCalcFunc) {
            super(logName, dedicatedCall, checkSummary, requestSummaryIfNeeded, Collections.emptyList());
            this.kwField = kwField;
            this.dedicatedCalcFunc = dedicatedCalcFunc;
        }

        @Override
        InterruptibleFuture<Lazy<IRangeMap<List<T>>>> mapSummary(
            InterruptibleFuture<IWithKeywordParameters<? extends IConstructor>> newSummary) {
            logger.trace("{}: Mapping summary by getting {}", logName, kwField);

            return newSummary.thenApply(s ->
                Lazy.defer(() -> translateRelation(getKWFieldSet(s, kwField)))
            );
        }

        private IRelation<ISet> getKWFieldSet(IWithKeywordParameters<? extends IConstructor> data, String name) {
            if (data.hasParameter(name)) {
                return ((ISet) data.getParameter(name)).asRelation();
            }
            return IRascalValueFactory.getInstance().set().asRelation();
        }

        @Override
        InterruptibleFuture<List<T>> requestDedicated(Position cursor) {
            var result = lookupState.apply(file)
                    .getCurrentTreeAsync()
                    .thenApplyAsync(t -> {
                        var line = cursor.getLine() + 1;
                        var translatedOffset = columns.get(file).translateInverseColumn(line, cursor.getCharacter(), false);
                        var cursorTree = TreeAdapter.locateLexical(t.get(), line, translatedOffset);
                        if (cursorTree == null) {
                            logger.trace("{}: could not find substree at line {} and offset {}", logName, line, translatedOffset);
                            return InterruptibleFuture.completedFuture(IRascalValueFactory.getInstance().set());
                        }
                        logger.trace("{}: looked up cursor to: {}, now calling dedicated function", () -> logName, () -> TreeAdapter.yield(cursorTree));
                        return dedicatedCalcFunc.lookup(file, t.get(), cursorTree);
                    }, exec);
            return InterruptibleFuture.flatten(result, exec)
                .thenApply(s -> {
                    logger.trace("{}: dedicated returned: {}", logName, s);
                    if (s == null || s.isEmpty()) {
                        return Collections.emptyList();
                    }
                    int size = s.size();

                    if (size == 1) {
                        var v = s.iterator().next();
                        return Collections.singletonList(mapValue(v));
                    }
                    var locs = new ArrayList<T>(size);
                    for (IValue v : s) {
                        locs.add(mapValue(v));
                    }
                    return locs;
                });
        }

        protected abstract T mapValue(IValue v);



        IRangeMap<List<T>> translateRelation(IRelation<ISet> binaryRel) {
            logger.trace("{}: summary contain rel of size:{}", () -> logName, () -> binaryRel.asContainer().size());
            TreeMapLookup<List<T>> result = new TreeMapLookup<>();
            for (IValue v: binaryRel) {
                ITuple row = (ITuple)v;
                Range from = Locations.toRange((ISourceLocation)row.get(0), columns);
                T to = mapValue(row.get(1));
                var existing = result.getExact(from);
                if (existing == null) {
                    // most cases there is only a single entry, to so save a lot of memory, we store a singleton list to start with
                    result.put(from, Collections.singletonList(to));
                }
                else if (existing.size() == 1) {
                    // we had a singleton list in there, so let's replace it with a regular list
                    existing = new ArrayList<>(existing);
                    result.put(from, existing);
                    existing.add(to);
                }
                else {
                    existing.add(to);
                }
            }
            return result;
        }
    }

    private class RelationLocationLookupMap extends RelationLookupMap<Location> {

        RelationLocationLookupMap(boolean dedicatedCall, boolean checkSummary,boolean requestSummaryIfNeeded, String kwField, DedicatedLookupFunction dedicatedCalcFunc) {
            super(kwField, dedicatedCall, checkSummary, requestSummaryIfNeeded, kwField, dedicatedCalcFunc);
        }
        @Override
        protected Location mapValue(IValue v) {
            return Locations.toLSPLocation((ISourceLocation)v, columns);
        }
    }

    private class RelationDocumentLookupMap extends RelationLookupMap<Either<String, MarkedString>> {

        RelationDocumentLookupMap(boolean dedicatedCall, boolean checkSummary) {
            super("documentation", dedicatedCall, checkSummary, false, "documentation", contrib::documentation );
        }
        @Override
        protected Either<String, MarkedString> mapValue(IValue v) {
            return Either.forLeft(((IString) v).getValue());
        }
    }


    private class LazyDefinitions extends RelationLocationLookupMap {
        public LazyDefinitions(boolean dedicatedCall, boolean askSummary) {
            super(dedicatedCall, askSummary, true, "definitions", contrib::defines);
        }
    }

    private class LazyImplementations extends RelationLocationLookupMap {
        public LazyImplementations(boolean dedicatedCall, boolean askSummary) {
            super(dedicatedCall, askSummary, true, "implementations", contrib::implementations);
        }
    }

    private class LazyReferences extends RelationLocationLookupMap {
        public LazyReferences(boolean dedicatedCall, boolean askSummary) {
            super(dedicatedCall, askSummary, true, "references", contrib::references);
        }
    }

    public CompletableFuture<List<Location>> getDefinition(Position cursor) {
        return definitions.thenCompose(d -> d.lookup(cursor).get());
    }

    public CompletableFuture<List<Location>> getReferences(Position cursor) {
        return references.thenCompose(d -> d.lookup(cursor).get());
    }

    public CompletableFuture<List<Location>> getImplementations(Position cursor) {
        return implementations.thenCompose(d -> d.lookup(cursor).get());
    }


    public class SummaryCalculation {
        private final CompletableFuture<Versioned<ITree>> tree;
        private final InterruptibleFuture<IConstructor> summary;
        private final InterruptibleFuture<Lazy<List<Diagnostic>>> messages;

        public SummaryCalculation(CompletableFuture<Versioned<ITree>> tree, InterruptibleFuture<IConstructor> summary, InterruptibleFuture<Lazy<List<Diagnostic>>> messages) {
            this.tree = tree;
            this.summary = summary;
            this.messages = messages;
        }

        public <T> CompletableFuture<@Nullable T> thenApply(BiFunction<Versioned<ITree>, List<Diagnostic>, T> fn) {
            return tree.thenCombine(unwrap(messages), (t, ms) ->
                // When a summary calculation is interrupted, the `messages`
                // future does complete (prematurely), but the list it supplies
                // is empty. As this is indistinguishable from an *un*interrupted
                // builder that just yields no messages, we need to check for
                // interruption explicitly.
                this.summary.isInterrupted() ? null : fn.apply(t, ms));
        }

        private CompletableFuture<List<Diagnostic>> unwrap(InterruptibleFuture<Lazy<List<Diagnostic>>> messages) {
            return messages
                .get()                     // Take inner `CompletableFuture` from outer `InterruptibleFuture`
                .thenApply(Supplier::get); // Evaluate the lazy computation
        }
    }

    public SummaryCalculation calculateSummary() {
        return calculateSummary(false, calculator);
    }

    public SummaryCalculation calculateSummary(CompletableFuture<Versioned<ITree>> tree) {
        return calculateSummary(false, calculator, tree);
    }

    private SummaryCalculation calculateSummary(boolean internal, SummaryCalculator calculator) {
        var tree = lookupState.apply(file).getCurrentTreeAsync();
        return calculateSummary(internal, calculator, tree);
    }

    private SummaryCalculation calculateSummary(boolean internal, SummaryCalculator calculator, CompletableFuture<Versioned<ITree>> tree) {
        logger.trace("Requesting Summary calculation for: {}", file);

        InterruptibleFuture<IConstructor> summary = InterruptibleFuture.flatten(tree
            .thenApplyAsync(t -> calculator.calc(file, t.get()), exec)
            , exec);
        definitions.thenAccept(d -> d.newSummary(summary, internal));
        references.thenAccept(d -> d.newSummary(summary, internal));
        implementations.thenAccept(d -> d.newSummary(summary, internal));
        hovers.thenAccept(d -> d.newSummary(summary, internal));

        InterruptibleFuture<Lazy<List<Diagnostic>>> lazyMessages = summary.thenApply(s -> Lazy.defer(() -> {
            var sum = s.asWithKeywordParameters();
            if (sum.hasParameter("messages")) {
                return ((ISet)sum.getParameter("messages")).stream()
                    .map(d -> Diagnostics.translateDiagnostic((IConstructor)(((ITuple)d).get(1)), columns))
                    .collect(Collectors.toList());
            }
            return Collections.emptyList();
        }));
        messages.replace(lazyMessages);

        return new SummaryCalculation(tree, summary, lazyMessages);
    }

    public CompletableFuture<List<Either<String, MarkedString>>> getHover(Position cursor) {
        return hovers.thenCompose(h -> h.lookup(cursor).get());
    }

    public CompletableFuture<List<Diagnostic>> getMessages() {
        return messages.get().thenApply(Lazy::get);
    }

    private static final Type summaryCons;

    static {
        TypeFactory TF = TypeFactory.getInstance();
        TypeStore TS = new TypeStore();
        summaryCons = TF.constructor(TS, TF.abstractDataType(TS, "Summary"), "summary", TF.sourceLocationType(), "src");
    }

    public static IConstructor emptySummary(ISourceLocation src) {
        return IRascalValueFactory.getInstance().constructor(summaryCons, src);
    }

}
