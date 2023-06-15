package org.rascalmpl.vscode.lsp.dap;

import io.usethesource.vallang.ISourceLocation;
import org.eclipse.lsp4j.debug.Breakpoint;
import org.eclipse.lsp4j.debug.BreakpointEventArguments;
import org.eclipse.lsp4j.debug.StoppedEventArguments;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.rascalmpl.debug.AbstractInterpreterEventTrigger;
import org.rascalmpl.debug.IRascalEventListener;
import org.rascalmpl.debug.RascalEvent;

public class RascalDebugEventTrigger extends AbstractInterpreterEventTrigger {

    private IDebugProtocolClient client;

    public RascalDebugEventTrigger(Object source) {
        super(source);
    }

    public void setDebugProtocolClient(IDebugProtocolClient client) {
        this.client = client;
    }

    @Override
    public void addRascalEventListener(IRascalEventListener listener) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'addRascalEventListener'");
    }

    @Override
    public void removeRascalEventListener(IRascalEventListener listener) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'removeRascalEventListener'");
    }

    @Override
    protected void fireEvent(RascalEvent event) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'fireEvent'");
    }

    @Override
    public void fireSuspendByBreakpointEvent(Object data) {
        ISourceLocation location = (ISourceLocation) data;
        int breakpointID = BreakpointsManager.getInstance().getBreakpointID(location);
        if(breakpointID < 0){
            System.out.println("Unknown breakpoint");
            return;
        }
        StoppedEventArguments stoppedEventArguments = new StoppedEventArguments();
        stoppedEventArguments.setThreadId(RascalDebugAdapterServer.mainThreadID);
        stoppedEventArguments.setDescription("Paused on breakpoint.");
        stoppedEventArguments.setReason("breakpoint");
        stoppedEventArguments.setHitBreakpointIds(new Integer[]{breakpointID});
        client.stopped(stoppedEventArguments);
	}

}
