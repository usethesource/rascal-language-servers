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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.rascalmpl.parser.gtd.exception.ParseError;
import org.rascalmpl.values.ValueFactoryFactory;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.values.parsetrees.TreeAdapter;
import org.rascalmpl.vscode.lsp.IBaseTextDocumentService;
import org.rascalmpl.vscode.lsp.util.locations.ColumnMaps;
import org.rascalmpl.vscode.lsp.util.locations.LineColumnOffsetMap;
import org.rascalmpl.vscode.lsp.util.locations.Locations;
import io.usethesource.vallang.ICollection;
import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IValueFactory;

public class Diagnostics {
    private static final Logger logger = LogManager.getLogger(Diagnostics.class);
    private static final Map<String, DiagnosticSeverity> severityMap;

    /**
     * You can use the following environment variables to control highlighting of recovered errors:
     * - RASCAL_EDITOR_ERROR_LOCATION_HIGHLIGHT
     * - RASCAL_EDITOR_ERROR_WHOLE_TREE_HIGHLIGHT
     * - RASCAL_EDITOR_ERROR_PREFIX_HIGHLIGHT
     * - RASCAL_EDITOR_ERROR_PREFIX_START_HIGHLIGHT
     * - RASCAL_EDITOR_ERROR_SKIPPED_HIGHLIGHT
     *
     * Each of these variables can be set to one of the following values:
     * - none
     * - error
     * - warning
     * - info
     * - hint
     *
     * Note that the "hint" severity only highlights a single character.
     */
    private static class ErrorHighlightConfig {
        final DiagnosticSeverity errorLocationHighlight;
        final DiagnosticSeverity errorWholeTreeHighlight;
        final DiagnosticSeverity errorPrefixHighlight;
        final DiagnosticSeverity errorPrefixStartHighlight;
        final DiagnosticSeverity errorSkippedHighlight;

        private DiagnosticSeverity initErrorHighlightSeverity(String name, DiagnosticSeverity defaultValue) {
            String spec = System.getenv("RASCAL_EDITOR_ERROR_" + name + "_HIGHLIGHT");
            if (spec != null) {
                switch (spec.toUpperCase()) {
                    case "ERROR":
                        return DiagnosticSeverity.Error;
                    case "WARNING":
                        return DiagnosticSeverity.Warning;
                    case "INFO":
                        return DiagnosticSeverity.Information;
                    case "HINT":
                        return DiagnosticSeverity.Hint;
                    case "NONE":
                        return null;
                }
            }

            return defaultValue;
        }

        // Configure error recovery highlighting using environment variables where available
        ErrorHighlightConfig() {
            errorLocationHighlight = initErrorHighlightSeverity("LOCATION", DiagnosticSeverity.Error);
            errorWholeTreeHighlight = initErrorHighlightSeverity("WHOLE_TREE", null);
            errorPrefixHighlight = initErrorHighlightSeverity("PREFIX", null);
            errorPrefixStartHighlight = initErrorHighlightSeverity("PREFIX_START", null);
            errorSkippedHighlight = initErrorHighlightSeverity("SKIPPED", null);

            System.err.println("Rascal editor error highlighting configuration: " + this);
        }

        @Override
        public String toString() {
            return "ErrorHighlightConfig{" +
                "errorLocationHighlight=" + errorLocationHighlight +
                ", errorWholeTreeHighlight=" + errorWholeTreeHighlight +
                ", errorPrefixHighlight=" + errorPrefixHighlight +
                ", errorPrefixStartHighlight=" + errorPrefixStartHighlight +
                ", errorSkippedHighlight=" + errorSkippedHighlight +
                '}';
        }
    }

    private static final ErrorHighlightConfig errorHighlightConfig = new ErrorHighlightConfig();

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

    public static Template generateParseErrorDiagnostic(ParseError e) {
        return cm -> new Diagnostic(toRange(e, cm), e.getMessage(), DiagnosticSeverity.Error, "parser");
    }

