package org.rascalmpl.vscode.lsp.dap;

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
import org.rascalmpl.debug.AbstractInterpreterEventTrigger;
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
import org.rascalmpl.vscode.lsp.rascal.RascalLanguageServer;
import org.rascalmpl.vscode.lsp.terminal.LSPTerminalREPL;
import org.rascalmpl.vscode.lsp.util.RascalServices;

import io.usethesource.vallang.IList;
import io.usethesource.vallang.INode;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValue;

public class RascalDebugAdapterServer implements IDebugProtocolServer {

    final public static int mainThreadID = 1;
    final private static int expensiveScopeMinSize = 100; // a scope is marked as expensive when there are more than xxx variables in it

    IDebugProtocolClient client;
    final private RascalDebugEventTrigger eventTrigger;
    final private DebugHandler debugHandler;
    final private Evaluator evaluator;
    final private SuspendedStateManager suspendedStateManager;
    public static ISourceLocation currentSuspensionLocation = null;


    public RascalDebugAdapterServer(AbstractInterpreterEventTrigger eventTrigger, DebugHandler debugHandler, Evaluator evaluator) {
        this.eventTrigger = (RascalDebugEventTrigger) eventTrigger;
        this.debugHandler = debugHandler;
        this.evaluator = evaluator;
        this.suspendedStateManager = new SuspendedStateManager(evaluator);
        this.eventTrigger.setSuspendedStateManager(suspendedStateManager);
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
            capabilities.setSupportsStepBack(false);
            capabilities.setSupportsRestartFrame(false);
            capabilities.setSupportsSetVariable(false);
            capabilities.setSupportsRestartRequest(false);

            return capabilities;
        });

        return future;
    }


    // TODO: Breakpoints not working anymore after editing the source file
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
                BreakpointsManager.getInstance().clearBreakpointsOfFile(loc.getPath(), debugHandler);
                for (SourceBreakpoint breakpoint : args.getBreakpoints()) {
                    ITree treeBreakableLocation = locateBreakableTree(parseTree, breakpoint.getLine());
                    if(treeBreakableLocation != null) {
                        ISourceLocation breakableLocation = TreeAdapter.getLocation(treeBreakableLocation);
                        BreakpointsManager.getInstance().addBreakpoint(breakableLocation, new BreakpointInfo(i, args.getSource()), debugHandler);
                    }
                    Breakpoint b = new Breakpoint();
                    b.setId(i);
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

    //TODO : make better completablefuture response
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

    //TODO : make better completablefuture response
    @Override
    public CompletableFuture<Void> configurationDone(ConfigurationDoneArguments args) {
        CompletableFuture<Void> future = CompletableFuture.supplyAsync(() -> {
            return null;
        });

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
        CompletableFuture<ThreadsResponse> future = CompletableFuture.supplyAsync(() -> {
            ThreadsResponse response = new ThreadsResponse();
            Thread t = new Thread();
            t.setId(mainThreadID);
            t.setName("Main Thread");
            response.setThreads(new Thread[]{
                t
            });
            return response;
        });

        return future;
    }

    //TODO : make better completablefuture response
    //TODO: fix main stackFrame source issue
    @Override
    public CompletableFuture<StackTraceResponse> stackTrace(StackTraceArguments args) {
        if(args.getThreadId() != mainThreadID) {
            return CompletableFuture.completedFuture(new StackTraceResponse());
        }
        StackTraceResponse response = new StackTraceResponse();
        IRascalFrame[] stackFrames = suspendedStateManager.getCurrentStackFrames();
        response.setTotalFrames(stackFrames.length);
        StackFrame[] stackFramesResponse = new StackFrame[stackFrames.length];
        IRascalFrame currentFrame = suspendedStateManager.getCurrentStackFrame();
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
            ISourceLocation loc = f.getCallerLocation();
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

        return CompletableFuture.completedFuture(response);
    }

    public Source getSourceFromISourceLocation(ISourceLocation loc) {
        //TODO: handle location conversion to source
        Source source = new Source();
        source.setName(loc.getPath());
        source.setPath(loc.getPath());
        //TODO: handle source reference
        source.setSourceReference(0);
        return source;
    }

    //TODO : make better completablefuture response
    @Override
    public CompletableFuture<ScopesResponse> scopes(ScopesArguments args) {
        int frameId = args.getFrameId();

        List<Scope> scopes = new ArrayList<>();

        IRascalFrame frame = suspendedStateManager.getCurrentStackFrames()[frameId];
        ScopesResponse response = new ScopesResponse();
        Scope scopeLocals = new Scope();
        scopeLocals.setName("Locals");
        scopeLocals.setNamedVariables(frame.getFrameVariables().size());
        scopeLocals.setPresentationHint("locals");
        scopeLocals.setExpensive(frame.getFrameVariables().size()>expensiveScopeMinSize);
        scopeLocals.setVariablesReference(suspendedStateManager.addScope(frame));
        scopes.add(scopeLocals);

        for(String importName : frame.getImports()){
            IRascalFrame module = evaluator.getModule(importName);

            if(module != null && module.getFrameVariables().size() > 0){
                Scope scopeModule = new Scope();
                scopeModule.setName("Module " + importName);
                scopeModule.setNamedVariables(module.getFrameVariables().size());
                scopeModule.setPresentationHint("module");
                scopeModule.setExpensive(module.getFrameVariables().size()>expensiveScopeMinSize);
                scopeModule.setVariablesReference(suspendedStateManager.addScope(module));
                scopes.add(scopeModule);
            }
        }

        response.setScopes(scopes.toArray(new Scope[scopes.size()]));
        return CompletableFuture.completedFuture(response);
    }

    //TODO : make better completablefuture response
    @Override
    public CompletableFuture<VariablesResponse> variables(VariablesArguments args) {
        int reference = args.getVariablesReference();
        int minIndex = args.getStart() != null ? args.getStart() : 0;
        int maxCount = args.getCount() != null ? args.getCount() : -1;

        VariablesResponse response = new VariablesResponse();
        List<ReferencedVariable> variables = suspendedStateManager.getVariablesByParentReferenceID(reference, minIndex, maxCount);
        Variable[] variablesResponse = new Variable[variables.size()];
        int i = 0;
        for(ReferencedVariable var : variables){
            Variable variable = new Variable();
            variable.setName(var.getName());
            variable.setType(var.getType().toString());
            variable.setValue(var.getDisplayValue());
            variable.setVariablesReference(var.getReferenceID());
            variablesResponse[i] = variable;
            i++;
        }

        response.setVariables(variablesResponse);
        return CompletableFuture.completedFuture(response);
    }

    @Override
    public CompletableFuture<ContinueResponse> continue_(ContinueArguments args) {
        ContinueResponse response = new ContinueResponse();
        response.setAllThreadsContinued(true);

        debugHandler.processMessage(DebugMessageFactory.requestResumption());

        return CompletableFuture.completedFuture(response);
    }

    @Override
    public CompletableFuture<Void> next(NextArguments args) {
        debugHandler.processMessage(DebugMessageFactory.requestStepOver());

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> cancel(CancelArguments args) {
        System.out.println("cancel request");
        return IDebugProtocolServer.super.cancel(args);
    }

    @Override
    public CompletableFuture<Void> restart(RestartArguments args) {
        System.out.println("restart request");
        return IDebugProtocolServer.super.restart(args);
    }

    @Override
    public CompletableFuture<Void> disconnect(DisconnectArguments args) {
        debugHandler.processMessage(DebugMessageFactory.requestTermination());
        RascalDebugAdapterLauncher.stopDebugServer();

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> terminate(TerminateArguments args) {
        System.out.println("terminate request");
        return IDebugProtocolServer.super.terminate(args);
    }

    @Override
    public CompletableFuture<BreakpointLocationsResponse> breakpointLocations(BreakpointLocationsArguments args) {
        System.out.println("breakpoint locations request");
        return IDebugProtocolServer.super.breakpointLocations(args);
    }

    @Override
    public CompletableFuture<SetFunctionBreakpointsResponse> setFunctionBreakpoints(SetFunctionBreakpointsArguments args) {
        System.out.println("set function breakpoints request");
        return IDebugProtocolServer.super.setFunctionBreakpoints(args);
    }

    @Override
    public CompletableFuture<SetExceptionBreakpointsResponse> setExceptionBreakpoints(SetExceptionBreakpointsArguments args) {
        System.out.println("set exception breakpoints request");
        return IDebugProtocolServer.super.setExceptionBreakpoints(args);
    }

    @Override
    public CompletableFuture<DataBreakpointInfoResponse> dataBreakpointInfo(DataBreakpointInfoArguments args) {
        System.out.println("data breakpoint info request");
        return IDebugProtocolServer.super.dataBreakpointInfo(args);
    }

    @Override
    public CompletableFuture<SetDataBreakpointsResponse> setDataBreakpoints(SetDataBreakpointsArguments args) {
        System.out.println("set data breakpoints request");
        return IDebugProtocolServer.super.setDataBreakpoints(args);
    }

    @Override
    public CompletableFuture<SetInstructionBreakpointsResponse> setInstructionBreakpoints(SetInstructionBreakpointsArguments args) {
        System.out.println("set instruction breakpoints request");
        return IDebugProtocolServer.super.setInstructionBreakpoints(args);
    }

    @Override
    public CompletableFuture<Void> stepIn(StepInArguments args) {
        debugHandler.processMessage(DebugMessageFactory.requestStepInto());

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> stepOut(StepOutArguments args) {
        //TODO: implementation of step out request.
        final Logger logger = LogManager.getLogger(RascalLanguageServer.class);
        logger.debug("Step out request not implemented in debug adapter");

        debugHandler.processMessage(DebugMessageFactory.requestStepOver());

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> stepBack(StepBackArguments args) {
        System.out.println("step back request");
        return IDebugProtocolServer.super.stepBack(args);
    }

    @Override
    public CompletableFuture<Void> reverseContinue(ReverseContinueArguments args) {
        System.out.println("reverse continue request");
        return IDebugProtocolServer.super.reverseContinue(args);
    }

    @Override
    public CompletableFuture<Void> restartFrame(RestartFrameArguments args) {
        System.out.println("restart frame request");
        return IDebugProtocolServer.super.restartFrame(args);
    }

    @Override
    public CompletableFuture<Void> goto_(GotoArguments args) {
        System.out.println("goto request");
        return IDebugProtocolServer.super.goto_(args);
    }

    @Override
    public CompletableFuture<Void> pause(PauseArguments args) {
        debugHandler.processMessage(DebugMessageFactory.requestSuspension());

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<SetVariableResponse> setVariable(SetVariableArguments args) {
        System.out.println("set variable request");
        return IDebugProtocolServer.super.setVariable(args);
    }

    @Override
    public CompletableFuture<SourceResponse> source(SourceArguments args) {
        System.out.println("source request");
        return IDebugProtocolServer.super.source(args);
    }

    @Override
    public CompletableFuture<Void> terminateThreads(TerminateThreadsArguments args) {
        System.out.println("terminate threads request");
        return IDebugProtocolServer.super.terminateThreads(args);
    }

    @Override
    public CompletableFuture<ModulesResponse> modules(ModulesArguments args) {
        System.out.println("modules request");
        return IDebugProtocolServer.super.modules(args);
    }

    @Override
    public CompletableFuture<LoadedSourcesResponse> loadedSources(LoadedSourcesArguments args) {
        System.out.println("loaded sources request");
        return IDebugProtocolServer.super.loadedSources(args);
    }

    @Override
    public CompletableFuture<EvaluateResponse> evaluate(EvaluateArguments args) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<SetExpressionResponse> setExpression(SetExpressionArguments args) {
        System.out.println("set expression request");
        return IDebugProtocolServer.super.setExpression(args);
    }

    @Override
    public CompletableFuture<StepInTargetsResponse> stepInTargets(StepInTargetsArguments args) {
        System.out.println("step in targets request");
        return IDebugProtocolServer.super.stepInTargets(args);
    }

    @Override
    public CompletableFuture<GotoTargetsResponse> gotoTargets(GotoTargetsArguments args) {
        System.out.println("goto targets request");
        return IDebugProtocolServer.super.gotoTargets(args);
    }

    @Override
    public CompletableFuture<CompletionsResponse> completions(CompletionsArguments args) {
        System.out.println("completions request");
        return IDebugProtocolServer.super.completions(args);
    }

    @Override
    public CompletableFuture<ExceptionInfoResponse> exceptionInfo(ExceptionInfoArguments args) {
        System.out.println("exception info request");
        return IDebugProtocolServer.super.exceptionInfo(args);
    }

    @Override
    public CompletableFuture<ReadMemoryResponse> readMemory(ReadMemoryArguments args) {
        System.out.println("read memory request");
        return IDebugProtocolServer.super.readMemory(args);
    }

    @Override
    public CompletableFuture<WriteMemoryResponse> writeMemory(WriteMemoryArguments args) {
        System.out.println("write memory request");
        return IDebugProtocolServer.super.writeMemory(args);
    }

    @Override
    public CompletableFuture<DisassembleResponse> disassemble(DisassembleArguments args) {
        System.out.println("disassemble request");
        return IDebugProtocolServer.super.disassemble(args);
    }
}

