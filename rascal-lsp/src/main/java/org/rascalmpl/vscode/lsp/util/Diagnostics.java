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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.rascalmpl.exceptions.RuntimeExceptionFactory;
import org.rascalmpl.exceptions.Throw;
import org.rascalmpl.parser.gtd.exception.ParseError;
import org.rascalmpl.values.ValueFactoryFactory;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.values.parsetrees.TreeAdapter;
import org.rascalmpl.vscode.lsp.util.locations.ColumnMaps;
import org.rascalmpl.vscode.lsp.util.locations.Locations;
import io.usethesource.vallang.ICollection;
import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IValueFactory;

public class Diagnostics {
    private static final String PARSER_DIAGNOSTICS_SOURCE = "parser";
    private static final String PARSE_ERROR_MESSAGE = "The parser couldn't fully understand this code.";

    private static final Logger logger = LogManager.getLogger(Diagnostics.class);
    private static final Map<String, DiagnosticSeverity> severityMap;

    static {
        severityMap = new HashMap<>();
        severityMap.put("error", DiagnosticSeverity.Error);
        severityMap.put("warning", DiagnosticSeverity.Warning);
        severityMap.put("info", DiagnosticSeverity.Information);
    }

    private Diagnostics() {/* hide implicit public constructor */ }

    public static <K, V> Map<K, List<V>> groupByKey(Stream<Entry<K, V>> diagnostics) {
        return diagnostics.collect(
            Collectors.groupingBy(Entry::getKey,
                Collectors.mapping(Entry::getValue, Collectors.toCollection(ArrayList::new))));
    }

    /**
     * Template for a diagnostic, to be instantiated using column maps. This
     * interface is equivalent to: {@code Function<ColumnMaps, Diagnostic>}.
     */
    @FunctionalInterface
    public static interface Template {
        public Diagnostic instantiate(ColumnMaps columns);
    }

    public static Template generateParseErrorDiagnostic(Throwable t) {
        if (t instanceof ParseError) {
            return generateParseErrorDiagnostic((ParseError) t);
        }

        if (t instanceof Throw) {
            IValue e = ((Throw) t).getException();
            if (e instanceof IConstructor) {
                IConstructor error = (IConstructor) e;
                if (error.getName().equals(RuntimeExceptionFactory.ParseError.getName())) {
                    ISourceLocation loc = (ISourceLocation) error.get(0);
                    return cm -> new Diagnostic(Locations.toRange(loc, cm), PARSE_ERROR_MESSAGE, DiagnosticSeverity.Error,
                            PARSER_DIAGNOSTICS_SOURCE);
                }
            }
        }

        logger.error("Parsing crashed", t);
        return cm -> new Diagnostic(new Range(new Position(0,0), new Position(0,1)),
                        "Parsing failed: " + t.getMessage(),
                        DiagnosticSeverity.Error,
                        "parser");
    }

    private static Template generateParseErrorDiagnostic(ParseError e) {
        return cm -> new Diagnostic(toRange(e, cm), PARSE_ERROR_MESSAGE, DiagnosticSeverity.Error, PARSER_DIAGNOSTICS_SOURCE);
    }

    public static List<Template> generateParseErrorDiagnostics(ITree errorTree) {
        final IValueFactory factory = ValueFactoryFactory.getValueFactory();

        final IList args = TreeAdapter.getArgs(errorTree);
        final ITree skipped = (ITree) args.get(args.size() - 1);

        // spans the error tree from the start and also surrounds the skipped part
        final ISourceLocation completeErrorTreeLoc = TreeAdapter.getLocation(errorTree);

        final ISourceLocation exactErrorLocation = (ISourceLocation) errorTree.asWithKeywordParameters().getParameter("parseError");

        // spans only the skipped part
        final ISourceLocation skippedLoc = TreeAdapter.getLocation(skipped);

        // points at where we had to start skipping. This is typically close to error
        // location as if no error recovery was done, but not exactly due to a little bit of backtracking
        // at the leaves of the parsing stack.
        final ISourceLocation stuckLoc = factory.sourceLocation(
            skippedLoc.top(),
            skippedLoc.getOffset(),
            1,
            skippedLoc.getBeginLine(),
            skippedLoc.getBeginLine(),
            skippedLoc.getBeginColumn(),
            skippedLoc.getBeginColumn() + 1);

        // this spans the recognized prefix until the skipped part
        final ISourceLocation prefixLoc = factory.sourceLocation(
                completeErrorTreeLoc.top(),
                completeErrorTreeLoc.getOffset(),
                skippedLoc.getOffset() - completeErrorTreeLoc.getOffset(),
                completeErrorTreeLoc.getBeginLine(),
                skippedLoc.getBeginLine(),
                completeErrorTreeLoc.getBeginColumn(),
                skippedLoc.getBeginColumn()
        );

        final List<Template> diagnostics = new ArrayList<>();

        diagnostics.add(cm -> {
            final var d = new Diagnostic(
                // stuckloc is here for backward compatibility (before parseError was registered on an error tree)
                toRange(exactErrorLocation != null ? exactErrorLocation : stuckLoc, cm),
                PARSE_ERROR_MESSAGE,
                DiagnosticSeverity.Error,
                PARSER_DIAGNOSTICS_SOURCE);

            final List<DiagnosticRelatedInformation> related = new ArrayList<>();

            related.add(related(cm, stuckLoc, "It is likely something is extra or missing here, or around this position."));
            related.add(related(cm, skippedLoc, "This part was skipped to recover and continue parsing."));
            related.add(related(cm, prefixLoc, "This part was still partially recognized."));

            d.setRelatedInformation(related);

            return d;
        });

        return diagnostics;
    }

