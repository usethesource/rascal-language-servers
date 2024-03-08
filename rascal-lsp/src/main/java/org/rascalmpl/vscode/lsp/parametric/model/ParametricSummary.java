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
import org.rascalmpl.vscode.lsp.parametric.ILanguageContributions;
import org.rascalmpl.vscode.lsp.parametric.ILanguageContributions.OndemandCalculator;
import org.rascalmpl.vscode.lsp.parametric.ILanguageContributions.ScheduledCalculator;
import org.rascalmpl.vscode.lsp.parametric.ILanguageContributions.SummaryConfig;
import org.rascalmpl.vscode.lsp.parametric.model.ParametricSummary.SummaryLookup;
import org.rascalmpl.vscode.lsp.parametric.model.RascalADTs.SummaryFields;
import org.rascalmpl.vscode.lsp.util.Diagnostics;
import org.rascalmpl.vscode.lsp.util.Lazy;
import org.rascalmpl.vscode.lsp.util.Versioned;
import org.rascalmpl.vscode.lsp.util.concurrent.InterruptibleFuture;
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

/**
 * The purpose of this interface is to provide a general abstraction for
 * `Position`-based look-ups of documentation, definitions, references, and
 * implementations, regardless of which component calculates the requested
 * information. There are two implementations:
 *
 *   - `ScheduledSummary` is a summary that originates from a scheduled
 *     summarizer (i.e., analyzer or builder). In this case, if available,
 *     information requested from the summary has already been pre-calculated by
 *     the summarizer (as a relation), so it only needs to be fetched (and
 *     translated to the proper format for LSP).
 *
 *   - `OndemandSummary` is a summary that originates from an on-demand
 *     summarizer (i.e., documenter, definer, referrer, or implementer). In this
 *     case, if available, information requested from the summary is calculated
 *     on-the-fly.
 */
public interface ParametricSummary {

    // The following methods return `null` when the requested information isn't
    // available in this summary. In the case of `ScheduledSummary`, this
    // happens when the summarizer is configured with a false `provides...`
    // property for the requested information. In the case of `OndemandSummary`,
    // this happens when no on-demand summarizer exists for the requested
    // information.
    @SuppressWarnings("deprecation") // For `MarkedString`
    @Nullable InterruptibleFuture<List<Either<String, MarkedString>>> getDocumentation(Position cursor);
    @Nullable InterruptibleFuture<List<Location>> getDefinitions(Position cursor);
    @Nullable InterruptibleFuture<List<Location>> getReferences(Position cursor);
    @Nullable InterruptibleFuture<List<Location>> getImplementations(Position cursor);

    InterruptibleFuture<List<Diagnostic>> getMessages();

    void invalidate();

    @FunctionalInterface // Just a type alias that abstracts a look-up function
    public static interface SummaryLookup<T> extends BiFunction<ParametricSummary, Position, @Nullable InterruptibleFuture<List<T>>> {}

    // The following methods make it more convenient to pass around a look-up
    // function *before* the actual summary to apply it to has been selected
    // (i.e., it later comes from the analyzer, builder, or an on-demand
    // summarizer).
    @SuppressWarnings("deprecation") // For `MarkedString`
    public static @Nullable InterruptibleFuture<List<Either<String, MarkedString>>> documentation(ParametricSummary summary, Position position) {
        return summary.getDocumentation(position);
    }
    public static @Nullable InterruptibleFuture<List<Location>> definitions(ParametricSummary summary, Position position) {
        return summary.getDefinitions(position);
    }
    public static @Nullable InterruptibleFuture<List<Location>> references(ParametricSummary summary, Position position) {
        return summary.getReferences(position);
    }
    public static @Nullable InterruptibleFuture<List<Location>> implementations(ParametricSummary summary, Position position) {
        return summary.getImplementations(position);
    }

    public static final ParametricSummary NULL = new NullSummary();

    public static InterruptibleFuture<List<Diagnostic>> getMessages(CompletableFuture<Versioned<ParametricSummary>> summary, Executor exec) {
        var messages = summary
            .thenApply(Versioned<ParametricSummary>::get)
            .thenApply(ParametricSummary::getMessages);
        return InterruptibleFuture.flatten(messages, exec);
    }
}

