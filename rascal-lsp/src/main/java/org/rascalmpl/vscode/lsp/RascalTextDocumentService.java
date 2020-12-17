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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentSymbolCapabilities;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.rascalmpl.parser.gtd.exception.ParseError;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.values.parsetrees.TreeAdapter;
import org.rascalmpl.vscode.lsp.model.Summary;
import org.rascalmpl.vscode.lsp.util.Diagnostics;
import org.rascalmpl.vscode.lsp.util.ErrorReporter;
import org.rascalmpl.vscode.lsp.util.FileState;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValue;

public class RascalTextDocumentService implements TextDocumentService, LanguageClientAware, ErrorReporter {
	private final ExecutorService ownExcecutor = Executors.newCachedThreadPool();
	private final RascalLanguageServices rascalServices = RascalLanguageServices.getInstance();
	private LanguageClient client;
	private final Map<ISourceLocation, FileState> files;

	private ConcurrentMap<ISourceLocation, List<Diagnostic>> currentDiagnostics = new ConcurrentHashMap<>();

	public RascalTextDocumentService() {
		this.files = new ConcurrentHashMap<>();
	}

	public void initializeServerCapabilities(ServerCapabilities result) {
		result.setDefinitionProvider(true);
		result.setTextDocumentSync(TextDocumentSyncKind.Full);
	}

	@Override
	public void connect(LanguageClient client) {
		this.client = client;
	}

	// Error reporting API

	@Override
	public void clearReports(ISourceLocation file) {
		client.publishDiagnostics(new PublishDiagnosticsParams(file.getURI().toString(), Collections.emptyList()));
		currentDiagnostics.replace(file, Collections.emptyList());
	}

	@Override
	public void report(ISet msgs) {
		appendDiagnostics(msgs.stream().map(d -> (IConstructor) d).map(
				d -> new SimpleEntry<>(((ISourceLocation) d.get("at")).top(), Diagnostics.translateDiagnostic(d))));
	}

	@Override
	public void report(ISourceLocation file, ISet messages) {
		replaceDiagnostics(file,
				messages.stream().map(d -> (IConstructor) d)
						.map(d -> new AbstractMap.SimpleEntry<>(((ISourceLocation) d.get("at")).top(),
								Diagnostics.translateDiagnostic(d))));
	}

	@Override
	public void report(ParseError e) {
		replaceDiagnostics(e.getLocation(), Stream.of(e)
				.map(e1 -> new AbstractMap.SimpleEntry<>(e.getLocation(), Diagnostics.translateDiagnostic(e1))));
	}

	// LSP interface methods

	@Override
	public void didOpen(DidOpenTextDocumentParams params) {
		open(params.getTextDocument());
	}

	@Override
	public void didChange(DidChangeTextDocumentParams params) {
		getFile(toLoc(params.getTextDocument())).update(last(params.getContentChanges()).getText());

		// TODO: temporarily here because didSave is not called
		try {
			Summary summary = getFile(toLoc(params.getTextDocument())).getSummary().get();
			report(summary.getMessages());
		} catch (InterruptedException | ExecutionException e) {
			Logger.getGlobal().log(Level.INFO, "compilation/typechecking of " + params.getTextDocument().getUri() + " failed", e);
		}
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
		try {
			Summary summary = getFile(toLoc(params.getTextDocument())).getSummary().get();
			report(summary.getMessages());
		} catch (InterruptedException | ExecutionException e) {
			Logger.getGlobal().log(Level.INFO, "compilation/typechecking of " + params.getTextDocument().getUri() + " failed", e);
		}
	}

	@Override
	public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(DefinitionParams params) {
		FileState file = getFile(toLoc(params.getTextDocument()));
	
		final int column = params.getPosition().getCharacter();
		final int line = params.getPosition().getLine();

		return file.getSummary()
			.thenApply(s -> {
				final ITree tree = file.getCurrentTree();
				ITree lexical = locateLexical(tree, line, column);

				if (lexical == null) {
					throw new RuntimeException("no lexical found");
				}

				return toLSPLocation(s.definition(TreeAdapter.getLocation(lexical)));
			})
			.thenApply(l -> locList(l))
			.exceptionally(e -> locList())
			;
	}

