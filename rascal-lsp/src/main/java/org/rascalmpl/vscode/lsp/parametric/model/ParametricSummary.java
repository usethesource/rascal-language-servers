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
import org.rascalmpl.vscode.lsp.parametric.ILanguageContributions;
import org.rascalmpl.vscode.lsp.parametric.ILanguageContributions.SingleShotFn;
import org.rascalmpl.vscode.lsp.parametric.ILanguageContributions.SummaryConfig;
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
 *   - `SummarizerSummary` is a summary that originates from a summarizer (i.e.,
 *     analyzer or builder). In this case, if available, information requested
 *     from the summary has already been pre-calculated by the summarizer (as a
 *     relation), so it only needs to be fetched (and translated to the proper
 *     format for LSP).
 *
 *   - `SingleShooterSummary` is a summary that originates from a single-shooter
 *     (i.e., documenter, definer, referrer, or implementer). In this case, if
 *     available, information requested from the summary is calculated
 *     on-the-fly by the single-shooter.
 */
@SuppressWarnings("deprecation") // For `MarkedString`
public interface ParametricSummary {

    // The following methods return `null` when the requested information isn't
    // available in this summary. In the case of `SummarizerSummary`, this
    // happens when the summarizer is configured with a falsy `provides...`
    // property for the requested information. In the case of
    // `SingleShooterSummary`, this happens when no single-shooter exists for
    // the requested information.
    @Nullable Supplier<InterruptibleFuture<List<Either<String, MarkedString>>>> getDocumentation(Position cursor);
    @Nullable Supplier<InterruptibleFuture<List<Location>>> getDefinitions(Position cursor);
    @Nullable Supplier<InterruptibleFuture<List<Location>>> getReferences(Position cursor);
    @Nullable Supplier<InterruptibleFuture<List<Location>>> getImplementations(Position cursor);

    InterruptibleFuture<List<Diagnostic>> getMessages();

    void invalidate();

    @FunctionalInterface // Just a type alias
    public static interface LookupFn<T> extends Function<ParametricSummary, @Nullable Supplier<InterruptibleFuture<List<T>>>> {}

    public static final ParametricSummary NULL = new ParametricSummary() {
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
        public InterruptibleFuture<List<Diagnostic>> getMessages() {
            return InterruptibleFuture.completedFuture(Collections.emptyList());
        }
        @Override
        public void invalidate() {
            // Nothing to invalidate
        }
    };

    public static InterruptibleFuture<List<Diagnostic>> getMessages(CompletableFuture<Versioned<ParametricSummary>> summary, Executor exec) {
        var messages = summary
            .thenApply(Versioned<ParametricSummary>::get)
            .thenApply(ParametricSummary::getMessages);
        return InterruptibleFuture.flatten(messages, exec);
    }
}

/**
 * The purpose of this class is to store global state that summaries need to
 * fulfil their responsibilities. It has two extensions, corresponding to the
 * two implementations of interface `ParametricSummary`: (1)
 * `SummarizerSummaryFactory` and (2) `SingleShooterSummaryFactory`.
 */
@SuppressWarnings("deprecation") // For `MarkedString`
abstract class ParametricSummaryFactory {
    public static final String DOCUMENTATION = "documentation";
    public static final String DEFINITIONS = "definitions";
    public static final String REFERENCES = "references";
    public static final String IMPLEMENTATIONS = "implementations";

    protected final SummaryConfig config;
    protected final Executor exec;
    protected final ColumnMaps columns;

    protected ParametricSummaryFactory(SummaryConfig config, Executor exec, ColumnMaps columns) {
        this.config = config;
        this.exec = exec;
        this.columns = columns;
    }

    public static Either<String, MarkedString> mapValueToString(IValue v) {
        return Either.forLeft(((IString) v).getValue());
    }
}

@SuppressWarnings("deprecation") // For `MarkedString`
class SummarizerSummaryFactory extends ParametricSummaryFactory {
    private static final Logger logger = LogManager.getLogger(SummarizerSummaryFactory.class);

    private final BiFunction<ISourceLocation, ITree, InterruptibleFuture<IConstructor>> calculator;

    public SummarizerSummaryFactory(SummaryConfig config, Executor exec, ColumnMaps columns,
            BiFunction<ISourceLocation, ITree, InterruptibleFuture<IConstructor>> calculator) {

        super(config, exec, columns);
        this.calculator = calculator;
    }

    public CompletableFuture<Versioned<ParametricSummary>> createSummary(
            ISourceLocation file, CompletableFuture<Versioned<ITree>> tree) {

        var calculation = calculate(file, tree);
        var summary = summarize(calculation);
        return tree
            .thenApply(Versioned::version)
            .thenApply(v -> new Versioned<>(v, summary));
    }

    private InterruptibleFuture<IConstructor> calculate(
            ISourceLocation file, CompletableFuture<Versioned<ITree>> tree) {

        logger.trace("Requesting summary calculation for: {}", file);
        return InterruptibleFuture.flatten(
            tree.thenApplyAsync(t -> calculator.apply(file, t.get()), exec), exec);
    }

    public ParametricSummary summarize(InterruptibleFuture<IConstructor> calculation) {
        return new SummarizerSummary(calculation);
    }

