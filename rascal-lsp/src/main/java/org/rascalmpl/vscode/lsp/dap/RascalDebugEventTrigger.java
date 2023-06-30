package org.rascalmpl.vscode.lsp.dap;

import io.usethesource.vallang.ISourceLocation;
import org.eclipse.lsp4j.debug.Breakpoint;
import org.eclipse.lsp4j.debug.BreakpointEventArguments;
import org.eclipse.lsp4j.debug.OutputEventArguments;
import org.eclipse.lsp4j.debug.StoppedEventArguments;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.rascalmpl.debug.AbstractInterpreterEventTrigger;
import org.rascalmpl.debug.IRascalEventListener;
import org.rascalmpl.debug.RascalEvent;

public class RascalDebugEventTrigger extends AbstractInterpreterEventTrigger {

    private IDebugProtocolClient client;
    private SuspendedStateManager suspendedStateManager;

    public RascalDebugEventTrigger(Object source) {
        super(source);
    }

    public void setDebugProtocolClient(IDebugProtocolClient client) {
        this.client = client;
    }
    public void setSuspendedStateManager(SuspendedStateManager suspendedStateManager) {
        this.suspendedStateManager = suspendedStateManager;
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
        int breakpointID = BreakpointsManager.getInstance().getBreakpointID(location);
        if(breakpointID < 0){
            return;
        }

        suspendedStateManager.suspended();

        StoppedEventArguments stoppedEventArguments = new StoppedEventArguments();
        stoppedEventArguments.setThreadId(RascalDebugAdapterServer.mainThreadID);
        stoppedEventArguments.setDescription("Paused on breakpoint.");
        stoppedEventArguments.setReason("breakpoint");
        stoppedEventArguments.setHitBreakpointIds(new Integer[]{breakpointID});
        client.stopped(stoppedEventArguments);
	}

    @Override
    public void fireResumeEvent(RascalEvent.Detail detail) {
        suspendedStateManager.resumed();
    }

    @Override
    public void fireResumeByStepOverEvent() {
        suspendedStateManager.resumed();
    }

    @Override
    public void fireResumeByStepIntoEvent() {
        suspendedStateManager.resumed();
    }

    @Override
    public void fireSuspendByStepEndEvent() {
        suspendedStateManager.suspended();

        StoppedEventArguments stoppedEventArguments = new StoppedEventArguments();
        stoppedEventArguments.setThreadId(RascalDebugAdapterServer.mainThreadID);
        stoppedEventArguments.setDescription("Paused on step end.");
        stoppedEventArguments.setReason("step");
        client.stopped(stoppedEventArguments);
    }

    @Override
    public void fireSuspendByClientRequestEvent() {
        suspendedStateManager.suspended();

        StoppedEventArguments stoppedEventArguments = new StoppedEventArguments();
        stoppedEventArguments.setThreadId(RascalDebugAdapterServer.mainThreadID);
        stoppedEventArguments.setDescription("Paused by client.");
        stoppedEventArguments.setReason("pause");
        client.stopped(stoppedEventArguments);
    }

    @Override
    public void fireSuspendEvent(RascalEvent.Detail detail) {}
}
