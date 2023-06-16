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

        // TODO: better way to keep track of current suspended location
        RascalDebugAdapterServer.currentSuspensionLocation = location;

        StoppedEventArguments stoppedEventArguments = new StoppedEventArguments();
        stoppedEventArguments.setThreadId(RascalDebugAdapterServer.mainThreadID);
        stoppedEventArguments.setDescription("Paused on breakpoint.");
        stoppedEventArguments.setReason("breakpoint");
        stoppedEventArguments.setHitBreakpointIds(new Integer[]{breakpointID});
        client.stopped(stoppedEventArguments);
	}

    @Override
    public void fireResumeEvent(RascalEvent.Detail detail) {
        // TODO: implement logic on resume event
    }

    @Override
    public void fireResumeByStepOverEvent() {
        // TODO: implement logic on step over
        System.out.println("Step over event");
    }

    @Override
    public void fireSuspendByStepEndEvent() {
        //TODO: implement logic on step end
        System.out.println("Step end event");
        StoppedEventArguments stoppedEventArguments = new StoppedEventArguments();
        stoppedEventArguments.setThreadId(RascalDebugAdapterServer.mainThreadID);
        stoppedEventArguments.setDescription("Paused on step end.");
        stoppedEventArguments.setReason("step");
        client.stopped(stoppedEventArguments);
    }

    @Override
    public void fireSuspendEvent(RascalEvent.Detail detail) {
        //TODO implement logic on suspend event
        System.out.println("Suspend event : " + detail);
    }
}
