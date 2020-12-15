/** 
 * Copyright (c) 2018, Davy Landman, SWAT.engineering BV, 2020 Jurgen J. Vinju, NWO-I CWI
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

import java.net.URISyntaxException;
import java.util.AbstractMap;
import java.util.AbstractMap.SimpleEntry;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
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
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.vscode.lsp.util.Diagnostics;
import org.rascalmpl.vscode.lsp.util.FileState;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISourceLocation;

public class RascalTextDocumentService implements TextDocumentService, LanguageClientAware {
	private final ExecutorService ownExcecutor = Executors.newCachedThreadPool();
	private final RascalLanguageServices rascalServices = RascalLanguageServices.getInstance();
	private LanguageClient client;
	private final Map<ISourceLocation, FileState> files;
	
	private ConcurrentMap<ISourceLocation, List<Diagnostic>> currentDiagnostics = new ConcurrentHashMap<>();

	private static final LoadingCache<String, ISourceLocation> uriToLocCache = Caffeine.newBuilder().maximumSize(1000)
	.expireAfterAccess(5, TimeUnit.MINUTES).build(u -> {
		try {
			return URIUtil.createFromURI(u);
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	});
	
	public RascalTextDocumentService() {
		this.files = new ConcurrentHashMap<>();
	}

	public void replaceDiagnostics(ISourceLocation clearFor, Stream<Entry<ISourceLocation, Diagnostic>> diagnostics) {
		Map<ISourceLocation, List<Diagnostic>> grouped = Diagnostics.groupByKey(diagnostics);
		grouped.putIfAbsent(clearFor, Collections.emptyList());

		grouped.forEach((file, msgs) -> {
			client.publishDiagnostics(new PublishDiagnosticsParams(file.getURI().toString(), msgs));
			currentDiagnostics.replace(file, msgs);
		});
	}

	public void clearDiagnostics(ISourceLocation file) {
		client.publishDiagnostics(new PublishDiagnosticsParams(file.getURI().toString(), Collections.emptyList()));
		currentDiagnostics.replace(file, Collections.emptyList());
	}

	public void appendDiagnostics(ISourceLocation location, Diagnostic msg) {
		List<Diagnostic> currentMessages = currentDiagnostics.get(location.top());
		currentMessages.add(msg);
		if (location.hasLineColumn()) {
			client.publishDiagnostics(new PublishDiagnosticsParams(location.top().getURI().toString(), currentMessages));
		} 
		else {
			client.publishDiagnostics(
					new PublishDiagnosticsParams(location.top().getURI().toString(), currentMessages));
		}
	}

	public void appendDiagnostics(Stream<Entry<ISourceLocation, Diagnostic>> diagnostics) {
		Diagnostics.groupByKey(diagnostics).forEach((file, msgs) -> {
			List<Diagnostic> currentMessages = currentDiagnostics.get(file);
			currentMessages.addAll(msgs);
			client.publishDiagnostics(new PublishDiagnosticsParams(file.getURI().toString(), currentMessages));
		});
	}

	public void report(ISet msgs) {
		appendDiagnostics(msgs.stream()
			.map(d -> (IConstructor) d)
			.map(d -> new SimpleEntry<>(((ISourceLocation) d.get("at")).top(), Diagnostics.translateDiagnostic(d))));
	}

	public void setCapabilities(ServerCapabilities result) {
		result.setDefinitionProvider(true);
		result.setTextDocumentSync(TextDocumentSyncKind.Full);
	}

	private FileState open(ISourceLocation loc, String content) {
		return files.computeIfAbsent(loc, l -> new FileState(rascalServices, this, ownExcecutor, l, content));
	}

	@Override
	public void didOpen(DidOpenTextDocumentParams params) {
		open(toLoc(params.getTextDocument()), params.getTextDocument().getText());
	}

	private static ISourceLocation toLoc(TextDocumentItem doc) {
		return toLoc(doc.getUri());
	}

	private static ISourceLocation toLoc(TextDocumentIdentifier doc) {
		return toLoc(doc.getUri());
	}



	private static ISourceLocation toLoc(String uri) {
		return uriToLocCache.get(uri);
	}

	@Override
	public void didChange(DidChangeTextDocumentParams params) {
		getFileOrThrow(toLoc(params.getTextDocument())).update(last(params.getContentChanges()).getText());
	}

	private static <T> T last(List<T> l) {
		return l.get(l.size() - 1);
	}

	@Override
	public void didClose(DidCloseTextDocumentParams params) {
		if (files.remove(toLoc(params.getTextDocument())) == null) {
			throw new ResponseErrorException(new ResponseError(ResponseErrorCode.InternalError,
					"Unknown file: " + toLoc(params.getTextDocument()), params));
		}
	}

	@Override
	public void didSave(DidSaveTextDocumentParams params) {

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

	public void report(ISourceLocation file, ISet messages) {
		replaceDiagnostics(file, messages.stream()
		.map(d -> (IConstructor) d)
		.map(d -> new AbstractMap.SimpleEntry<>(((ISourceLocation) d.get("at")).top(), Diagnostics.translateDiagnostic(d))));
	}

	public void report(ParseError e) {
		replaceDiagnostics(e.getLocation(), Stream.of(e)
								.map(e1 -> new AbstractMap.SimpleEntry<>(e.getLocation(), Diagnostics.translateDiagnostic(e1))));
	}
}