package org.rascalmpl.vscode.xterm;

import static org.rascalmpl.debug.AbstractInterpreterEventTrigger.newInterpreterEventTrigger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.rascalmpl.debug.AbstractInterpreterEventTrigger;
import org.rascalmpl.debug.DebugHandler;
import org.rascalmpl.debug.IRascalEventListener;
import org.rascalmpl.debug.IRascalRuntimeInspection;
import org.rascalmpl.interpreter.Evaluator;
import org.rascalmpl.interpreter.result.IRascalResult;
import org.rascalmpl.repl.BaseREPL;
import org.rascalmpl.repl.BaseRascalREPL;
import org.rascalmpl.repl.RascalInterpreterREPL;
import org.rascalmpl.shell.RascalShell;
import org.rascalmpl.vscode.lsp.util.ModuleReloader;

@SuppressWarnings("restriction")
public class RascalXtermConnector implements XtermConnector {
    private BaseREPL shell;
    private final AtomicBoolean shellIsRunning = new AtomicBoolean(false);
    private InputStream stdIn;
    protected String project = "rascal";
    protected String module = null;
    protected String mode = "run";
    private ModuleReloader reloader;
    private int terminalHeight = 24;
    private int terminalWidth = 80;
  
	@Override
    public boolean isLocalEcho() {
        return false;
    }

    protected File getHistoryFile() throws IOException {
        File home = new File(System.getProperty("user.home"));
        File rascal = new File(home, ".rascal");
        if (!rascal.exists()) {
            rascal.mkdirs();
        }
        File historyFile = new File(rascal, ".repl-history-rascal");
        if (!historyFile.exists()) {
            historyFile.createNewFile();
        }
        return historyFile;
    }

    @Override
    public void connect(InputStream stdIn, OutputStream stdOut, Map<String,String> parameters) {
        String projectParam = parameters.get("project");
        if (projectParam != null && !projectParam.equals("null")) {
            this.project = projectParam;
        }
        this.stdIn = stdIn;

        RascalXtermRegistry.getInstance().register(this);
        
        Thread t = new Thread() {
            public void run() {
                try {
                    shell = constructREPL(stdIn, stdOut);

                    if (module != null) {
                        queueCommand("import " + module + ";");
                        queueCommand("main()");
                    }
                    
                    String version = RascalShell.getVersionNumber();
                    shell.getOutput().println("Rascal Version: " + version);
                
                    shellIsRunning.set(true);
                    shell.run();
                }
                catch (IOException | URISyntaxException e) {
                    Activator.log("terminal not connected", e);
                } 
                finally {
                    if (reloader != null) {
                        reloader.destroy();
                    }
                    
                    try {
                        if (debug()) {
                            launch.getDebugTarget().terminate();
                            launch.removeDebugTarget(launch.getDebugTarget());
                        }
                        ILaunchManager launchManager = DebugPlugin.getDefault().getLaunchManager();
                        launchManager.removeLaunch(launch);
                    } catch (DebugException e) {
                        Activator.log("problem disconnecting from debugger", e);
                    }
                }
            }
        };
        t.setName("Rascal REPL Runner");
        t.start();

    }

    public void setFocus() {
        RascalXtermRegistry.getInstance().setActive(this);
    }
    
    public String getProject() {
        return project;
    }

    public void queueCommand(String cmd) {
    	throw new RuntimeException("Not supported yet, we have to extend the xterm bridge to allow this");
    }
    
    private boolean debug() {
        return "debug".equals(mode);
    }

    @Override
    public void setTerminalSize(int newWidth, int newHeight) {
        terminalHeight = newHeight;
        terminalWidth = newWidth;
    }

    public int getHeight() {
        return terminalHeight;
    }

    public int getWidth() {
        return terminalWidth;
    }

    protected BaseREPL constructREPL(InputStream stdIn, OutputStream stdOut) throws IOException, URISyntaxException {
        BaseRascalREPL repl = constructRascalREPL(stdIn, stdOut);
        return new BaseREPL(repl, null, stdIn, stdOut, true, true, getHistoryFile(), new XtermTerminal(), new XtermIDEServices());
    }
    
