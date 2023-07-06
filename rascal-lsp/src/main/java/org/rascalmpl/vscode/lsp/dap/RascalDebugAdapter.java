package org.rascalmpl.vscode.lsp.dap;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.lsp4j.debug.*;
import org.eclipse.lsp4j.debug.Thread;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.rascalmpl.debug.DebugHandler;
import org.rascalmpl.debug.DebugMessageFactory;
import org.rascalmpl.debug.IRascalFrame;
import org.rascalmpl.interpreter.Evaluator;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.values.RascalValueFactory;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.values.parsetrees.ProductionAdapter;
import org.rascalmpl.values.parsetrees.TreeAdapter;
import org.rascalmpl.vscode.lsp.terminal.LSPTerminalREPL;
import org.rascalmpl.vscode.lsp.util.RascalServices;

import io.usethesource.vallang.IList;
import io.usethesource.vallang.INode;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValue;

public class RascalDebugAdapter implements IDebugProtocolServer {

    final public static int mainThreadID = 1;
    final private int expensiveScopeMinSize = 100; // a scope is marked as expensive when there are more than xxx variables in it

    private IDebugProtocolClient client;
    final private RascalDebugEventTrigger eventTrigger;
    final private DebugHandler debugHandler;
    final private Evaluator evaluator;
    final private SuspendedState suspendedState;
    final private Logger logger;
    final private BreakpointsCollection breakpointsCollection;


    public RascalDebugAdapter(DebugHandler debugHandler, Evaluator evaluator) {
        this.debugHandler = debugHandler;
        this.evaluator = evaluator;

        this.suspendedState = new SuspendedState(evaluator);
        this.logger = LogManager.getLogger(RascalDebugAdapter.class);
        this.breakpointsCollection = new BreakpointsCollection(debugHandler);

        this.eventTrigger = new RascalDebugEventTrigger(this, breakpointsCollection, suspendedState, debugHandler);
        debugHandler.setEventTrigger(eventTrigger);
    }

    public void connect(IDebugProtocolClient client) {
        this.client = client;
        this.eventTrigger.setDebugProtocolClient(client);
    }

    @Override
    public CompletableFuture<Capabilities> initialize(InitializeRequestArguments args) {
        return CompletableFuture.supplyAsync(() -> {
            Capabilities capabilities = new Capabilities();

            capabilities.setSupportsConfigurationDoneRequest(true);
            capabilities.setExceptionBreakpointFilters(new ExceptionBreakpointsFilter[]{});
            capabilities.setSupportsStepBack(false);
            capabilities.setSupportsRestartFrame(false);
            capabilities.setSupportsSetVariable(false);
            capabilities.setSupportsRestartRequest(false);

            return capabilities;
        });
    }


    @Override
    public CompletableFuture<SetBreakpointsResponse> setBreakpoints(SetBreakpointsArguments args) {
        return CompletableFuture.supplyAsync(() -> {
            SetBreakpointsResponse response = new SetBreakpointsResponse();
            Breakpoint[] breakpoints = new Breakpoint[args.getBreakpoints().length];
            int i = 0;
            StringBuilder contents = new StringBuilder();
            // TODO : handle different path format (ex: module std:/IO.rsc)
            String path = args.getSource().getPath();
            ISourceLocation loc;
            try {
                loc = URIUtil.createFileLocation(path);
            } catch (URISyntaxException e) {
                logger.error(e.getMessage(), e);
                response.setBreakpoints(new Breakpoint[0]);
                return response;
            }
            try(Reader reader = URIResolverRegistry.getInstance().getCharacterReader(loc)) {
                char[] buffer = new char[1024];
                int bufferlen = 0;
                while((bufferlen = reader.read(buffer)) > 0){
                    contents.append(buffer, 0, bufferlen);
                }
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
                response.setBreakpoints(new Breakpoint[0]);
                return response;
            }
            ITree parseTree = RascalServices.parseRascalModule(loc, contents.toString().toCharArray());
            breakpointsCollection.clearBreakpointsOfFile(loc.getPath());
            for (SourceBreakpoint breakpoint : args.getBreakpoints()) {
                ITree treeBreakableLocation = locateBreakableTree(parseTree, breakpoint.getLine());
                if(treeBreakableLocation != null) {
                    ISourceLocation breakableLocation = TreeAdapter.getLocation(treeBreakableLocation);
                    breakpointsCollection.addBreakpoint(breakableLocation, args.getSource());
                }
                Breakpoint b = new Breakpoint();
                b.setId(i);
                b.setLine(breakpoint.getLine());
                b.setColumn(breakpoint.getColumn());
                b.setVerified(treeBreakableLocation != null);
                breakpoints[i] = b;
                i++;
            }
            response.setBreakpoints(breakpoints);
            return response;
        });
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
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);

