package org.rascalmpl.vscode.lsp.util;

import java.io.IOException;
import java.io.Reader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

import org.rascalmpl.parser.gtd.exception.ParseError;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.values.parsetrees.TreeAdapter;
import org.rascalmpl.vscode.lsp.RascalLanguageServices;
import org.rascalmpl.vscode.lsp.RascalTextDocumentService;
import org.rascalmpl.vscode.lsp.model.Summary;

import io.usethesource.vallang.ISourceLocation;

public class FileState {
	// private static final int DEBOUNCE_TIME = 500;
	private final ExecutorService javaScheduler;
	private final RascalLanguageServices services;
	private final RascalTextDocumentService parent;

	private final ISourceLocation file;
	private volatile TimedReference<String> fileContents;
	private volatile CompletableFuture<Summary> currentSummary;
	private volatile CompletableFuture<ITree> currentTree;
	private volatile CompletableFuture<Summary> previousSummary;

	public FileState(RascalLanguageServices services, RascalTextDocumentService tds, ExecutorService javaSchedular, ISourceLocation file) {
		this.services = services;
		this.javaScheduler = javaSchedular;
		this.parent = tds;
		
		this.file = file;
		this.fileContents = null;
		this.currentTree = CompletableFuture.completedFuture(null);
		this.previousSummary = CompletableFuture.supplyAsync(() -> new Summary());
		this.currentSummary = previousSummary;
	}

	public FileState(RascalLanguageServices services, RascalTextDocumentService tds, ExecutorService javaSchedular, ISourceLocation file, String content) {
		this(services, tds, javaSchedular, file);
		newContents(content);
	}

	public void update(String text) {
		newContents(text);
	}

	private synchronized void newContents(String contents) {
		fileContents = new TimedReference<String>(contents, System.currentTimeMillis());
		// if (currentTree.isDone()) {
		CompletableFuture<ITree> newTreeCalculate = new CompletableFuture<>();

		CompletableFuture.runAsync(() -> {
			// repeat until we didn't race between the parser and completing the parse
			while (true) {
				// debounce the calls of the parser & rest
				long time;
				TimedReference<String> currentContents;
				// while ((currentContents = fileContents).stamp + DEBOUNCE_TIME < (time =
				// System.currentTimeMillis())) {
				// try {
				// Thread.sleep(DEBOUNCE_TIME - Math.abs(time - currentContents.stamp));
				// } catch (InterruptedException e) {
				// newTreeCalculate.completeExceptionally(e);
				// return;
				// }
				// }

				currentContents = fileContents;
				try {
					ITree result = services.parseSourceFile(file, currentContents.value);
					if (currentContents == fileContents) {
						newTreeCalculate.complete(result);
						parent.clearDiagnostics(file);
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
			}
		}, javaScheduler);

		CompletableFuture<Summary> newSummaryCalculate = newTreeCalculate.thenCombineAsync(previousSummary,
				// TODO: share ITree of the given module? Now we parse this from disk. Can't be
				// correct.
				// We need a getSummary to work on an ITree
				(t, s) -> new Summary(services.getSummary(TreeAdapter.getLocation(t),
						services.getModulePathConfig(TreeAdapter.getLocation(t)))));

		newSummaryCalculate.thenAcceptAsync((s) -> {
			parent.report(file, s.getMessages());
		}, javaScheduler);

		currentTree = newTreeCalculate;
		// currentSummary = newSummaryCalculate;
		// }
	}

	public ITree getCurrentTree() throws IOException {
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
}