package org.rascalmpl.vscode.lsp.dap;

import io.usethesource.vallang.ISourceLocation;
import org.eclipse.lsp4j.debug.StoppedEventArguments;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.rascalmpl.debug.AbstractInterpreterEventTrigger;
import org.rascalmpl.debug.IRascalEventListener;
import org.rascalmpl.debug.RascalEvent;

public class RascalDebugEventTrigger extends AbstractInterpreterEventTrigger {

    private IDebugProtocolClient client;
    private final SuspendedState suspendedState;
    private final BreakpointsCollection breakpointsCollection;

    public RascalDebugEventTrigger(Object source, BreakpointsCollection breakpointsCollection, SuspendedState suspendedState) {
        super(source);
        this.breakpointsCollection = breakpointsCollection;
        this.suspendedState = suspendedState;
    }

    public void setDebugProtocolClient(IDebugProtocolClient client) {
        this.client = client;
    }

    @Override
    public void addRascalEventListener(IRascalEventListener listener) {
        throw new UnsupportedOperationException("Unimplemented method 'addRascalEventListener'");
    }

    @Override
    public void removeRascalEventListener(IRascalEventListener listener) {
        throw new UnsupportedOperationException("Unimplemented method 'removeRascalEventListener'");
    }

    @Override
    protected void fireEvent(RascalEvent event) {
        throw new UnsupportedOperationException("Unimplemented method 'fireEvent'");
    }

    @Override
    public void fireSuspendByBreakpointEvent(Object data) {
        ISourceLocation location = (ISourceLocation) data;
        int breakpointID = breakpointsCollection.getBreakpointID(location);
        if(breakpointID < 0){
            return;
        }

        suspendedState.suspended();

        StoppedEventArguments stoppedEventArguments = new StoppedEventArguments();
        stoppedEventArguments.setThreadId(RascalDebugAdapter.mainThreadID);
        stoppedEventArguments.setDescription("Paused on breakpoint.");
        stoppedEventArguments.setReason("breakpoint");
        stoppedEventArguments.setHitBreakpointIds(new Integer[]{breakpointID});
        client.stopped(stoppedEventArguments);
	}

    @Override
    public void fireResumeEvent(RascalEvent.Detail detail) {
        suspendedState.resumed();
    }

    @Override
    public void fireResumeByStepOverEvent() {
        suspendedState.resumed();
    }

    @Override
    public void fireResumeByStepIntoEvent() {
        suspendedState.resumed();
    }

    @Override
    public void fireSuspendByStepEndEvent() {
        //TODO: issue with step over last instruction
        suspendedState.suspended();

        StoppedEventArguments stoppedEventArguments = new StoppedEventArguments();
        stoppedEventArguments.setThreadId(RascalDebugAdapter.mainThreadID);
        stoppedEventArguments.setDescription("Paused on step end.");
        stoppedEventArguments.setReason("step");
        client.stopped(stoppedEventArguments);
    }

    @Override
    public void fireSuspendByClientRequestEvent() {
        suspendedState.suspended();

        StoppedEventArguments stoppedEventArguments = new StoppedEventArguments();
        stoppedEventArguments.setThreadId(RascalDebugAdapter.mainThreadID);
        stoppedEventArguments.setDescription("Paused by client.");
        stoppedEventArguments.setReason("pause");
        client.stopped(stoppedEventArguments);
    }

    @Override
    public void fireSuspendEvent(RascalEvent.Detail detail) {}
}
