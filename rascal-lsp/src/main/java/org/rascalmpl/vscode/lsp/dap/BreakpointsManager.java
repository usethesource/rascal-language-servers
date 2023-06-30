package org.rascalmpl.vscode.lsp.dap;

import io.usethesource.vallang.ISourceLocation;
import org.eclipse.lsp4j.debug.Source;
import org.rascalmpl.debug.DebugHandler;
import org.rascalmpl.debug.DebugMessageFactory;

import java.util.HashMap;

public class BreakpointsManager {
    private final HashMap<String, HashMap<ISourceLocation, BreakpointInfo>> breakpoints;

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
    }

    public void addBreakpoint(ISourceLocation location, BreakpointInfo breakpointInfo, DebugHandler handler){
        String path = location.getPath();
        if(!breakpoints.containsKey(path)) breakpoints.put(path, new HashMap<>());
        breakpoints.get(path).put(location, breakpointInfo);
        handler.processMessage(DebugMessageFactory.requestSetBreakpoint(location));
    }

    public int getBreakpointID(ISourceLocation location){
        String path = location.getPath();
        if(!breakpoints.containsKey(path) || !breakpoints.get(path).containsKey(location)) return -1;
        return breakpoints.get(path).get(location).getId();
    }
}
