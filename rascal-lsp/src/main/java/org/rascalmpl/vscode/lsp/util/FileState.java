package org.rascalmpl.vscode.lsp.util;

import java.io.IOException;
import java.io.Reader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

import org.rascalmpl.library.util.PathConfig;
import org.rascalmpl.parser.gtd.exception.ParseError;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.vscode.lsp.RascalLanguageServices;
import org.rascalmpl.vscode.lsp.RascalTextDocumentService;
import org.rascalmpl.vscode.lsp.model.Summary;

import io.usethesource.vallang.ISourceLocation;

public class FileState {
	private final ExecutorService javaScheduler;
	private final RascalLanguageServices services;
	private final RascalTextDocumentService parent;

	private final ISourceLocation file;
	private final PathConfig pcfg;
	private volatile String fileContents;
	private volatile CompletableFuture<ITree> currentTree;
	private volatile CompletableFuture<Summary> currentSummary;
	
	public FileState(RascalLanguageServices services, RascalTextDocumentService tds, ExecutorService javaSchedular, ISourceLocation file) {
		this.services = services;
		this.javaScheduler = javaSchedular;
		this.parent = tds;
		
		this.file = file;
		this.fileContents = null;
		this.currentTree = CompletableFuture.completedFuture(null);

		try {
			this.pcfg = PathConfig.fromSourceProjectMemberRascalManifest(file);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public FileState(RascalLanguageServices services, RascalTextDocumentService tds, ExecutorService javaSchedular, ISourceLocation file, String content) {
		this(services, tds, javaSchedular, file);
		newContents(content);
	}

	public void update(String text) {
		newContents(text);
	}

	private synchronized void newContents(String contents) {
		fileContents = contents;
		
		CompletableFuture<ITree> newTreeCalculate = new CompletableFuture<>();

		CompletableFuture.runAsync(() -> {
			String currentContents = fileContents;
			try {
				ITree result = services.parseSourceFile(file, currentContents);
				if (currentContents == fileContents) {
					newTreeCalculate.complete(result);
					parent.clearReports(file);
					return;
				}
			} catch (ParseError e) {
				if (currentContents == fileContents) {
					parent.report(e);
					newTreeCalculate.completeExceptionally(e);
					return;
				}
			} catch (Throwable e) {
				if (currentContents == fileContents) {
					newTreeCalculate.completeExceptionally(e);
					return;
				}
			}
		}, javaScheduler);

		currentTree = newTreeCalculate;
	}

	public ITree getCurrentTree() {
		if (fileContents == null) {
			// new file, we have to read it in first
			
			StringBuilder result = new StringBuilder();
			try (Reader r = URIResolverRegistry.getInstance().getCharacterReader(file)) {
				char[] buffer = new char[4096];
				int read;
				while ((read = r.read(buffer)) > 0) {
					result.append(buffer, 0, read);
				}
			}
			catch (IOException e) {
				return null;
			}
			
			newContents(result.toString());
		}

		try {
			return currentTree.get();
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

	public CompletableFuture<Summary> getSummary() {
		currentSummary = currentTree.thenApplyAsync(t -> new Summary(services.getSummary(file, pcfg)), javaScheduler);
		return currentSummary;
	}
}