    private static DiagnosticRelatedInformation related(ColumnMaps cm, ISourceLocation loc, String message) {
        return new DiagnosticRelatedInformation(Locations.toLSPLocation(loc, cm), message);
    }

    private static void storeFixCommands(IConstructor d, Diagnostic result) {
        // Here we attach quick-fix commands to every Diagnostic, if present.
        // Later when codeActions are requested, the LSP client sends selected
        // messages back to us, in which we can find these commands and send
        // them right back in response to the codeActions request.

        if (d.asWithKeywordParameters().hasParameter("fixes")) {
            // setData is meant exactly for this!
            result.setData(d.asWithKeywordParameters().getParameter("fixes").toString());
        }
    }

    public static Diagnostic translateDiagnostic(IConstructor d, ColumnMaps cm) {
        return translateDiagnostic(d, Locations.toRange(getMessageLocation(d), cm), cm);
    }

    public static Diagnostic translateDiagnostic(IConstructor d, Range range, ColumnMaps otherFiles) {
        Diagnostic result = new Diagnostic();
        result.setSeverity(severityMap.get(d.getName()));
        result.setMessage(getMessageString(d));
        result.setRange(range);


        if (d.asWithKeywordParameters().hasParameter("causes")) {
            result.setRelatedInformation(
                ((IList) d.asWithKeywordParameters().getParameter("causes")).stream()
                .map(IConstructor.class::cast)
                .map(c -> new DiagnosticRelatedInformation(
                    Locations.toLSPLocation(getMessageLocation(d), otherFiles.get(getMessageLocation(d))),
                    getMessageString(c)))
                .collect(Collectors.toList())
            );
        }

        storeFixCommands(d, result);
        return result;
    }

    private static Range toRange(ParseError pe, ColumnMaps cm) {
        return toRange(pe.getLocation(), cm);
    }

    private static Range toRange(ISourceLocation loc, ColumnMaps cm) {
        if (loc.getBeginLine() == loc.getEndLine() && loc.getBeginColumn() == loc.getEndColumn()) {
            // zero width parse error is not something LSP likes, so we make it one char wider
            loc = ValueFactoryFactory.getValueFactory().sourceLocation(loc,
                loc.getOffset(), loc.getLength() + 1,
                loc.getBeginLine(), loc.getEndLine(),
                loc.getBeginColumn(), loc.getEndColumn() + 1);
        }
        return Locations.toRange(loc, cm);
    }

    public static Map<ISourceLocation, List<Diagnostic>> translateMessages(Map<ISourceLocation, ISet> messagesPerModule, ColumnMaps cm) {
        return messagesPerModule.entrySet().stream()
            .filter(kv -> isValidLocation(kv.getKey(), kv.getValue()))
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                kv -> translateDiagnostics(kv.getValue(), cm)
            ));
    }

    private static List<Diagnostic> translateDiagnostics(ICollection<?> messages, ColumnMaps cm) {
        return messages.stream()
            .filter(IConstructor.class::isInstance)
            .map(IConstructor.class::cast)
            .map(d -> translateDiagnostic(d, cm))
            .collect(Collectors.toList());
    }

    public static Map<ISourceLocation, List<Diagnostic>> translateMessages(ICollection<?> messages, ColumnMaps cm) {
        return messages.stream()
            .filter(IConstructor.class::isInstance)
            .map(IConstructor.class::cast)
            .filter(Diagnostics::hasValidLocation)
            .map(d -> Pair.of(getMessageLocation(d), translateDiagnostic(d, cm)))
            .collect(Collectors.groupingBy(Pair::getLeft, Collectors.mapping(Pair::getRight, Collectors.toList())));
    }

    private static ISourceLocation getMessageLocation(IConstructor message) {
        return Locations.toClientLocation(((ISourceLocation) message.get("at")));
    }

    private static String getMessageString(IConstructor msg) {
        return ((IString) msg.get("msg")).getValue();
    }

    private static boolean hasValidLocation(IConstructor d) {
        return isValidLocation(getMessageLocation(d), d);
    }

    private static boolean isValidLocation( ISourceLocation loc, IValue m) {
        if (loc == null || loc.getScheme().equals("unknown")) {
            logger.trace("Dropping diagnostic due to incorrect location on message: {}", m);
            return false;
        }
        if (loc.getPath().endsWith(".rsc")) {
            return true;
        }
        if (loc.getPath().endsWith("/RASCAL.MF")) {
            return true;
        }
        if (loc.getPath().endsWith("/pom.xml")) {
            return true;
        }
        logger.error("Filtering diagnostic as it's an unsupported file to report diagnostics on: {}", m);
        return false;
    }
}
