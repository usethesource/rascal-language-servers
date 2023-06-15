package org.rascalmpl.vscode.lsp.dap;

import io.usethesource.vallang.ISourceLocation;
import org.rascalmpl.debug.DebugHandler;
import org.rascalmpl.debug.DebugMessageFactory;

import java.util.HashMap;

public class BreakpointsManager {
    private final HashMap<String, HashMap<ISourceLocation, Integer>> breakpoints;

    private static BreakpointsManager instance;

    public static BreakpointsManager getInstance(){
        if(instance == null){
            instance = new BreakpointsManager();
        }
        return instance;
    }

    private BreakpointsManager(){
        this.breakpoints = new HashMap<>();
    }

    public void clearBreakpointsOfFile(String filePath, DebugHandler handler){
        if(!breakpoints.containsKey(filePath)) return;
        for (ISourceLocation breakpointLocation : breakpoints.get(filePath).keySet()) {
            handler.processMessage(DebugMessageFactory.requestDeleteBreakpoint(breakpointLocation));
        }
        breakpoints.get(filePath).clear();
        System.out.println("Cleared breakpoints at "+filePath);
    }

    public void addBreakpoint(ISourceLocation location, int breakpointId, DebugHandler handler){
        String path = location.getPath();
        if(!breakpoints.containsKey(path)) breakpoints.put(path, new HashMap<>());
        breakpoints.get(path).put(location, breakpointId);
        handler.processMessage(DebugMessageFactory.requestSetBreakpoint(location));
        System.out.println("Add breakpoint at "+path);
    }

    public int getBreakpointID(ISourceLocation location){
        String path = location.getPath();
        if(!breakpoints.containsKey(path) || !breakpoints.get(path).containsKey(location)) return -1;
        return breakpoints.get(path).get(location);
    }
}
