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
import org.rascalmpl.vscode.lsp.parametric.ILanguageContributions.SummaryConfig;
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

@SuppressWarnings({"deprecation", "java:S1192"})
public class ParametricSummaryBridge {
    private static final Logger logger = LogManager.getLogger(ParametricSummaryBridge.class);
    private final Executor exec;
    private final ColumnMaps columns;
    private final ILanguageContributions contrib;
    private final Function<ISourceLocation, TextDocumentState> lookupState;
    private final ISourceLocation file;
    private final BiFunction<ISourceLocation, ITree, InterruptibleFuture<IConstructor>> calculator;

    private final ReplaceableFuture<List<Diagnostic>> messages;
    private volatile CompletableFuture<LazyRangeMapCalculation<List<Location>>> definitions;
    private volatile CompletableFuture<LazyRangeMapCalculation<List<Location>>> implementations;
    private volatile CompletableFuture<LazyRangeMapCalculation<List<Location>>> references;
    private volatile CompletableFuture<LazyRangeMapCalculation<List<Either<String, MarkedString>>>> hovers;

    private final Supplier<CompletableFuture<SummaryConfig>> summarizerConfig;
    private volatile CompletableFuture<SummarizerSummaryFactory> summaryFactory;

    public ParametricSummaryBridge(Executor exec, ISourceLocation file, ColumnMaps columns,
        ILanguageContributions contrib, Function<ISourceLocation, TextDocumentState> lookupState,
        BiFunction<ISourceLocation, ITree, InterruptibleFuture<IConstructor>> calculator,
        Supplier<CompletableFuture<SummaryConfig>> summarizerConfig) {

        this.exec = exec;
        this.file = file;
        this.columns = columns;
        this.contrib = contrib;
        this.lookupState = lookupState;
        this.calculator = calculator;
        this.summarizerConfig = summarizerConfig;
        messages = ReplaceableFuture.completed(Collections.emptyList());
        reloadContributions();
    }