class NullSummary implements ParametricSummary {
    @Override
    @SuppressWarnings("deprecation") // For `MarkedString`
    public @Nullable InterruptibleFuture<List<Either<String, MarkedString>>> getDocumentation(Position cursor) {
        return null;
    }

    @Override
    public @Nullable InterruptibleFuture<List<Location>> getDefinitions(Position cursor) {
        return null;
    }

    @Override
    public @Nullable InterruptibleFuture<List<Location>> getReferences(Position cursor) {
        return null;
    }

    @Override
    public @Nullable InterruptibleFuture<List<Location>> getImplementations(Position cursor) {
        return null;
    }

    @Override
    public InterruptibleFuture<List<Diagnostic>> getMessages() {
        return InterruptibleFuture.completedFuture(Collections.emptyList());
    }

    @Override
    public void invalidate() {
        // Nothing to invalidate
    }
}

/**
 * The purpose of this class is to store global state that summaries need to
 * fulfil their responsibilities. It has two extensions, corresponding to the
 * two implementations of interface `ParametricSummary`: (1)
 * `ScheduledSummaryFactory` and (2) `OndemandSummaryFactory`.
 */
abstract class ParametricSummaryFactory {
    protected final SummaryConfig config;
    protected final Executor exec;
    protected final ColumnMaps columns;

    protected ParametricSummaryFactory(SummaryConfig config, Executor exec, ColumnMaps columns) {
        this.config = config;
        this.exec = exec;
        this.columns = columns;
    }

    @SuppressWarnings("deprecation") // For `MarkedString`
    protected static Either<String, MarkedString> mapValueToString(IValue v) {
        return Either.forLeft(((IString) v).getValue());
    }

    protected static Function<IValue, Location> locationMapper(ColumnMaps cm) {
        return v -> Locations.mapValueToLocation(v, cm);
    }
}

class ScheduledSummaryFactory extends ParametricSummaryFactory {
    private static final Logger logger = LogManager.getLogger(ScheduledSummaryFactory.class);

    private final ScheduledCalculator calculator;

    public ScheduledSummaryFactory(SummaryConfig config, Executor exec, ColumnMaps columns, ScheduledCalculator calculator) {
        super(config, exec, columns);
        this.calculator = calculator;
    }

    public CompletableFuture<Versioned<ParametricSummary>> createMessagesOnlySummary(
            ISourceLocation file, CompletableFuture<Versioned<ITree>> tree) {
        return createSummary(file, tree, MessagesOnlyScheduledSummary::new);
    }

    public CompletableFuture<Versioned<ParametricSummary>> createFullSummary(
            ISourceLocation file, CompletableFuture<Versioned<ITree>> tree) {
        return createSummary(file, tree, FullScheduledSummary::new);
    }

    private CompletableFuture<Versioned<ParametricSummary>> createSummary(
            ISourceLocation file, CompletableFuture<Versioned<ITree>> tree,
            Function<InterruptibleFuture<IConstructor>, ParametricSummary> constructor) {

        return tree.thenApplyAsync(t -> {
            logger.trace("Requesting summary calculation for: {}", file);
            var calculation = calculator.apply(file, t.get());
            return new Versioned<>(t.version(), constructor.apply(calculation));
        }, exec);
    }

    public class MessagesOnlyScheduledSummary extends NullSummary {
        private final InterruptibleFuture<Lazy<List<Diagnostic>>> messages;

        public MessagesOnlyScheduledSummary(InterruptibleFuture<IConstructor> calculation) {
            this.messages = extractMessages(calculation);
        }

        @Override
        public InterruptibleFuture<List<Diagnostic>> getMessages() {
            return messages.thenApply(Lazy::get);
        }

        @Override
        public void invalidate() {
            messages.interrupt();
        }

        private InterruptibleFuture<Lazy<List<Diagnostic>>> extractMessages(InterruptibleFuture<IConstructor> summary) {
            return summary.thenApply(s -> Lazy.defer(() -> {
                var sum = s.asWithKeywordParameters();
                if (sum.hasParameter("messages")) {
                    return ((ISet)sum.getParameter("messages")).stream()
                        .map(d -> Diagnostics.translateDiagnostic((IConstructor)(((ITuple)d).get(1)), columns))
                        .collect(Collectors.toList());
                }
                return Collections.emptyList();
            }));
        }
    }


