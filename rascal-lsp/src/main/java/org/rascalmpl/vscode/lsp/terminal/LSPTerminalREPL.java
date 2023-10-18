/*
 * Copyright (c) 2018-2023, NWO-I CWI and Swat.engineering
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.rascalmpl.vscode.lsp.terminal;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.rascalmpl.ideservices.IDEServices;
import org.rascalmpl.interpreter.Evaluator;
import org.rascalmpl.interpreter.env.GlobalEnvironment;
import org.rascalmpl.interpreter.env.ModuleEnvironment;
import org.rascalmpl.interpreter.load.StandardLibraryContributor;
import org.rascalmpl.interpreter.result.IRascalResult;
import org.rascalmpl.interpreter.result.ResultFactory;
import org.rascalmpl.interpreter.utils.RascalManifest;
import org.rascalmpl.library.util.PathConfig;
import org.rascalmpl.library.util.PathConfig.RascalConfigMode;
import org.rascalmpl.repl.BaseREPL;
import org.rascalmpl.repl.ILanguageProtocol;
import org.rascalmpl.repl.RascalInterpreterREPL;
import org.rascalmpl.shell.ShellEvaluatorFactory;
import org.rascalmpl.uri.ISourceLocationWatcher.ISourceLocationChanged;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.uri.classloaders.SourceLocationClassLoader;
import org.rascalmpl.values.ValueFactoryFactory;
import org.rascalmpl.vscode.lsp.dap.DebugSocketServer;
import org.rascalmpl.vscode.lsp.uri.ProjectURIResolver;
import org.rascalmpl.vscode.lsp.uri.TargetURIResolver;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.impl.VSCodeVFSClient;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IValueFactory;
import io.usethesource.vallang.io.StandardTextWriter;
import jline.Terminal;
import jline.TerminalFactory;
import jline.internal.Configuration;

/**
 * This class runs a Rascal terminal REPL that
 * connects to a running LSP server instance to
 * provide IDE feature to the user of a terminal instance.
 */
public class LSPTerminalREPL extends BaseREPL {
    private static final InputStream stdin = System.in;
    private static final OutputStream stderr = System.err;
    private static final OutputStream stdout = System.out;
    private static final boolean prettyPrompt = true;
    private static final boolean allowColors = true;

    public LSPTerminalREPL(Terminal terminal, IDEServices services) throws IOException, URISyntaxException {
        super(makeInterpreter(terminal, services), null, stdin, stderr, stdout, true, terminal.isAnsiSupported(), getHistoryFile(), terminal, services);
    }

    private static String getRascalLspVersion() {
        try {
            return new Manifest(URIResolverRegistry.getInstance()
                .getInputStream(URIUtil.correctLocation("lib", "rascal-lsp", "META-INF/MANIFEST.MF")))
                .getMainAttributes().getValue("Specification-Version");
        } catch (IOException e) {
            return "Unknown";
        }
    }

    private static boolean isUTF8() {
        String lang = System.getenv("LANG");
        return lang != null && lang.contains("UTF-8");
    }

