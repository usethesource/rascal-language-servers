package org.rascalmpl.vscode.lsp.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.rascalmpl.parser.gtd.exception.ParseError;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.ITuple;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IWithKeywordParameters;

public class Diagnostics {
    private static final Map<String, DiagnosticSeverity> serverityMap;
	static {
		serverityMap = new HashMap<>();
		serverityMap.put("error", DiagnosticSeverity.Error);
		serverityMap.put("warning", DiagnosticSeverity.Warning);
		serverityMap.put("info", DiagnosticSeverity.Information);
    }
    
    private static final LoadingCache<ISourceLocation, String> slocToURI = Caffeine.newBuilder().maximumSize(1000)
			.expireAfterAccess(5, TimeUnit.MINUTES).build(l -> l.getURI().toString());

    public static <K, V> Map<K, List<V>> groupByKey(Stream<Entry<K, V>> diagnostics) {
		return diagnostics.collect(
				Collectors.groupingBy(Entry::getKey, Collectors.mapping(Entry::getValue, Collectors.toList())));
	}

	public static Diagnostic translateDiagnostic(ParseError e) {
		return new Diagnostic(toRange(e), e.getMessage(), DiagnosticSeverity.Error, "parser");
	}

	public static Diagnostic translateDiagnostic(IConstructor d) {
		Diagnostic result = new Diagnostic();
		result.setSeverity(serverityMap.get(d.getName()));
		result.setMessage(((IString) d.get("msg")).getValue());
		result.setRange(toRange((ISourceLocation) d.get("at")));
		IWithKeywordParameters<? extends IConstructor> dkw = d.asWithKeywordParameters();

		if (dkw.hasParameter("source")) {
			result.setSource(((IString) dkw.getParameter("source")).getValue());
		}

		if (dkw.hasParameter("relatedInformation")) {
			List<DiagnosticRelatedInformation> related = new ArrayList<>();
			for (IValue e : (IList) dkw.getParameter("relatedInformation")) {
				ITuple ent = (ITuple) e;
				related.add(new DiagnosticRelatedInformation(toJSPLoc((ISourceLocation) ent.get(0)),
						((IString) ent.get(1)).getValue()));
			}
			result.setRelatedInformation(related);
		}

		return result;
    }
    
    public static Range toRange(ISourceLocation sloc) {
		return new Range(new Position(sloc.getBeginLine() - 1, sloc.getBeginColumn()),
				new Position(sloc.getEndLine() - 1, sloc.getEndColumn()));
	}

    public static Location toJSPLoc(ISourceLocation sloc) {
		return new Location(slocToURI.get(sloc), toRange(sloc));
    }
    
	private static Range toRange(ParseError pe) {
		Logger.getGlobal().log(Level.SEVERE, pe.getMessage());
		if (pe.getBeginLine() == pe.getEndLine() && pe.getBeginColumn() == pe.getEndColumn()) {
			return new Range(new Position(pe.getBeginLine() - 1, pe.getBeginColumn()),
					new Position(pe.getEndLine() - 1, pe.getEndColumn() + 1));
		} else {
			return new Range(new Position(pe.getBeginLine() - 1, pe.getBeginColumn()),
					new Position(pe.getEndLine() - 1, pe.getEndColumn()));
		}
	}
}