        future.thenAccept(result -> {
            client.initialized();
        });

        return future;
    }

    @Override
    public CompletableFuture<Void> configurationDone(ConfigurationDoneArguments args) {
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);

        future.thenAccept(result -> {
            ProcessEventArguments eventArgs = new ProcessEventArguments();
            eventArgs.setSystemProcessId((int) ProcessHandle.current().pid());
            eventArgs.setName(LSPTerminalREPL.class.getProtectionDomain().getCodeSource().getLocation().getPath());
            eventArgs.setIsLocalProcess(true);
            eventArgs.setStartMethod(ProcessEventArgumentsStartMethod.ATTACH);
            client.process(eventArgs);

            ThreadEventArguments thread = new ThreadEventArguments();
            thread.setThreadId(mainThreadID);
            thread.setReason(ThreadEventArgumentsReason.STARTED);
            client.thread(thread);
        });

        return future;
    }

    @Override
    public CompletableFuture<ThreadsResponse> threads() {
        return CompletableFuture.supplyAsync(() -> {
            ThreadsResponse response = new ThreadsResponse();
            Thread t = new Thread();
            t.setId(mainThreadID);
            t.setName("Main Thread");
            response.setThreads(new Thread[]{
                t
            });
            return response;
        });
    }

    @Override
    public CompletableFuture<StackTraceResponse> stackTrace(StackTraceArguments args) {
        if(args.getThreadId() != mainThreadID) {
            return CompletableFuture.completedFuture(new StackTraceResponse());
        }
        return CompletableFuture.supplyAsync(() -> {
            StackTraceResponse response = new StackTraceResponse();
            IRascalFrame[] stackFrames = suspendedState.getCurrentStackFrames();
            response.setTotalFrames(stackFrames.length);
            StackFrame[] stackFramesResponse = new StackFrame[stackFrames.length];
            IRascalFrame currentFrame = suspendedState.getCurrentStackFrame();
            ISourceLocation currentLoc = evaluator.getCurrentPointOfExecution() != null ?
                evaluator.getCurrentPointOfExecution()
                : URIUtil.rootLocation("stdin");
            StackFrame frame = new StackFrame();
            frame.setId(stackFrames.length-1);
            frame.setName(currentFrame.getName());
            frame.setLine(currentLoc.getBeginLine());
            frame.setColumn(currentLoc.getBeginColumn());
            frame.setSource(getSourceFromISourceLocation(currentLoc));
            stackFramesResponse[0] = frame;
            for(int i = 1; i < stackFramesResponse.length; i++) {
                IRascalFrame f = stackFrames[stackFrames.length-i-1];
                ISourceLocation loc = stackFrames[stackFrames.length-i].getCallerLocation();
                frame = new StackFrame();
                frame.setId(stackFrames.length-i-1);
                frame.setName(f.getName());
                if(loc != null){
                    frame.setLine(loc.getBeginLine());
                    frame.setColumn(loc.getBeginColumn());
                    frame.setSource(getSourceFromISourceLocation(loc));
                }
                stackFramesResponse[i] = frame;
            }
            response.setStackFrames(stackFramesResponse);
            return response;
        });
    }

    public Source getSourceFromISourceLocation(ISourceLocation loc) {
        //TODO: handle location conversion to source
        Source source = new Source();
        File file = new File(loc.getPath());
        source.setName(file.getName());
        source.setPath(loc.getPath());
        //TODO: handle source reference
        source.setSourceReference(0);
        return source;
    }

    @Override
    public CompletableFuture<ScopesResponse> scopes(ScopesArguments args) {
        return CompletableFuture.supplyAsync(() -> {
            int frameId = args.getFrameId();

            List<Scope> scopes = new ArrayList<>();

            IRascalFrame frame = suspendedState.getCurrentStackFrames()[frameId];
            ScopesResponse response = new ScopesResponse();
            Scope scopeLocals = new Scope();
            scopeLocals.setName("Locals");
            scopeLocals.setNamedVariables(frame.getFrameVariables().size());
            scopeLocals.setPresentationHint("locals");
            scopeLocals.setExpensive(frame.getFrameVariables().size()>expensiveScopeMinSize);
            scopeLocals.setVariablesReference(suspendedState.addScope(frame));
            scopes.add(scopeLocals);

            for(String importName : frame.getImports()){
                IRascalFrame module = evaluator.getModule(importName);

                if(module != null && module.getFrameVariables().size() > 0){
                    Scope scopeModule = new Scope();
                    scopeModule.setName("Module " + importName);
                    scopeModule.setNamedVariables(module.getFrameVariables().size());
                    scopeModule.setPresentationHint("module");
                    scopeModule.setExpensive(module.getFrameVariables().size()>expensiveScopeMinSize);
                    scopeModule.setVariablesReference(suspendedState.addScope(module));
                    scopes.add(scopeModule);
                }
            }

            response.setScopes(scopes.toArray(new Scope[scopes.size()]));
            return response;
        });
    }

    @Override
    public CompletableFuture<VariablesResponse> variables(VariablesArguments args) {
        return CompletableFuture.supplyAsync(() -> {
            int reference = args.getVariablesReference();
            int minIndex = args.getStart() != null ? args.getStart() : 0;
            int maxCount = args.getCount() != null ? args.getCount() : -1;

            VariablesResponse response = new VariablesResponse();
            List<RascalVariable> variables = suspendedState.getVariables(reference, minIndex, maxCount);
            Variable[] variablesResponse = new Variable[variables.size()];
            int i = 0;
            for(RascalVariable var : variables){
                Variable variable = new Variable();
                variable.setName(var.getName());
                variable.setType(var.getType().toString());
                variable.setValue(var.getDisplayValue());
                variable.setVariablesReference(var.getReferenceID());
                variablesResponse[i] = variable;
                i++;
            }

            response.setVariables(variablesResponse);
            return response;
        });
    }

    @Override
    public CompletableFuture<ContinueResponse> continue_(ContinueArguments args) {
        return CompletableFuture.supplyAsync(() -> {
            ContinueResponse response = new ContinueResponse();
            response.setAllThreadsContinued(true);

            debugHandler.processMessage(DebugMessageFactory.requestResumption());

            return response;
        });
    }

    @Override
    public CompletableFuture<Void> next(NextArguments args) {
        return CompletableFuture.supplyAsync(() -> {
            debugHandler.processMessage(DebugMessageFactory.requestStepOver());
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> disconnect(DisconnectArguments args) {
        return CompletableFuture.supplyAsync(() -> {
            debugHandler.processMessage(DebugMessageFactory.requestTermination());
            // TODO: fix, serversocket closed before client is notified (it shows an error message on vscode)
            RascalDebugAdapterLauncher.stopDebugServer();
            if(suspendedState.isSuspended()){
                debugHandler.processMessage(DebugMessageFactory.requestResumption());
            }

            return null;
        });
    }

    @Override
    public CompletableFuture<Void> stepIn(StepInArguments args) {
        return CompletableFuture.supplyAsync(() -> {
            debugHandler.processMessage(DebugMessageFactory.requestStepInto());

            return null;
        });
    }

    @Override
    public CompletableFuture<Void> stepOut(StepOutArguments args) {
        return CompletableFuture.supplyAsync(() -> {
            //TODO: implementation of step out request.
            logger.debug("Step out request not implemented in debug adapter");
            debugHandler.processMessage(DebugMessageFactory.requestStepOver());

            return null;
        });
    }

    @Override
    public CompletableFuture<Void> pause(PauseArguments args) {
        return CompletableFuture.supplyAsync(() -> {
            debugHandler.processMessage(DebugMessageFactory.requestSuspension());

            return null;
        });
    }

    @Override
    public CompletableFuture<SourceResponse> source(SourceArguments args) {
        System.out.println("source request");
        return IDebugProtocolServer.super.source(args);
    }

    @Override
    public CompletableFuture<EvaluateResponse> evaluate(EvaluateArguments args) {
        return CompletableFuture.completedFuture(null);
    }
}

