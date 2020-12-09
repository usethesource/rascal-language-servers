/** 
 * Copyright (c) 2018, Davy Landman, SWAT.engineering BV
 * All rights reserved. 
 *  
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met: 
 *  
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer. 
 *  
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution. 
 *  
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. 
 */
package org.rascalmpl.vscode.lsp;

import java.io.IOException;
import java.io.Reader;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.rascalmpl.parser.gtd.exception.ParseError;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.vscode.lsp.util.FileState;
import org.rascalmpl.vscode.lsp.util.IRangeToLocationMap;
import org.rascalmpl.vscode.lsp.util.SimpleEntry;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.ITuple;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IWithKeywordParameters;

public class RascalTextDocumentService implements TextDocumentService, LanguageClientAware /*, ILSPContext*/ {
	public static final CompletableFuture<IRangeToLocationMap> EMPTY_LOCATION_RANGE_MAP = CompletableFuture.completedFuture(null);
	public static final CompletableFuture<ITree> EMPTY_TREE = CompletableFuture.completedFuture(null);
	private final Map<ISourceLocation, FileState> files;
	private final ExecutorService ownExcecutor = Executors.newCachedThreadPool();
	private final RascalLanguageServices rascalServices = RascalLanguageServices.getInstance();
	private LanguageClient client;
	private ConcurrentMap<ISourceLocation, List<Diagnostic>> currentDiagnostics = new ConcurrentHashMap<>();

	private static final Map<String, DiagnosticSeverity> serverityMap;
	static {
		serverityMap = new HashMap<>();
		serverityMap.put("error", DiagnosticSeverity.Error);
		serverityMap.put("warning", DiagnosticSeverity.Warning);
		serverityMap.put("info", DiagnosticSeverity.Information);
    }
    
    public RascalTextDocumentService() {
		this.files = new ConcurrentHashMap<>();
	}
	
	public void replaceDiagnostics(ISourceLocation clearFor, Stream<Entry<ISourceLocation, Diagnostic>> diagnostics) {
		Map<ISourceLocation, List<Diagnostic>> grouped = groupByKey(diagnostics);
		grouped.putIfAbsent(clearFor, Collections.emptyList());

		grouped.forEach((file, msgs) -> {
			client.publishDiagnostics(new PublishDiagnosticsParams(file.getURI().toString(), msgs));
			currentDiagnostics.replace(file, msgs);
		});
	}
	
	private static <K,V> Map<K, List<V>> groupByKey(Stream<Entry<K, V>> diagnostics) {
		return diagnostics.collect(
            Collectors.groupingBy(Entry::getKey, Collectors.mapping(Entry::getValue, Collectors.toList()))
        );
	}

	public static Diagnostic translateDiagnostic(ParseError e) {
		return new Diagnostic(toRange(e), e.getMessage(), DiagnosticSeverity.Error, "parser");
	}

	public static Diagnostic translateDiagnostic(IConstructor d) {
		Diagnostic result = new Diagnostic();
		result.setSeverity(serverityMap.get(d.getName()));
		result.setMessage(((IString)d.get("msg")).getValue());
		result.setRange(toRange((ISourceLocation) d.get("at")));
		IWithKeywordParameters<? extends IConstructor> dkw = d.asWithKeywordParameters();

		if (dkw.hasParameter("source")) {
			result.setSource(((IString)dkw.getParameter("source")).getValue());
		}

		if (dkw.hasParameter("relatedInformation")) {
			List<DiagnosticRelatedInformation> related = new ArrayList<>();
			for (IValue e : (IList)dkw.getParameter("relatedInformation")) {
				ITuple ent = (ITuple) e;
				related.add(new DiagnosticRelatedInformation(toJSPLoc((ISourceLocation) ent.get(0)), ((IString)ent.get(1)).getValue()));
			}
			result.setRelatedInformation(related);
		}

		return result;
	}
	
	public void appendDiagnostics(ISourceLocation location, Diagnostic msg) {
		List<Diagnostic> currentMessages = currentDiagnostics.get(location.top());
		currentMessages.add(msg);
		client.publishDiagnostics(new PublishDiagnosticsParams(location.top().getURI().toString(), currentMessages));
	}

	public void appendDiagnostics(Stream<Entry<ISourceLocation, Diagnostic>> diagnostics) {
		groupByKey(diagnostics)
			.forEach((file, msgs) -> {
				List<Diagnostic> currentMessages = currentDiagnostics.get(file);
				currentMessages.addAll(msgs);
                client.publishDiagnostics(new PublishDiagnosticsParams(file.getURI().toString(), currentMessages));
            });
	}