    public static CompletableFuture<Versioned<ParametricSummary>> newSummary(
            CompletableFuture<SummarizerSummaryFactory> factory,
            ISourceLocation file, CompletableFuture<Versioned<ITree>> tree) {

        return factory
            .thenApply(f -> f.createSummary(file, tree))
            .thenCompose(Function.identity());
    }

    public class SummarizerSummary implements ParametricSummary {
        private final @Nullable InterruptibleFuture<Lazy<IRangeMap<List<Either<String, MarkedString>>>>> documentation;
        private final @Nullable InterruptibleFuture<Lazy<IRangeMap<List<Location>>>> definitions;
        private final @Nullable InterruptibleFuture<Lazy<IRangeMap<List<Location>>>> references;
        private final @Nullable InterruptibleFuture<Lazy<IRangeMap<List<Location>>>> implementations;
        private final InterruptibleFuture<List<Diagnostic>> messages;

        public SummarizerSummary(InterruptibleFuture<IConstructor> calculation) {
            this.documentation = config.providesDocumentation ?
                mapCalculation(DOCUMENTATION, calculation, DOCUMENTATION, ParametricSummaryFactory::mapValueToString) : null;
            this.definitions = config.providesDefinitions ?
                mapCalculation(DEFINITIONS, calculation, DEFINITIONS, columns::mapValueToLocation) : null;
            this.references = config.providesReferences ?
                mapCalculation(REFERENCES, calculation, REFERENCES, columns::mapValueToLocation) : null;
            this.implementations = config.providesImplementations ?
                mapCalculation(IMPLEMENTATIONS, calculation, IMPLEMENTATIONS, columns::mapValueToLocation) : null;
            this.messages = extractMessages(calculation);
        }

        @Override
        public @Nullable Supplier<InterruptibleFuture<List<Either<String, MarkedString>>>> getDocumentation(Position cursor) {
            return get(documentation, cursor);
        }

        @Override
        public @Nullable Supplier<InterruptibleFuture<List<Location>>> getDefinitions(Position cursor) {
            return get(definitions, cursor);
        }

        @Override
        public @Nullable Supplier<InterruptibleFuture<List<Location>>> getReferences(Position cursor) {
            return get(references, cursor);
        }

        @Override
        public @Nullable Supplier<InterruptibleFuture<List<Location>>> getImplementations(Position cursor) {
            return get(implementations, cursor);
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

        private <T> @Nullable Supplier<InterruptibleFuture<List<T>>> get(
                @Nullable InterruptibleFuture<Lazy<IRangeMap<List<T>>>> result, Position cursor) {

            return result == null ? null : () -> result
                .thenApplyAsync(Lazy::get, exec)
                .thenApply(l -> l.lookup(new Range(cursor, cursor)))
                .thenApply(r -> r == null ? Collections.emptyList() : r);
        }
    }
}

@SuppressWarnings("deprecation") // For `MarkedString`
class SingleShooterSummaryFactory extends ParametricSummaryFactory {
    private static final Logger logger = LogManager.getLogger(SingleShooterSummaryFactory.class);

    private final ILanguageContributions contrib;

    public SingleShooterSummaryFactory(SummaryConfig config, Executor exec, ColumnMaps columns, ILanguageContributions contrib) {
        super(config, exec, columns);
        this.contrib = contrib;
    }

    public ParametricSummary createSummary(ISourceLocation file, Versioned<ITree> tree) {
        return new SingleShooterSummary(file, tree);
    }

    public class SingleShooterSummary implements ParametricSummary {
        private final ISourceLocation file;
        private final Versioned<ITree> tree;

        public SingleShooterSummary(ISourceLocation file, Versioned<ITree> tree) {
            this.file = file;
            this.tree = tree;
        }

        @Override
        public @Nullable Supplier<InterruptibleFuture<List<Either<String, MarkedString>>>> getDocumentation(Position cursor) {
            return config.providesDocumentation ? get(cursor, contrib::documentation, ParametricSummaryFactory::mapValueToString, DOCUMENTATION) : null;
        }

        @Override
        public @Nullable Supplier<InterruptibleFuture<List<Location>>> getDefinitions(Position cursor) {
            return config.providesDefinitions ? get(cursor, contrib::definitions, columns::mapValueToLocation, DEFINITIONS) : null;
        }

        @Override
        public @Nullable Supplier<InterruptibleFuture<List<Location>>> getReferences(Position cursor) {
            return config.providesReferences ? get(cursor, contrib::references, columns::mapValueToLocation, REFERENCES) : null;
        }

        @Override
        public @Nullable Supplier<InterruptibleFuture<List<Location>>> getImplementations(Position cursor) {
            return config.providesImplementations ? get(cursor, contrib::implementations, columns::mapValueToLocation, IMPLEMENTATIONS) : null;
        }

        @Override
        public InterruptibleFuture<List<Diagnostic>> getMessages() {
            return InterruptibleFuture.completedFuture(Collections.emptyList());
        }

        @Override
        public void invalidate() {
            // Nothing to invalidate
        }

        private <T> Supplier<InterruptibleFuture<List<T>>> get(Position cursor,
                SingleShotFn singleShotFn, Function<IValue, T> valueMapper, String logName) {

            return () -> {
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
                    set = singleShotFn.apply(file, tree.get(), cursorTree);
                }

                logger.trace("{}: dedicated returned: {}", logName, set);
                return set.thenApply(s -> toList(s, valueMapper));
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

