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

    // Note: DiagnosticSeverity.Hint only highlightes a single character!
    static DiagnosticSeverity errorLocationHighlight = DiagnosticSeverity.Error;
    static DiagnosticSeverity errorTreeHighlight = null;
    static DiagnosticSeverity prefixHighlight = null;
    static DiagnosticSeverity skippedHighlight = null;

    static {
        severityMap = new HashMap<>();
        severityMap.put("error", DiagnosticSeverity.Error);
        severityMap.put("warning", DiagnosticSeverity.Warning);
        severityMap.put("info", DiagnosticSeverity.Information);
    }

    public static <K, V> Map<K, List<V>> groupByKey(Stream<Entry<K, V>> diagnostics) {
        return diagnostics.collect(
            Collectors.groupingBy(Entry::getKey,
                Collectors.mapping(Entry::getValue, Collectors.toCollection(ArrayList::new))));
    }

    public static Diagnostic translateDiagnostic(ParseError e, ColumnMaps cm) {
        var message = e.getMessage() + " (irrecoverable)";
        return new Diagnostic(toRange(e, cm), message, DiagnosticSeverity.Error, "parser");
    }

    public static List<Diagnostic> generateParseErrorDiagnostics(ITree errorTree, ColumnMaps cm) {
        IValueFactory factory = ValueFactoryFactory.getValueFactory();

        IList args = TreeAdapter.getArgs(errorTree);
        ITree skipped = (ITree) args.get(args.size()-1);

        ISourceLocation errorTreeLoc = TreeAdapter.getLocation(errorTree);
        ISourceLocation skippedLoc = TreeAdapter.getLocation(skipped);

        List<Diagnostic> diagnostics = new ArrayList<>();

        // Highlight selected parts of the error tree
        if (errorLocationHighlight != null) {
            // Just the error location
            ISourceLocation errorLoc = factory.sourceLocation(skippedLoc,
                    skippedLoc.getOffset(), 1,
                    skippedLoc.getBeginLine(), skippedLoc.getBeginLine(),
                    skippedLoc.getBeginColumn(), skippedLoc.getBeginColumn() + 1);
            diagnostics.add(new Diagnostic(toRange(errorLoc, cm), "Recovered parse error location",
                    errorLocationHighlight, "parser"));
        }

        if (errorTreeHighlight != null) {
            // The whole error tree
            diagnostics.add(new Diagnostic(toRange(errorTreeLoc, cm), "Recovered parse error", errorTreeHighlight, "parser"));
        }

        if (prefixHighlight != null) {
            // The recognized prefix
            int prefixLength = skippedLoc.getOffset()-errorTreeLoc.getOffset();
            if (prefixLength > 0) {
                ISourceLocation prefixLoc = factory.sourceLocation(errorTreeLoc,
                        errorTreeLoc.getOffset(), skippedLoc.getOffset()-errorTreeLoc.getOffset(),
                        errorTreeLoc.getBeginLine(), skippedLoc.getBeginLine(),
                        errorTreeLoc.getBeginColumn(), skippedLoc.getBeginColumn());
                diagnostics.add(new Diagnostic(toRange(prefixLoc, cm), "Recovered parse error prefix", DiagnosticSeverity.Error, "parser"));
            }
        }

        if (skippedHighlight != null && skippedLoc.getLength() > 0) {
            // The skipped part
            diagnostics.add(new Diagnostic(toRange(skippedLoc, cm), "Recovered parse error skipped", skippedHighlight, "parser"));
        }

        // Note: DiagnosticSeverity.Hint only highlightes a single character!

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
            try {
                loc = ValueFactoryFactory.getValueFactory().sourceLocation(loc,
                    loc.getOffset(), loc.getLength() + 1,
                    loc.getBeginLine(), loc.getBeginColumn(),
                    loc.getEndLine(), loc.getEndColumn() + 1);
            } catch (Throwable t) {
                logger.trace("Cannot extend 0-width location for parse error: " + t.getMessage());
                loc = ValueFactoryFactory.getValueFactory().sourceLocation(
                    loc, 0, 1, 1, 1, 0, 1);
            }
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

            List<Diagnostic> lst = results.get(file);
            if (lst == null) {
                lst = new LinkedList<>();
                results.put(file, lst);
            }
            lst.add(d);
        }

        return results;
    }

    private static ISourceLocation getMessageLocation(IConstructor message) {
        return Locations.toPhysicalIfPossible(((ISourceLocation) message.get("at")));
    }

    private static boolean hasValidLocation(IConstructor d, ISourceLocation file) {
        ISourceLocation loc = getMessageLocation(d);

        if (loc == null || loc.getScheme().equals("unknown")) {
            logger.error("Dropping diagnostic due to incorrect location on message: {}", d);
            return false;
        }

        if (!loc.top().equals(file.top())) {
            logger.error("Dropping diagnostic, reported for the wrong file: " + loc + ", " + file);
            return false;
        }
        return true;
    }
}
