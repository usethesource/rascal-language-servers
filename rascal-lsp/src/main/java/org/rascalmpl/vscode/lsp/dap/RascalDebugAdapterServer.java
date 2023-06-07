package org.rascalmpl.vscode.lsp.dap;

import org.eclipse.lsp4j.debug.*;
import org.eclipse.lsp4j.debug.Thread;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class RascalDebugAdapterServer implements IDebugProtocolServer {

    IDebugProtocolClient client;

    public void connect(IDebugProtocolClient client) {
        this.client = client;
    }
    @Override
    public CompletableFuture<Capabilities> initialize(InitializeRequestArguments args) {
        System.out.println("initialize message received!");

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
        System.out.println("setBreakpoints message received!");
        System.out.println(" - path: " + args.getSource().getPath());
        CompletableFuture<SetBreakpointsResponse> future = CompletableFuture.supplyAsync(() -> {
            SetBreakpointsResponse response = new SetBreakpointsResponse();
            Breakpoint[] breakpoints = new Breakpoint[args.getBreakpoints().length];
            int i = 0;
            for (SourceBreakpoint breakpoint : args.getBreakpoints()) {
                System.out.println(" - breakpoint line : " + breakpoint.getLine());
                Breakpoint b = new Breakpoint();
                b.setLine(breakpoint.getLine());
                b.setColumn(breakpoint.getColumn());
                b.setVerified(true);
                breakpoints[i] = b;
                i++;
            }
            response.setBreakpoints(breakpoints);
            return response;
        });
        return future;
    }

    @Override
    public CompletableFuture<Void> attach(Map<String, Object> args) {
        System.out.println("attach message received!");
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
        System.out.println("configurationDone message received!");
        CompletableFuture<Void> future = CompletableFuture.supplyAsync(() -> {
            return null;
        });

        return future;
    }

    @Override
    public CompletableFuture<ThreadsResponse> threads() {
        System.out.println("threads message received!");
        CompletableFuture<ThreadsResponse> future = CompletableFuture.supplyAsync(() -> {
            ThreadsResponse response = new ThreadsResponse();
            Thread t = new Thread();
            t.setId(1);
            t.setName("temporary");
            response.setThreads(new Thread[]{
                t
            });
            return response;
        });
        return future;
    }
}