    protected BaseRascalREPL constructRascalREPL(InputStream stdIn, OutputStream stdOut) throws IOException, URISyntaxException {
        return new RascalInterpreterREPL(stdIn, stdOut, true, true, false, getHistoryFile()) {
            private AbstractInterpreterEventTrigger eventTrigger;
            private DebugHandler debugHandler;
            
            @Override
            protected Evaluator constructEvaluator(InputStream input, OutputStream stdout, OutputStream stderr) {
                IProject ipr = project != null ? ResourcesPlugin.getWorkspace().getRoot().getProject(project) : null;
                if (ipr != null && !ipr.isOpen()) {
                    ipr = null;
                }

                Evaluator eval = ProjectEvaluatorFactory.getInstance().createProjectEvaluator(ipr, stderr, stdout);
                
                // TODO: this is a workaround to get access to a launch, but we'd rather
                // just get it from the terminal's properties
                launch = RascalXtermRegistry.getInstance().getLaunch();
                warnings = new WarningsToPrintWriter(new PrintWriter(ThreadSafeImpulseConsole.INSTANCE.getWriter()));
                reloader = new ModuleReloader(ipr, eval, warnings);
                eval.setMonitor(new RascalMonitor(new NullProgressMonitor(), warnings));

                if (debug()) {
                    initializeRascalDebugMode(eval);      
                    connectToEclipseDebugAPI(eval);
                    eventTrigger.fireSuspendByClientRequestEvent();
                }
                
                return eval;
            }
            
            @Override
            public void handleInput(String line, Map<String, InputStream> output, Map<String, String> metadata)
            		throws InterruptedException {
            	super.handleInput(line, output, metadata);
            	
            	for (String mimetype : output.keySet()) {
                    if (!mimetype.contains("html") && !mimetype.startsWith("image/")) {
                        continue;
                    }

            		new UIJob("Content") {
						@Override
						public IStatus runInUIThread(IProgressMonitor monitor) {
							try {
								String id = metadata.get("url");
								URL url = new URL(id);
                                IWebBrowser browser = WorkbenchBrowserSupport.getInstance().createBrowser(IWorkbenchBrowserSupport.AS_EDITOR, id, id, "This browser shows the latest web content produced by a Rascal terminal");
								browser.openURL(url);
							} catch (PartInitException | MalformedURLException e) {
								Activator.log("could not view HTML content", e);
							}
							
							return Status.OK_STATUS;
						}
					}.schedule();
                }
            }
            
            @Override
            public IRascalResult evalStatement(String statement, String lastLine)
                    throws InterruptedException {
                try {
                    if (debug()) {
                        synchronized(eval) {
                            eventTrigger.fireResumeByClientRequestEvent();
                        }
                    }
                    
                    Job job = new Job("Reloading modules") {
                        @Override
                        protected IStatus run(IProgressMonitor monitor) {
                            reloader.updateModules(monitor, warnings, Collections.emptySet());
                            return Status.OK_STATUS;
                        }
                    };
                    job.schedule();
                    job.join();
                 
                    return super.evalStatement(statement, lastLine);
                }
                finally {
                    if (debug() && !":quit".equals(statement.trim())) {
                        synchronized(eval) {
                            eventTrigger.fireSuspendByClientRequestEvent();
                        }
                    }
                }
            }
            
            private void connectToEclipseDebugAPI(IRascalRuntimeInspection eval) {
                try {
                    RascalDebugTarget  debugTarget = new RascalDebugTarget(eval, launch, eventTrigger, debugHandler);
                    launch.addDebugTarget(debugTarget);
                    debugTarget.breakpointManagerEnablementChanged(true);
                } catch (CoreException e) {
                    Activator.log("could not connect to debugger", e);
                    // otherwise no harm done, can continue
                }
             
            }
            
            private void initializeRascalDebugMode(Evaluator eval) {
                eventTrigger = newInterpreterEventTrigger(this, new CopyOnWriteArrayList<IRascalEventListener>());
                debugHandler = new DebugHandler();
                debugHandler.setEventTrigger(eventTrigger);
                debugHandler.setTerminateAction(new Runnable() {
                    @Override
                    public void run() {
                        disconnect();
                    }
                });         
                eval.addSuspendTriggerListener(debugHandler);
            }
        };
    }

    @Override
    public void initialize() throws Exception {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void disconnect() {
        try {
            if (shell != null) {
                try {
                    stdIn.close();
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
                shell.stop();
                shell = null;
            }
        } finally {
            RascalXtermRegistry.getInstance().unregister(this);
        }        
    }
}