    private static ILanguageProtocol makeInterpreter(Terminal terminal, final IDEServices services) throws IOException, URISyntaxException {
        if (Configuration.isWindows() && isUTF8()) {
            // vs code doesn't properly set the codepage for the terminal process
            // but xterm.js and vs code expect us to print in utf8, so we have to quickly set the
            // codepage ourself, just to make sure we are not getting the default
            // that the user has (mostly some old codepoint that breaks any unicode character)
            try {
                // we want to call: Kernel32::SetConsoleOutputCP
                // but that class is not available outside of windows,
                // so we have to dynamically load it, so the compiler & classloaders
                // don't break on linux & osx
                var kernelClass = Configuration.class.getClassLoader().loadClass("org.fusesource.jansi.internal.Kernel32");
                var setOutputCp = kernelClass.getMethod("SetConsoleOutputCP", int.class);
                setOutputCp.invoke(null, 65001);

            } catch (ClassNotFoundException | NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                System.err.println("Error loading Kernel32 class, special characters will most likely not work");
                System.err.println(ex);
            }
        }
        RascalInterpreterREPL repl =
            new RascalInterpreterREPL(prettyPrompt, allowColors, getHistoryFile()) {
                private final Set<String> dirtyModules = ConcurrentHashMap.newKeySet();
                private DebugSocketServer debugServer;
                private final Pattern debuggingCommandPattern = Pattern.compile("^\\s*:set\\s+debugging\\s+(true|false)");

                @Override
                protected SortedSet<String> getCommandLineOptions() {
                    SortedSet<String> options = super.getCommandLineOptions();
                    options.add("debugging");
                    return options;
                }

                @Override
                public IRascalResult evalStatement(String statement, String lastLine) throws InterruptedException {
                    Matcher matcher = debuggingCommandPattern.matcher(statement);
                    if (matcher.find()) {
                        if(matcher.group(1).equals("true")){
                            if(!debugServer.isClientConnected()){
                                ((TerminalIDEClient) services).startDebuggingSession(debugServer.getPort());
                                getOutputWriter().println("Debugging session started.");
                                return ResultFactory.nothing();
                            }
                            getOutputWriter().println("Debugging session was already running.");
                            return ResultFactory.nothing();
                        }
                        if(debugServer.isClientConnected()){
                            debugServer.terminateDebugSession();
                            getOutputWriter().println("Debugging session stopped.");
                            return ResultFactory.nothing();
                        }
                        getOutputWriter().println("Debugging session was not running.");
                        return ResultFactory.nothing();
                    }

                    return super.evalStatement(statement, lastLine);
                }

                @Override
                protected Evaluator constructEvaluator(InputStream input, OutputStream stdout, OutputStream stderr, IDEServices services) {
                    GlobalEnvironment heap = new GlobalEnvironment();
                    ModuleEnvironment root = heap.addModule(new ModuleEnvironment(ModuleEnvironment.SHELL_MODULE, heap));
                    IValueFactory vf = ValueFactoryFactory.getValueFactory();
                    Evaluator evaluator = new Evaluator(vf, input, stderr, stdout, root, heap);
                    evaluator.addRascalSearchPathContributor(StandardLibraryContributor.getInstance());
                    evaluator.addRascalSearchPath(URIUtil.correctLocation("lib", "rascal-lsp", ""));

                    URIResolverRegistry reg = URIResolverRegistry.getInstance();

                    ISourceLocation projectDir = ShellEvaluatorFactory.inferProjectRoot(new File(System.getProperty("user.dir")));
                    String projectName = "unknown-project";
                    if (projectDir != null) {
                        projectName = new RascalManifest().getProjectName(projectDir);
                    }

                    reg.registerLogical(new ProjectURIResolver(services::resolveProjectLocation));
                    reg.registerLogical(new TargetURIResolver(services::resolveProjectLocation));

                    debugServer = new DebugSocketServer(evaluator, (TerminalIDEClient) services);

                    try {
                        PathConfig pcfg;
                        if (projectDir != null) {
                            pcfg = PathConfig.fromSourceProjectRascalManifest(projectDir, RascalConfigMode.INTERPETER);
                        }
                        else {
                            // TODO: see if we need to do something special for RascalConfigMode flag for this default path config
                            pcfg = new PathConfig();
                        }
                        evaluator.getErrorPrinter().println("Rascal Version: " + RascalManifest.getRascalVersionNumber());
                        evaluator.getErrorPrinter().println("Rascal-lsp Version: " + getRascalLspVersion());
                        new StandardTextWriter(true).write(pcfg.asConstructor(), evaluator.getErrorPrinter());

                        if (services instanceof TerminalIDEClient) {
                            ((TerminalIDEClient) services).registerErrorPrinter(evaluator.getErrorPrinter());
                        }

                        for (IValue path : pcfg.getSrcs()) {
                            evaluator.addRascalSearchPath((ISourceLocation) path);
                            reg.watch((ISourceLocation) path, true, d -> sourceLocationChanged(pcfg, d));
                        }

                        ClassLoader cl = new SourceLocationClassLoader(
                            pcfg.getClassloaders()
                                .append(URIUtil.correctLocation("lib", "rascal",""))
                                .append(URIUtil.correctLocation("lib", "rascal-lsp",""))
                                .append(URIUtil.correctLocation("target", projectName, "")),
                            ClassLoader.getSystemClassLoader()
                        );

                        evaluator.addClassLoader(cl);
                    }
                    catch (IOException e) {
                        e.printStackTrace(new PrintStream(stderr));
                    }

                     // this is very important since it hooks up the languageRegistration feature
                    evaluator.setMonitor(services);

                    return evaluator;
                }

                private void sourceLocationChanged(PathConfig pcfg, ISourceLocationChanged d) {
                    for (IValue src : pcfg.getSrcs()) {
                        ISourceLocation srcPath = (ISourceLocation) src;

                        if (URIUtil.isParentOf(srcPath, d.getLocation())) {
                            ISourceLocation relative = URIUtil.relativize(srcPath, d.getLocation());
                            relative = URIUtil.removeExtension(relative);

                            String modName = relative.getPath();
                            if (modName.startsWith("/")) {
                                modName = modName.substring(1);
                            }
                            modName = modName.replaceAll("/", "::");
                            modName = modName.replaceAll("\\\\", "::");
                            dirtyModules.add(modName);
                        }
                    }
                }

                @Override
                public void handleInput(String line, Map<String, InputStream> output, Map<String, String> metadata)
                    throws InterruptedException {
                        try {
                            Set<String> changes = new HashSet<>();
                            changes.addAll(dirtyModules);
                            dirtyModules.removeAll(changes);
                            eval.reloadModules(eval.getMonitor(), changes, URIUtil.rootLocation("reloader"));
                        }
                        catch (Throwable e) {
                            getErrorWriter().println("Error during reload: " + e.getMessage());
                            // in which case the dirty modules are not cleared and the system will try
                            // again at the next command
                            return;
                        }

                        super.handleInput(line, output, metadata);

                        for (String mimetype : output.keySet()) {
                            if (!mimetype.contains("html") && !mimetype.startsWith("image/")) {
                                continue;
                            }

                            services.browse(
                                URIUtil.assumeCorrect(metadata.get("url")),
                                metadata.containsKey("title") ? metadata.get("title") : metadata.get("url"),
                                metadata.containsKey("viewColumn") ? Integer.parseInt(metadata.get("viewColumn")) : 1
                            );
                        }
                }
            };

        repl.setMeasureCommandTime(false);

        return repl;
    }



