package org.rascalmpl.vscode.lsp.dap;

import io.usethesource.vallang.ISourceLocation;
import org.eclipse.lsp4j.debug.Source;
import org.rascalmpl.debug.DebugHandler;
import org.rascalmpl.debug.DebugMessageFactory;

import java.util.HashMap;

public class BreakpointsCollection {
    private final HashMap<String, HashMap<ISourceLocation, BreakpointInfo>> breakpoints;
    private final DebugHandler debugHandler;
    private int breakpointIDCounter = 0;

    public BreakpointsCollection(DebugHandler debugHandler){
        this.breakpoints = new HashMap<>();
        this.debugHandler = debugHandler;
    }

    public void clearBreakpointsOfFile(String filePath){
        if(!breakpoints.containsKey(filePath)) return;
        for (ISourceLocation breakpointLocation : breakpoints.get(filePath).keySet()) {
            debugHandler.processMessage(DebugMessageFactory.requestDeleteBreakpoint(breakpointLocation));
        }
        breakpoints.get(filePath).clear();
    }

    public void addBreakpoint(ISourceLocation location, Source source){
        String path = location.getPath();
        if(!breakpoints.containsKey(path)) breakpoints.put(path, new HashMap<>());
        BreakpointInfo breakpoint = new BreakpointInfo(++breakpointIDCounter, source);
        breakpoints.get(path).put(location, breakpoint);
        debugHandler.processMessage(DebugMessageFactory.requestSetBreakpoint(location));
    }

    public int getBreakpointID(ISourceLocation location){
        String path = location.getPath();
        if(!breakpoints.containsKey(path) || !breakpoints.get(path).containsKey(location)) return -1;
        return breakpoints.get(path).get(location).getId();
    }
}