	public ITree getTree(ISourceLocation loc) throws IOException {
		FileState file = openExistingOrOpenNew(loc);

		if (file.fileContents == null) {
			// new file, we have to read it ourself
			StringBuilder result = new StringBuilder();
			try (Reader r = URIResolverRegistry.getInstance().getCharacterReader(loc)) {
				char[] buffer = new char[4096];
				int read;
				while ((read = r.read(buffer)) > 0) {
					result.append(buffer, 0, read);
				}
			}
			file.newContents(result.toString(), this);
		}

		try {
			return file.currentTree.get();
		} 
		catch (InterruptedException e) {
			return null;
		} 
		catch (ExecutionException e) {
			Throwable cause = e.getCause();
			if (cause instanceof ParseError) {
				throw (ParseError)cause;
			}
			else if (cause instanceof RuntimeException) {
				throw (RuntimeException)cause;
			}
			else {
				throw new RuntimeException(cause);
			}
		}
	}

	public void report(ISet msgs) {
		appendDiagnostics(StreamSupport.stream(msgs.spliterator(), false)
				.map(d -> (IConstructor)d)
				.map(d -> 
                		new SimpleEntry<>(
                			((ISourceLocation)d.get("at")).top()
                			, translateDiagnostic(d)
                        )
                    )
			);
	}

	public void setCapabilities(ServerCapabilities result) {
		result.setDefinitionProvider(true);
		result.setTextDocumentSync(TextDocumentSyncKind.Full);
	}
	
	public FileState openExistingOrOpenNew(ISourceLocation loc) {
		return files.computeIfAbsent(loc, 
				l -> new FileState(rascalServices, l, ownExcecutor));
	}


	@Override
	public void didOpen(DidOpenTextDocumentParams params) {
		openExistingOrOpenNew(toLoc(params.getTextDocument()))
			.newContents(params.getTextDocument().getText(), this);
	}

	private static ISourceLocation toLoc(TextDocumentItem doc) {
		return toLoc(doc.getUri());
	}
	private static ISourceLocation toLoc(TextDocumentIdentifier doc) {
		return toLoc(doc.getUri());
	}
	
	private static final LoadingCache<String, ISourceLocation> uriToLocCache = Caffeine.newBuilder()
			.maximumSize(1000)
			.expireAfterAccess(5, TimeUnit.MINUTES)
			.build(u -> {
				try {
					return URIUtil.createFromURI(u);
				} catch (URISyntaxException e) {
					throw new RuntimeException(e);
				}
			});

	private static ISourceLocation toLoc(String uri) {
		return uriToLocCache.get(uri);
	}

	@Override
	public void didChange(DidChangeTextDocumentParams params) {
        getFileOrThrow(toLoc(params.getTextDocument())).newContents(last(params.getContentChanges()).getText(), this);
	}
	
	private static <T> T last(List<T> l) {
		return l.get(l.size() - 1);
	}


	@Override
	public void didClose(DidCloseTextDocumentParams params) {
		if (files.remove(toLoc(params.getTextDocument())) == null) {
			throw new ResponseErrorException(new ResponseError(ResponseErrorCode.InternalError, "Unknown file: " + toLoc(params.getTextDocument()), params));
		}
	}

	@Override
	public void didSave(DidSaveTextDocumentParams params) {
		
	}
	
	private static final LoadingCache<ISourceLocation, String> slocToURI = Caffeine.newBuilder()
			.maximumSize(1000)
			.expireAfterAccess(5, TimeUnit.MINUTES)
			.build(l -> l.getURI().toString());
	
	public static Location toJSPLoc(ISourceLocation sloc) {
		return new Location(slocToURI.get(sloc), toRange(sloc));
    }
    
	public static Range toRange(ISourceLocation sloc) {
		return new Range(new Position(sloc.getBeginLine() - 1, sloc.getBeginColumn()), new Position(sloc.getEndLine() - 1, sloc.getEndColumn()));
	}

	private static Range toRange(ParseError pe) {
		return new Range(new Position(pe.getBeginLine() - 1, pe.getBeginColumn()), new Position(pe.getEndLine() - 1, pe.getEndColumn()));
	}

	private FileState getFileOrThrow(ISourceLocation loc) {
		FileState file = files.get(loc);
		if (file == null) {
			throw new ResponseErrorException(new ResponseError(-1, "Unknown file: " + loc, loc));
		}
		return file;
	}

	@Override
	public void connect(LanguageClient client) {
		this.client = client;
	}
}