    @SuppressWarnings("java:S899") // it's fine to ignore the result of createNewFile
    private static File getHistoryFile() throws IOException {
        File home = new File(System.getProperty("user.home"));
        File rascal = new File(home, ".rascal");

        if (!rascal.exists()) {
            rascal.mkdirs();
        }

        File historyFile = new File(rascal, ".repl-history-rascal-terminal");
        if (!historyFile.exists()) {
            historyFile.createNewFile();
        }

        return historyFile;
    }



    public static void main(String[] args) throws InterruptedException, IOException {
        int ideServicesPort = -1;
        int vfsPort = -1;
        String loadModule = null;
        boolean runModule = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--ideServicesPort":
                    ideServicesPort = Integer.parseInt(args[++i]);
                    break;
                case "--vfsPort":
                    vfsPort = Integer.parseInt(args[++i]);
                    break;
                case "--loadModule":
                    loadModule = args[++i];
                    break;
                case "--runModule":
                    runModule = true;
                    break;
            }
        }

        if (ideServicesPort == -1) {
            throw new IllegalArgumentException("missing --ideServicesPort commandline parameter");
        }

        if (vfsPort != -1) {
            VSCodeVFSClient.buildAndRegister(vfsPort);
        }

        try {
            LSPTerminalREPL terminal =
                new LSPTerminalREPL(TerminalFactory.get(), new TerminalIDEClient(ideServicesPort));
            if (loadModule != null) {
                terminal.queueCommand("import " + loadModule + ";");
                if (runModule) {
                    terminal.queueCommand("main()");
                }
            }
            terminal.run();
            System.exit(0); // kill the other threads
        }
        catch (IOException | URISyntaxException e) {
            e.printStackTrace();
            System.err.println("Rascal terminal terminated exceptionally; press any key to exit process.");
            System.in.read();
            System.exit(1);
        }
    }
}

