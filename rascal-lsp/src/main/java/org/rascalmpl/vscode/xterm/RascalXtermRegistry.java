package org.rascalmpl.eclipse.views.xterm;

import java.lang.ref.WeakReference;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.ui.progress.UIJob;
import org.rascalmpl.eclipse.Activator;
import org.rascalmpl.eclipse.IRascalResources;

public class RascalXtermRegistry {
    private static final class Instance {
        public static final RascalXtermRegistry manager = new RascalXtermRegistry();
    }
    
    public static RascalXtermRegistry getInstance() {
        return Instance.manager;
    }
    
    private final List<WeakReference<RascalXtermConnector>> connectors = new LinkedList<>();
    private WeakReference<RascalXtermConnector> focussed = null;
    private WeakReference<ILaunch> launch;
    
    public ILaunch getLaunch() {
        return launch != null ? launch.get() : null;
    }
    
    public void register(RascalXtermConnector connector) {
        if (!connectors.stream().anyMatch(x -> x.get() == connector)) {
            connectors.add(new WeakReference<>(connector));
        }
    }
    
    public void unregister(RascalXtermConnector connector) {
        for (int i = 0; i < connectors.size(); i++) {
            if (connectors.get(i).get() == connector) {
                connectors.remove(i);
                break;
            }
        }
    }
    
    public RascalXtermConnector findByProject(String project) {
        assert project != null;
        return connectors.stream().map(x -> x.get()).filter(x -> x != null && project.equals(x.getProject())).findFirst().orElse(null);
    }

    public void setActive(RascalXtermConnector connector) {
        this.focussed = new WeakReference<>(connector);
    }
    
    public RascalXtermConnector getActiveConnector(String project) {
        if (project != null) {
            RascalXtermConnector f = focussed != null ? focussed.get() : null;
            
            if (f != null && connectors.stream().anyMatch(x -> x.get() == f) && f.getProject().equals(project)) {
                return f;
            }
            
            return findByProject(project);
        }
        else if (connectors.size() > 0) {
            return connectors.stream().map(x -> x.get()).filter(x -> x != null).findFirst().orElse(null);
        }
        else {
            return null;
        }
    }
    
    public void queueCommand(String project, String cmd) {
        RascalXtermConnector connector = RascalXtermRegistry.getInstance().getActiveConnector(project);
        if (connector != null) {
            connector.setFocus();
            connector.queueCommand(cmd);
        }
        else {
            Activator.log("No terminal available for project " + project, new NullPointerException());
        }
    }
    
    public void queueCommands(String project, Iterable<String> cmds) {
        RascalXtermConnector connector = RascalXtermRegistry.getInstance().getActiveConnector(project);
        if (connector != null) {
            connector.setFocus();
            StringBuilder b = new StringBuilder();
            for (String cmd : cmds) {
                b.append(cmd);
                b.append('\n');
            }
            connector.queueCommand(b.toString());
        }
        else {
            Activator.log("No terminal available for project " + project, new NullPointerException());
        }
    }
    
    public static void launchTerminal(String project, String mode) {
        launchTerminal(project, null, mode);
    }
    
    public static void launchTerminal(String project, String module, String mode) {
        Job job = new UIJob("Launching console") {
            @Override
            public IStatus runInUIThread(IProgressMonitor monitor) {
                try {
                    ILaunchManager launchManager = DebugPlugin.getDefault().getLaunchManager();
                    ILaunchConfigurationType type = launchManager.getLaunchConfigurationType(IRascalResources.LAUNCHTYPE);
                    ILaunchConfigurationWorkingCopy launch = type.newInstance(null, "Rascal Project Terminal Launch [" + project + "]");
                    DebugUITools.setLaunchPerspective(type, mode, IDebugUIConstants.PERSPECTIVE_NONE);
                    launch.setAttribute(IRascalResources.ATTR_RASCAL_PROJECT, project);
                    launch.setAttribute(IRascalResources.ATTR_RASCAL_PROGRAM, module);
                    getInstance().setLaunch(launch.launch(mode, monitor));
                } 
                catch (CoreException e) {
                    Activator.getInstance().logException("could not start a terminal for " + project, e);
                }
                return Status.OK_STATUS;
            }
        };
        
        job.setUser(true);
        job.schedule();
    }
    
    private void setLaunch(ILaunch launch) {
       this.launch = new WeakReference<ILaunch>(launch);
    }
}