    public class FullScheduledSummary extends MessagesOnlyScheduledSummary {
        @SuppressWarnings("deprecation") // For `MarkedString`
        private final @Nullable InterruptibleFuture<Lazy<IRangeMap<List<Either<String, MarkedString>>>>> documentation;
        private final @Nullable InterruptibleFuture<Lazy<IRangeMap<List<Location>>>> definitions;
        private final @Nullable InterruptibleFuture<Lazy<IRangeMap<List<Location>>>> references;
        private final @Nullable InterruptibleFuture<Lazy<IRangeMap<List<Location>>>> implementations;

        public FullScheduledSummary(InterruptibleFuture<IConstructor> calculation) {
            super(calculation);
            this.documentation = config.providesDocumentation ?
                mapCalculation(SummaryFields.DOCUMENTATION, calculation, SummaryFields.DOCUMENTATION, ParametricSummaryFactory::mapValueToString) : null;
            this.definitions = config.providesDefinitions ?
                mapCalculation(SummaryFields.DEFINITIONS, calculation, SummaryFields.DEFINITIONS, locationMapper(columns)) : null;
            this.references = config.providesReferences ?
                mapCalculation(SummaryFields.REFERENCES, calculation, SummaryFields.REFERENCES, locationMapper(columns)) : null;
            this.implementations = config.providesImplementations ?
                mapCalculation(SummaryFields.IMPLEMENTATIONS, calculation, SummaryFields.IMPLEMENTATIONS, locationMapper(columns)) : null;
        }

        @Override
        @SuppressWarnings("deprecation") // For `MarkedString`
        public @Nullable InterruptibleFuture<List<Either<String, MarkedString>>> getDocumentation(Position cursor) {
            return get(documentation, cursor);
        }

        @Override
        public @Nullable InterruptibleFuture<List<Location>> getDefinitions(Position cursor) {
            return get(definitions, cursor);
        }

        @Override
        public @Nullable InterruptibleFuture<List<Location>> getReferences(Position cursor) {
            return get(references, cursor);
        }

        @Override
        public @Nullable InterruptibleFuture<List<Location>> getImplementations(Position cursor) {
            return get(implementations, cursor);
        }

        @Override
        public void invalidate() {
            super.invalidate();
            documentation.interrupt();
            definitions.interrupt();
            references.interrupt();
            implementations.interrupt();
        }

        private <T> InterruptibleFuture<Lazy<IRangeMap<List<T>>>> mapCalculation(String logName,
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

        @SuppressWarnings("java:S3358") // Nested ternary looks fine
        private <T> @Nullable InterruptibleFuture<List<T>> get(
                @Nullable InterruptibleFuture<Lazy<IRangeMap<List<T>>>> result, Position cursor) {

            return result == null ? null : result
                .thenApplyAsync(Lazy::get, exec)
                .thenApply(l -> l.lookup(new Range(cursor, cursor)))
                .thenApply(r -> r == null ? Collections.emptyList() : r);
        }
    }
}

/**
 * The purpose of this class is to offer a similar interface for the
 * construction of on-demand summaries (originating from
 * documenter/definer/referrer/implementer) as for the construction of scheduled
 * summaries (originating from analyser/builder). To achieve this, it extends
 * the common superclass `ParametricSummaryFactory`, which stores global state
 * that both kinds of summaries need to fulfil their responsibilities.
 *
 * From the outside, an on-demand summary looks just like any other scheduled
 * summary: it's an abstraction through which `Position`-based information can
 * be looked up. On the inside, however, on-demand summaries work quite
 * differently from scheduled summaries. Conceptually, the idea is that a
 * documenter, definer, referrer, and implementer "sit" inside the factory,
 * waiting for on-demand requests for information:
 *
 *  1. First, when such a request happens, a summary is to be created via the
 *     factory as an interface for the actual look-up.
 *  2. Next, when such a look-up happens, the requested information is
 *     calculated on-the-fly via the summary by the corresponding documenter,
 *     definer, referrer, or implementer that sits inside the factory.
 *
 * For convenience, method `createSummaryThenLookup` combines these two steps.
 *
 * This design enables reuse of code and concepts common to scheduled summaries
 * and on-demand summaries.
 */
class OndemandSummaryFactory extends ParametricSummaryFactory {
    private static final Logger logger = LogManager.getLogger(OndemandSummaryFactory.class);