    public static List<Template> generateParseErrorDiagnostics(ITree errorTree) {
        IValueFactory factory = ValueFactoryFactory.getValueFactory();

        IList args = TreeAdapter.getArgs(errorTree);
        ITree skipped = (ITree) args.get(args.size()-1);

        ISourceLocation errorTreeLoc = TreeAdapter.getLocation(errorTree);
        ISourceLocation skippedLoc = TreeAdapter.getLocation(skipped);

        List<Template> diagnostics = new ArrayList<>();

        // Highlight selected parts of the error tree
        if (errorHighlightConfig.errorLocationHighlight != null) {
            // Highligth just the error location
            ISourceLocation errorLoc = factory.sourceLocation(skippedLoc,
                    skippedLoc.getOffset(), 1,
                    skippedLoc.getBeginLine(), skippedLoc.getBeginLine(),
                    skippedLoc.getBeginColumn(), skippedLoc.getBeginColumn() + 1);
            diagnostics.add(cm -> new Diagnostic(toRange(errorLoc, cm), "Recovered parse error location",
                    errorHighlightConfig.errorLocationHighlight, "parser"));
        }

        if (errorHighlightConfig.errorWholeTreeHighlight != null) {
            // Highlight the whole error tree
            diagnostics.add(cm -> new Diagnostic(toRange(errorTreeLoc, cm), "Recovered parse error",
                    errorHighlightConfig.errorWholeTreeHighlight, "parser"));
        }

        if (errorHighlightConfig.errorPrefixHighlight != null) {
            // Highlight the whole recognized prefix
            int prefixLength = skippedLoc.getOffset()-errorTreeLoc.getOffset();
            if (prefixLength > 0) {
                ISourceLocation prefixLoc = factory.sourceLocation(errorTreeLoc,
                        errorTreeLoc.getOffset(), skippedLoc.getOffset()-errorTreeLoc.getOffset(),
                        errorTreeLoc.getBeginLine(), skippedLoc.getBeginLine(),
                        errorTreeLoc.getBeginColumn(), skippedLoc.getBeginColumn());
                diagnostics.add(cm -> new Diagnostic(toRange(prefixLoc, cm), "Recovered parse error prefix",
                        errorHighlightConfig.errorPrefixHighlight, "parser"));
            }
        }

        if (errorHighlightConfig.errorPrefixStartHighlight != null) {
            // Highlight just the start of the recognized prefix
            int prefixLength = skippedLoc.getOffset() - errorTreeLoc.getOffset();
            if (prefixLength > 0) {
                ISourceLocation prefixLoc = factory.sourceLocation(errorTreeLoc,
                        errorTreeLoc.getOffset(), 1,
                        errorTreeLoc.getBeginLine(), errorTreeLoc.getBeginLine(),
                        errorTreeLoc.getBeginColumn(), errorTreeLoc.getBeginColumn() + 1);
                diagnostics.add(cm -> new Diagnostic(toRange(prefixLoc, cm), "Start of unrecognized construct",
                        errorHighlightConfig.errorPrefixStartHighlight, "parser"));
            }
        }

        if (errorHighlightConfig.errorSkippedHighlight != null && skippedLoc.getLength() > 0) {
            // Highlight the skipped part
            diagnostics.add(cm -> new Diagnostic(toRange(skippedLoc, cm), "Recovered parse error skipped",
                errorHighlightConfig.errorSkippedHighlight, "parser"));
        }

        return diagnostics;
    }

    public static Diagnostic translateErrorRecoveryDiagnostic(ITree errorTree, ColumnMaps cm) {
        IList args = TreeAdapter.getArgs(errorTree);
        ITree skipped = (ITree) args.get(args.size()-1);
        return new Diagnostic(toRange(skipped, cm), "Parse error (recoverable)", DiagnosticSeverity.Error, "parser");
    }

    public static Diagnostic translateRascalParseError(IValue e, ColumnMaps cm) {
        if (e instanceof IConstructor) {
            IConstructor error = (IConstructor) e;
            if (error.getName().equals("ParseError")) {
                ISourceLocation loc = (ISourceLocation) error.get(0);
                return new Diagnostic(Locations.toRange(loc, cm), "parse error", DiagnosticSeverity.Error, "parser");
            }
            else {
                return new Diagnostic(new Range(new Position(0, 0), new Position(0,0)), "Unknown error : " + e.toString());
            }
        }
        else {
            throw new IllegalArgumentException(e.toString());
        }
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
        return translateDiagnostic(d, Locations.toRange(getMessageLocation(d), cm));
    }

    public static Diagnostic translateDiagnostic(IConstructor d, LineColumnOffsetMap cm) {
        return translateDiagnostic(d, Locations.toRange(getMessageLocation(d), cm));
    }

    public static Diagnostic translateDiagnostic(IConstructor d, Range range) {
        Diagnostic result = new Diagnostic();
        result.setSeverity(severityMap.get(d.getName()));
        result.setMessage(((IString) d.get("msg")).getValue());
        result.setRange(range);

        storeFixCommands(d, result);
        return result;
    }

    private static Range toRange(ITree t, ColumnMaps cm) {
        return toRange(TreeAdapter.getLocation(t), cm);
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

    public static List<Diagnostic> translateDiagnostics(ISourceLocation file, ICollection<?> messages, ColumnMaps cm) {
        return messages.stream()
            .filter(IConstructor.class::isInstance)
            .map(IConstructor.class::cast)
            .filter(d -> Diagnostics.hasValidLocation(d, file))
            .map(d -> translateDiagnostic(d, cm))
            .collect(Collectors.toList());
    }

    /**
     * Indexes all messages per file and translates them to LSP Diagnostic representation
     * @param messages    as in the `Message` standard library module
     * @param docService  needed to convert column positions
     * @return an ordered map of Diagnostics
     */
    public static Map<ISourceLocation, List<Diagnostic>> translateMessages(IList messages, IBaseTextDocumentService docService) {
        Map<ISourceLocation, List<Diagnostic>> results = new HashMap<>();

        for (IValue elem : messages) {
            IConstructor message = (IConstructor) elem;
            ISourceLocation file = getMessageLocation(message).top();
            Diagnostic d = translateDiagnostic(message, docService.getColumnMap(file));

            List<Diagnostic> lst = results.computeIfAbsent(file, l -> new LinkedList<>());
            lst.add(d);
        }

        return results;
    }

    private static ISourceLocation getMessageLocation(IConstructor message) {
        return Locations.toClientLocation(((ISourceLocation) message.get("at")));
    }

    private static boolean hasValidLocation(IConstructor d, ISourceLocation file) {
        ISourceLocation loc = getMessageLocation(d);

        if (loc == null || loc.getScheme().equals("unknown")) {
            logger.error("Dropping diagnostic due to incorrect location on message: {}", d);
            return false;
        }

        if (!loc.top().equals(file.top())) {
            logger.error("Dropping diagnostic, reported for the wrong file: {}, {}", loc, file);
            return false;
        }
        return true;
    }
}
