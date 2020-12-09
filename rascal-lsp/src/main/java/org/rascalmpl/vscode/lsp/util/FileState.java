package org.rascalmpl.vscode.lsp.util;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.rascalmpl.parser.gtd.exception.ParseError;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.vscode.lsp.RascalLanguageServices;
import org.rascalmpl.vscode.lsp.RascalTextDocumentService;
import org.rascalmpl.vscode.lsp.model.Summary;

import io.usethesource.vallang.ISourceLocation;

public class FileState {
	private static final int DEBOUNCE_TIME = 500;
    public final ISourceLocation file;
	private final ExecutorService javaSchedular;
	private final RascalLanguageServices services;
	public volatile StampedReference<String> fileContents;
	private final AtomicReference<CompletableFuture<IRangeToLocationMap>> defineMap;
	public volatile CompletableFuture<Summary> currentSummary;
	public volatile CompletableFuture<ITree> currentTree;
	public volatile CompletableFuture<Summary> previousSummary;
	
	public FileState(RascalLanguageServices services, ISourceLocation file, ExecutorService javaSchedular) {
		this.services = services;
		this.file = file;
		this.javaSchedular = javaSchedular;
		this.defineMap = new AtomicReference<>(RascalTextDocumentService.EMPTY_LOCATION_RANGE_MAP);
		this.currentTree = RascalTextDocumentService.EMPTY_TREE;
		this.previousSummary = CompletableFuture.supplyAsync(() -> new Summary());
		this.currentSummary = previousSummary;
		this.fileContents = null;
	}
	

	public synchronized void newContents(String contents, RascalTextDocumentService parent) {
		fileContents = new StampedReference<String>(contents, System.currentTimeMillis()); 
		// if (currentTree.isDone()) {
			CompletableFuture<ITree> newTreeCalculate = new CompletableFuture<>();

            CompletableFuture.runAsync(() -> {
            	// repeat until we didn't race between the parser and completing the parse
            	while (true) {
            		// debounce the calls of the parser & rest
                    long time;
                    StampedReference<String> currentContents;
                    // while ((currentContents = fileContents).stamp + DEBOUNCE_TIME < (time = System.currentTimeMillis())) {
                    //     try {
                    //         Thread.sleep(DEBOUNCE_TIME - Math.abs(time - currentContents.stamp));
                    //     } catch (InterruptedException e) {
                    //         newTreeCalculate.completeExceptionally(e);
                    //         return;
                    //     }
                    // }

					currentContents = fileContents;
                    try {
                        ITree result = services.parseSourceFile(file, currentContents.value);
                        if (currentContents == fileContents) {
                        	newTreeCalculate.complete(result);
                        	return;
                        }
					} 
					catch (ParseError e) {
                    	if (currentContents == fileContents) {
                    		parent.replaceDiagnostics(file,
                                Stream.of(e)
                                .map(e1 -> new SimpleEntry<>(file, RascalTextDocumentService.translateDiagnostic(e1)))
                            );
                    		newTreeCalculate.completeExceptionally(e);
                    		return;
                    	}
					} 
					catch (Throwable e) {
                    	if (currentContents == fileContents) {
                    		newTreeCalculate.completeExceptionally(e);
                    		return;
                    	}
                    }
            	}
            }, javaSchedular);

			// CompletableFuture<Summary> newSummaryCalculate = newTreeCalculate.thenCombineAsync(previousSummary, 
			// // TODO: share ITree of the given module? Now we parse this from disk. Can't be correct.
			// // We need a getSummary to work on an ITree
			// (t,s) -> new Summary(services.getSummary(TreeAdapter.getLocation(t), services.getModulePathConfig(TreeAdapter.getLocation(t)))));

            // newSummaryCalculate.thenAcceptAsync((s) -> {
            // 	parent.replaceDiagnostics(file, s.getDiagnostics().map(d -> 
            // 		new SimpleEntry<>(
            // 			((ISourceLocation)d.get("at")).top()
            // 			, RascalTextDocumentService.translateDiagnostic(d)
            //         )
            //     ));
            // }, javaSchedular);
            
            currentTree = newTreeCalculate;
            // currentSummary = newSummaryCalculate;
            defineMap.set(null);
		// }
	}

    public CompletableFuture<List<? extends Location>> definition(Range cursor) {
		CompletableFuture<IRangeToLocationMap> defines = defineMap.get();
		while (defines == null) {
			defineMap.compareAndSet(null, currentSummary.thenApplyAsync(s -> {
				final TreeMapLookup result = new TreeMapLookup();
				s.getDefinitions().
					forEach(d -> result.add(RascalTextDocumentService.toRange(d.getKey()), RascalTextDocumentService.toJSPLoc(d.getValue())));
				return result;
			}, javaSchedular));
			defines = defineMap.get();
		}
		return defines.thenApply(rl -> 
			rl.lookup(cursor)
            .map(Collections::singletonList).orElse(Collections.emptyList())
        );
	}
}