    private final ILanguageContributions contrib;

    public OndemandSummaryFactory(SummaryConfig config, Executor exec, ColumnMaps columns, ILanguageContributions contrib) {
        super(config, exec, columns);
        this.contrib = contrib;
    }

    public ParametricSummary createSummary(ISourceLocation file, Versioned<ITree> tree, Position cursor) {
        return new OndemandSummary(file, tree, cursor);
    }

    public <T> @Nullable InterruptibleFuture<List<T>> createSummaryThenLookup(
            ISourceLocation file, Versioned<ITree> tree, Position cursor,
            SummaryLookup<T> lookup) {

        return lookup.apply(new OndemandSummary(file, tree, cursor), cursor);
    }

    public class OndemandSummary implements ParametricSummary {
        private final ISourceLocation file;
        private final Versioned<ITree> tree;
        private final Position cursor;

        public OndemandSummary(ISourceLocation file, Versioned<ITree> tree, Position cursor) {
            this.file = file;
            this.tree = tree;
            this.cursor = cursor;
        }

        @Override
        @SuppressWarnings("deprecation") // For `MarkedString`
        public @Nullable InterruptibleFuture<List<Either<String, MarkedString>>> getDocumentation(Position cursor) {
            return get(config.providesDocumentation, cursor, contrib::documentation, ParametricSummaryFactory::mapValueToString, SummaryFields.DOCUMENTATION);
        }

        @Override
        public @Nullable InterruptibleFuture<List<Location>> getDefinitions(Position cursor) {
            return get(config.providesDefinitions, cursor, contrib::definitions, locationMapper(columns), SummaryFields.DEFINITIONS);
        }

        @Override
        public @Nullable InterruptibleFuture<List<Location>> getReferences(Position cursor) {
            return get(config.providesReferences, cursor, contrib::references, locationMapper(columns), SummaryFields.REFERENCES);
        }

        @Override
        public @Nullable InterruptibleFuture<List<Location>> getImplementations(Position cursor) {
            return get(config.providesImplementations, cursor, contrib::implementations, locationMapper(columns), SummaryFields.IMPLEMENTATIONS);
        }

        @Override
        public InterruptibleFuture<List<Diagnostic>> getMessages() {
            return InterruptibleFuture.completedFuture(Collections.emptyList());
        }

        @Override
        public void invalidate() {
            // Nothing to invalidate
        }

        private <T> @Nullable InterruptibleFuture<List<T>> get(boolean provides, Position cursor,
                OndemandCalculator calculator, Function<IValue, T> valueMapper, String logName) {

            if (!provides) {
                return null;
            }

            // To ensure that this summary can't be misused if it's accidentally
            // leaked elsewhere, this summary provides information only if the
            // cursor is *identical* (not just equal) to the cursor when this
            // summary was created.
            if (this.cursor != cursor) {
                logger.trace("{}: unexpected use of an on-demand summary (cursor at creation time != cursor at usage time)", logName);
                return null;
            }

            var line = cursor.getLine() + 1;
            var translatedOffset = columns.get(file).translateInverseColumn(line, cursor.getCharacter(), false);
            var cursorTree = TreeAdapter.locateLexical(tree.get(), line, translatedOffset);

            InterruptibleFuture<ISet> set = null;
            if (cursorTree == null) {
                logger.trace("{}: could not find substree at line {} and offset {}", logName, line, translatedOffset);
                set = InterruptibleFuture.completedFuture(IRascalValueFactory.getInstance().set());
            } else {
                var yielded = TreeAdapter.yield(cursorTree);
                logger.trace("{}: looked up cursor to: {}, now calling dedicated function", logName, yielded);
                set = calculator.apply(file, tree.get(), cursorTree);
            }

            logger.trace("{}: dedicated returned: {}", logName, set);
            return set.thenApply(s -> toList(s, valueMapper));
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