    public void reloadContributions() {
        definitions = contrib
            .hasDedicatedDefinitions()
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

        summaryFactory = summarizerConfig.get().thenApply(config ->
            new SummarizerSummaryFactory(config, exec, columns, contrib));
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
                var tree = lookupState.apply(file).getCurrentTreeAsync();
                calculateSummary(true, tree);
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
            super(dedicatedCall, askSummary, true, "definitions", contrib::definitions);
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

    public CompletableFuture<Versioned<Summary>> calculateSummary(CompletableFuture<Versioned<ITree>> tree) {
        return calculateSummary(false, tree);
    }

    private CompletableFuture<Versioned<Summary>> calculateSummary(boolean internal, CompletableFuture<Versioned<ITree>> tree) {
        logger.trace("Requesting Summary calculation for: {}", file);
        var version = tree.thenApply(Versioned::version);
        var summary = summaryFactory.thenApply(f -> f.create(calculate(tree)));
        return version.thenCombine(summary, Versioned::new);
    }

    public InterruptibleFuture<List<Diagnostic>> calculateMessages(CompletableFuture<Versioned<ITree>> tree) {
        return calculateMessages(calculate(tree));
    }

    private InterruptibleFuture<List<Diagnostic>> calculateMessages(InterruptibleFuture<IConstructor> summary) {
        return messages.replace(extractMessages(summary));
    }

    private InterruptibleFuture<IConstructor> calculate(CompletableFuture<Versioned<ITree>> tree) {
        return InterruptibleFuture.flatten(
            tree.thenApplyAsync(t -> calculator.apply(file, t.get()), exec),
            exec);
    }

    private InterruptibleFuture<List<Diagnostic>> extractMessages(InterruptibleFuture<IConstructor> summary) {
        return summary.thenApply(s -> {
            var sum = s.asWithKeywordParameters();
            if (sum.hasParameter("messages")) {
                return ((ISet)sum.getParameter("messages")).stream()
                    .map(d -> Diagnostics.translateDiagnostic((IConstructor)(((ITuple)d).get(1)), columns))
                    .collect(Collectors.toList());
            }
            return Collections.emptyList();
        });
    }

    public CompletableFuture<List<Either<String, MarkedString>>> getHover(Position cursor) {
        return hovers.thenCompose(h -> h.lookup(cursor).get());
    }

    public CompletableFuture<List<Diagnostic>> getMessages() {
        return messages.get();
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

@SuppressWarnings("deprecation")
interface Summary {
    @Nullable Supplier<InterruptibleFuture<List<Either<String, MarkedString>>>> getDocumentation(Position cursor);
    @Nullable Supplier<InterruptibleFuture<List<Location>>> getDefinitions(Position cursor);
    @Nullable Supplier<InterruptibleFuture<List<Location>>> getReferences(Position cursor);
    @Nullable Supplier<InterruptibleFuture<List<Location>>> getImplementations(Position cursor);
    @Nullable InterruptibleFuture<List<Diagnostic>> getMessages();
    void invalidate();

    static final Summary NULL_SUMMARY = new Summary() {
        @Override
        public @Nullable Supplier<InterruptibleFuture<List<Either<String, MarkedString>>>> getDocumentation(Position cursor) {
            return null;
        }
        @Override
        public @Nullable Supplier<InterruptibleFuture<List<Location>>> getDefinitions(Position cursor) {
            return null;
        }
        @Override
        public @Nullable Supplier<InterruptibleFuture<List<Location>>> getReferences(Position cursor) {
            return null;
        }
        @Override
        public @Nullable Supplier<InterruptibleFuture<List<Location>>> getImplementations(Position cursor) {
            return null;
        }
        @Override
        public @Nullable InterruptibleFuture<List<Diagnostic>> getMessages() {
            return null;
        }
        @Override
        public void invalidate() {
            // Nothing to invalidate
        }
    };
}

abstract class BaseSummaryFactory {
    protected final SummaryConfig config;
    protected final Executor exec;
    protected final ColumnMaps columns;
    protected final ILanguageContributions contrib;
    protected final ValueMapper valueMapper;

    protected BaseSummaryFactory(SummaryConfig config, Executor exec, ColumnMaps columns, ILanguageContributions contrib) {
        this.config = config;
        this.exec = exec;
        this.columns = columns;
        this.contrib = contrib;
        this.valueMapper = new ValueMapper(columns);
    }
}

@SuppressWarnings("deprecation")
class SummarizerSummaryFactory extends BaseSummaryFactory {
    private static final Logger logger = LogManager.getLogger(SummarizerSummaryFactory.class);

    public SummarizerSummaryFactory(SummaryConfig config, Executor exec, ColumnMaps columns, ILanguageContributions contrib) {
        super(config, exec, columns, contrib);
    }

    public Summary create(InterruptibleFuture<IConstructor> calculation) {
        return new SummarizerSummary(calculation);
    }

    protected <T> InterruptibleFuture<Lazy<IRangeMap<List<T>>>> mapCalculation(String logName,
        InterruptibleFuture<IConstructor> calculation, String kwField, Function<IValue, T> valueMapper) {

        logger.trace("{}: Mapping summary by getting {}", logName, kwField);
        return calculation
            .thenApply(IConstructor::asWithKeywordParameters)
            .thenApply(s ->
                Lazy.defer(() ->
                    translateRelation(logName, getKWFieldSet(s, kwField), valueMapper)));
    }

    private IRelation<ISet> getKWFieldSet(IWithKeywordParameters<? extends IConstructor> data, String name) {
        if (data.hasParameter(name)) {
            return ((ISet) data.getParameter(name)).asRelation();
        }
        return IRascalValueFactory.getInstance().set().asRelation();
    }

    private <T> IRangeMap<List<T>> translateRelation(String logName,
            IRelation<ISet> binaryRel, Function<IValue, T> mapValue) {

        logger.trace("{}: summary contain rel of size:{}", logName, binaryRel.asContainer().size());
        TreeMapLookup<List<T>> result = new TreeMapLookup<>();
        for (IValue v: binaryRel) {
            ITuple row = (ITuple)v;
            Range from = Locations.toRange((ISourceLocation)row.get(0), columns);
            T to = mapValue.apply(row.get(1));
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

    public class SummarizerSummary implements Summary {
        public final @Nullable InterruptibleFuture<Lazy<IRangeMap<List<Either<String, MarkedString>>>>> documentation;
        public final @Nullable InterruptibleFuture<Lazy<IRangeMap<List<Location>>>> definitions;
        public final @Nullable InterruptibleFuture<Lazy<IRangeMap<List<Location>>>> references;
        public final @Nullable InterruptibleFuture<Lazy<IRangeMap<List<Location>>>> implementations;
        public final InterruptibleFuture<List<Diagnostic>> messages;

        public SummarizerSummary(InterruptibleFuture<IConstructor> calculation) {
            this.documentation = config.providesDocumentation ?
                mapCalculation("documentation", calculation, "documentation", valueMapper::mapValueToString) : null;
            this.definitions = config.providesDefinitions ?
                mapCalculation("definitions", calculation, "definitions", valueMapper::mapValueToLocation) : null;
            this.references = config.providesReferences ?
                mapCalculation("references", calculation, "references", valueMapper::mapValueToLocation) : null;
            this.implementations = config.providesImplementations ?
                mapCalculation("implementations", calculation, "implementations", valueMapper::mapValueToLocation) : null;
            this.messages = extractMessages(calculation);
        }

        @Override
        public @Nullable Supplier<InterruptibleFuture<List<Either<String, MarkedString>>>> getDocumentation(Position cursor) {
            return documentation == null ? null : get(documentation, cursor);
        }

        @Override
        public @Nullable Supplier<InterruptibleFuture<List<Location>>> getDefinitions(Position cursor) {
            return definitions == null ? null : get(definitions, cursor);
        }

        @Override
        public @Nullable Supplier<InterruptibleFuture<List<Location>>> getReferences(Position cursor) {
            return references == null ? null : get(references, cursor);
        }

        @Override
        public @Nullable Supplier<InterruptibleFuture<List<Location>>> getImplementations(Position cursor) {
            return implementations == null ? null : get(implementations, cursor);
        }

        @Override
        public InterruptibleFuture<List<Diagnostic>> getMessages() {
            return messages;
        }

        @Override
        public void invalidate() {
            documentation.interrupt();
            definitions.interrupt();
            references.interrupt();
            implementations.interrupt();
            messages.interrupt();
        }

        private <T> Supplier<InterruptibleFuture<List<T>>> get(InterruptibleFuture<Lazy<IRangeMap<List<T>>>> result, Position cursor) {
            return () -> result
                .thenApplyAsync(Lazy::get, exec)
                .thenApply(l -> l.lookup(new Range(cursor, cursor)))
                .thenApply(r -> r == null ? Collections.emptyList() : r);
        }

        private InterruptibleFuture<List<Diagnostic>> extractMessages(InterruptibleFuture<IConstructor> summary) {
            return summary.thenApply(s -> {
                var sum = s.asWithKeywordParameters();
                if (sum.hasParameter("messages")) {
                    return ((ISet)sum.getParameter("messages")).stream()
                        .map(d -> Diagnostics.translateDiagnostic((IConstructor)(((ITuple)d).get(1)), columns))
                        .collect(Collectors.toList());
                }
                return Collections.emptyList();
            });
        }
    }
}

@FunctionalInterface
interface DedicatedLookupFunction {
    InterruptibleFuture<ISet> lookup(ISourceLocation file, ITree tree, ITree cursor);
}

@SuppressWarnings("deprecation")
class DedicatedLookupFunctionsSummaryFactory extends BaseSummaryFactory {
    private static final Logger logger = LogManager.getLogger(DedicatedLookupFunctionsSummaryFactory.class);

    public DedicatedLookupFunctionsSummaryFactory(SummaryConfig config, Executor exec, ColumnMaps columns, ILanguageContributions contrib) {
        super(config, exec, columns, contrib);
    }

    public Summary create(ISourceLocation file, CompletableFuture<Versioned<ITree>> tree) {
        return new Lookups(file, tree);
    }

    public class Lookups implements Summary {
        private final ISourceLocation file;
        private final CompletableFuture<Versioned<ITree>> tree;

        public Lookups(ISourceLocation file, CompletableFuture<Versioned<ITree>> tree) {
            this.file = file;
            this.tree = tree;
        }

        @Override
        public @Nullable Supplier<InterruptibleFuture<List<Either<String, MarkedString>>>> getDocumentation(Position cursor) {
            return config.providesDocumentation ? get(cursor, contrib::documentation, valueMapper::mapValueToString, "documentation") : null;
        }

        @Override
        public @Nullable Supplier<InterruptibleFuture<List<Location>>> getDefinitions(Position cursor) {
            return config.providesDefinitions ? get(cursor, contrib::definitions, valueMapper::mapValueToLocation, "definitions") : null;
        }

        @Override
        public @Nullable Supplier<InterruptibleFuture<List<Location>>> getReferences(Position cursor) {
            return config.providesReferences ? get(cursor, contrib::references, valueMapper::mapValueToLocation, "references") : null;
        }

        @Override
        public @Nullable Supplier<InterruptibleFuture<List<Location>>> getImplementations(Position cursor) {
            return config.providesImplementations ? get(cursor, contrib::implementations, valueMapper::mapValueToLocation, "implementations") : null;
        }

        @Override
        public InterruptibleFuture<List<Diagnostic>> getMessages() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void invalidate() {
            // Nothing to invalidate
        }

        private <T> Supplier<InterruptibleFuture<List<T>>> get(Position cursor,
                DedicatedLookupFunction function, Function<IValue, T> valueMapper, String logName) {

            return () -> {
                var result = tree
                        .thenApplyAsync(t -> {
                            var line = cursor.getLine() + 1;
                            var translatedOffset = columns.get(file).translateInverseColumn(line, cursor.getCharacter(), false);
                            var cursorTree = TreeAdapter.locateLexical(t.get(), line, translatedOffset);
                            if (cursorTree == null) {
                                logger.trace("{}: could not find substree at line {} and offset {}", logName, line, translatedOffset);
                                return InterruptibleFuture.completedFuture(IRascalValueFactory.getInstance().set());
                            }
                            logger.trace("{}: looked up cursor to: {}, now calling dedicated function", logName, TreeAdapter.yield(cursorTree));
                            var set = function.lookup(file, t.get(), cursorTree);
                            logger.trace("{}: dedicated returned: {}", logName, set);
                            return set;
                        }, exec);

                return InterruptibleFuture.flatten(result, exec)
                    .thenApply(s -> toList(s, valueMapper));
            };
        }

        private <T> List<T> toList(ISet s, Function<IValue, T> valueMapper) {
            if (s == null || s.isEmpty()) {
                return Collections.emptyList();
            }
            int size = s.size();
            if (size == 1) {
                var v = s.iterator().next();
                return Collections.singletonList(valueMapper.apply(v));
            }
            var list = new ArrayList<T>(size);
            for (IValue v : s) {
                list.add(valueMapper.apply(v));
            }
            return list;
        }
    }
}

@SuppressWarnings("deprecation")
class ValueMapper {
    private final ColumnMaps columns;

    public ValueMapper(ColumnMaps columns) {
        this.columns = columns;
    }

    public Location mapValueToLocation(IValue v) {
        return Locations.toLSPLocation((ISourceLocation)v, columns);
    }

    public Either<String, MarkedString> mapValueToString(IValue v) {
        return Either.forLeft(((IString) v).getValue());
    }
}