	// Private utility methods

	private static Either<List<? extends Location>, List<? extends LocationLink>> locList(Location... l) {
		return Either.<List<? extends Location>, List<? extends LocationLink>>forLeft(Arrays.asList(l));
	}

	private static Location toLSPLocation(ISourceLocation sloc) {
		return new Location(sloc.getURI().toASCIIString(), toRange(sloc));
	}

	private static Range toRange(ISourceLocation sloc) {
		return new Range(new Position(sloc.getBeginLine() - 1, sloc.getBeginColumn()), new Position(sloc.getEndLine() - 1, sloc.getEndColumn()));
	}

	public static ITree locateLexical(ITree tree, int line, int column) {
		ISourceLocation l = TreeAdapter.getLocation(tree);

		if (l == null) {
			throw new IllegalArgumentException("no position info");
		}

		if (!l.hasLineColumn()) {
			return null;
		}

		if (TreeAdapter.isLexical(tree)) {
			if (l.getBeginLine() == line && l.getBeginColumn() <= column && column <= l.getEndColumn()) {
				// found a lexical that has the cursor inside of it
				return tree;
			}

			return null;
		}

		if (TreeAdapter.isAmb(tree)) {
			return null;
		}

		if (TreeAdapter.isAppl(tree)) {
			IList children = TreeAdapter.getASTArgs(tree);

			for (IValue child : children) {
				ISourceLocation childLoc = TreeAdapter.getLocation((ITree) child);

				if (childLoc == null) {
					continue;
				}

				// only go down in the right range, such that
				// finding the lexical is in O(log filesize)
				if (childLoc.getBeginLine() <= line && line <= childLoc.getEndLine()) {
					if (childLoc.getBeginLine() == line && childLoc.getEndColumn() == line) {
						// go down to the right column
						if (childLoc.getBeginColumn() <= column && column <= childLoc.getEndColumn()) {
							ITree result = locateLexical((ITree) child, line, column);	
							if (result != null) {
								return result;
							}
						}
					}
					else { // in the line range, but not on the exact line yet
						ITree result = locateLexical((ITree) child, line, column);

						if (result != null) {
							return result;
						}
					}
				}
			}
		}

		return null;
	}
	
	private void replaceDiagnostics(ISourceLocation clearFor, Stream<Entry<ISourceLocation, Diagnostic>> diagnostics) {
		Map<ISourceLocation, List<Diagnostic>> grouped = Diagnostics.groupByKey(diagnostics);
		grouped.putIfAbsent(clearFor, Collections.emptyList());

		grouped.forEach((file, msgs) -> {
			client.publishDiagnostics(new PublishDiagnosticsParams(file.getURI().toString(), msgs));
			currentDiagnostics.replace(file, msgs);
		});
	}

	private void appendDiagnostics(Stream<Entry<ISourceLocation, Diagnostic>> diagnostics) {
		Diagnostics.groupByKey(diagnostics).forEach((file, msgs) -> {
			List<Diagnostic> currentMessages = currentDiagnostics.get(file);
			currentMessages.addAll(msgs);
			client.publishDiagnostics(new PublishDiagnosticsParams(file.getURI().toString(), currentMessages));
		});
	}

	private static ISourceLocation toLoc(TextDocumentItem doc) {
		return toLoc(doc.getUri());
	}

	private static ISourceLocation toLoc(TextDocumentIdentifier doc) {
		return toLoc(doc.getUri());
	}

	private static ISourceLocation toLoc(String uri) {
		try {
			return URIUtil.createFromURI(uri);
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	private static <T> T last(List<T> l) {
		return l.get(l.size() - 1);
	}

	private FileState open(TextDocumentItem doc) {
		return files.computeIfAbsent(toLoc(doc), l -> new FileState(rascalServices, this, ownExcecutor, l, doc.getText()));
	}

	private FileState getFile(ISourceLocation loc) {
		FileState file = files.get(loc);
		if (file == null) {
			throw new ResponseErrorException(new ResponseError(-1, "Unknown file: " + loc, loc));
		}
		return file;
	}
}