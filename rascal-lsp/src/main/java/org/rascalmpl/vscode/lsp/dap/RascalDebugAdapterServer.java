package org.rascalmpl.vscode.lsp.dap;

import java.io.IOException;
import java.io.Reader;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.debug.Breakpoint;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.ConfigurationDoneArguments;
import org.eclipse.lsp4j.debug.ExceptionBreakpointsFilter;
import org.eclipse.lsp4j.debug.InitializeRequestArguments;
import org.eclipse.lsp4j.debug.SetBreakpointsArguments;
import org.eclipse.lsp4j.debug.SetBreakpointsResponse;
import org.eclipse.lsp4j.debug.SourceBreakpoint;
import org.eclipse.lsp4j.debug.Thread;
import org.eclipse.lsp4j.debug.ThreadsResponse;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.rascalmpl.debug.AbstractInterpreterEventTrigger;
import org.rascalmpl.debug.DebugHandler;
import org.rascalmpl.debug.DebugMessageFactory;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.values.RascalValueFactory;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.values.parsetrees.ProductionAdapter;
import org.rascalmpl.values.parsetrees.TreeAdapter;
import org.rascalmpl.vscode.lsp.util.RascalServices;

import io.usethesource.vallang.IList;
import io.usethesource.vallang.INode;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValue;

public class RascalDebugAdapterServer implements IDebugProtocolServer {

    IDebugProtocolClient client;
    final private RascalDebugEventTrigger eventTrigger;
    final private DebugHandler debugHandler;

    public RascalDebugAdapterServer(AbstractInterpreterEventTrigger eventTrigger, DebugHandler debugHandler) {
        this.eventTrigger = (RascalDebugEventTrigger) eventTrigger;
        this.debugHandler = debugHandler;
    }

    public void connect(IDebugProtocolClient client) {
        this.client = client;
        this.eventTrigger.setDebugProtocolClient(client);
    }

    @Override
    public CompletableFuture<Capabilities> initialize(InitializeRequestArguments args) {
        CompletableFuture<Capabilities> future = CompletableFuture.supplyAsync(() -> {
            Capabilities capabilities = new Capabilities();

            capabilities.setSupportsConfigurationDoneRequest(true);
            capabilities.setExceptionBreakpointFilters(new ExceptionBreakpointsFilter[]{});

            return capabilities;
        });

        return future;
    }

    @Override
    public CompletableFuture<SetBreakpointsResponse> setBreakpoints(SetBreakpointsArguments args) {
        CompletableFuture<SetBreakpointsResponse> future = CompletableFuture.supplyAsync(() -> {
            SetBreakpointsResponse response = new SetBreakpointsResponse();
            Breakpoint[] breakpoints = new Breakpoint[args.getBreakpoints().length];
            int i = 0;
            StringBuilder contents = new StringBuilder();
            try {
                // TODO : handle different path format (ex: module std:/IO.rsc)
                // TODO : handle error of lower case harddrive name
                String path = args.getSource().getPath().replace("c:", "C:");
                ISourceLocation loc = URIUtil.createFileLocation(path);
                try(Reader reader = URIResolverRegistry.getInstance().getCharacterReader(loc)) {
                    char[] buffer = new char[1024];
                    int bufferlen = 0;
                    while((bufferlen = reader.read(buffer)) > 0){
                        contents.append(buffer, 0, bufferlen);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    // TODO : handle errors
                }
                ITree parseTree = RascalServices.parseRascalModule(loc, contents.toString().toCharArray());
                for (SourceBreakpoint breakpoint : args.getBreakpoints()) {
                    System.out.println(" - breakpoint line : " + breakpoint.getLine());
                    ITree treeBreakableLocation = locateBreakableTree(parseTree, breakpoint.getLine());
                    if(treeBreakableLocation != null) {
                        ISourceLocation breakableLocation = TreeAdapter.getLocation(treeBreakableLocation);
                        debugHandler.processMessage(DebugMessageFactory.requestSetBreakpoint(breakableLocation));
                    }
                    Breakpoint b = new Breakpoint();
                    b.setLine(breakpoint.getLine());
                    b.setColumn(breakpoint.getColumn());
                    b.setVerified(treeBreakableLocation != null);
                    breakpoints[i] = b;
                    i++;
                }
            } catch (URISyntaxException e) {
                e.printStackTrace();
                // TODO : handle failed breakpoint path
            }
            response.setBreakpoints(breakpoints);
            return response;
        });
        return future;
    }

    private static final String breakable = "breakable";
    private static ITree locateBreakableTree(ITree tree, int line) {
		ISourceLocation l = TreeAdapter.getLocation(tree);

		if (l == null) {
			throw new IllegalArgumentException("Missing location");
		}

		if (TreeAdapter.isAmb(tree)) {
            INode node = IRascalValueFactory.getInstance().node(breakable);
			if (ProductionAdapter.hasAttribute(TreeAdapter.getProduction(tree), IRascalValueFactory.getInstance().constructor(RascalValueFactory.Attr_Tag, node))) {
				return tree;
			}

			return null;
		}

		if (TreeAdapter.isAppl(tree) && !TreeAdapter.isLexical(tree)) {
			IList children = TreeAdapter.getArgs(tree);

			for (IValue child : children) {
				ISourceLocation childLoc = TreeAdapter.getLocation((ITree) child);

				if (childLoc == null) {
					continue;
				}

				if (childLoc.getBeginLine() <= line && line <= childLoc.getEndLine() ) {
					ITree result = locateBreakableTree((ITree) child, line);

					if (result != null) {
						return result;
					}
				}
			}
		}
        INode node = IRascalValueFactory.getInstance().node(breakable);
		if (l.getBeginLine() == line && ProductionAdapter.hasAttribute(TreeAdapter.getProduction(tree), IRascalValueFactory.getInstance().constructor(RascalValueFactory.Attr_Tag, node))) {
			return tree;
		}

		return null;
	}

    @Override
    public CompletableFuture<Void> attach(Map<String, Object> args) {
        CompletableFuture<Void> future = CompletableFuture.supplyAsync(() -> {
            return null;
        });

        future.thenAccept(result -> {
            client.initialized();
        });

        return future;
    }

    @Override
    public CompletableFuture<Void> configurationDone(ConfigurationDoneArguments args) {
        CompletableFuture<Void> future = CompletableFuture.supplyAsync(() -> {
            return null;
        });

        return future;
    }

    @Override
    public CompletableFuture<ThreadsResponse> threads() {
        CompletableFuture<ThreadsResponse> future = CompletableFuture.supplyAsync(() -> {
            ThreadsResponse response = new ThreadsResponse();
            Thread t = new Thread();
            t.setId(1);
            t.setName("Main Thread");
            response.setThreads(new Thread[]{
                t
            });
            return response;
        });
        return future;
    }
}

