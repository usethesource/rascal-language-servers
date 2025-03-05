/*
 * Copyright (c) 2018-2025, NWO-I CWI and Swat.engineering
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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.OSUtils;
import org.rascalmpl.debug.IRascalMonitor;
import org.rascalmpl.ideservices.IDEServices;
import org.rascalmpl.interpreter.Evaluator;
import org.rascalmpl.interpreter.utils.RascalManifest;
import org.rascalmpl.library.Messages;
import org.rascalmpl.library.util.PathConfig;
import org.rascalmpl.library.util.PathConfig.RascalConfigMode;
import org.rascalmpl.parser.gtd.exception.ParseError;
import org.rascalmpl.repl.BaseREPL;
import org.rascalmpl.repl.StopREPLException;
import org.rascalmpl.repl.output.ICommandOutput;
import org.rascalmpl.repl.output.impl.AsciiStringOutputPrinter;
import org.rascalmpl.repl.rascal.RascalInterpreterREPL;
import org.rascalmpl.repl.rascal.RascalReplServices;
import org.rascalmpl.uri.ISourceLocationWatcher.ISourceLocationChanged;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.uri.classloaders.SourceLocationClassLoader;
import org.rascalmpl.uri.jar.JarURIResolver;
import org.rascalmpl.vscode.lsp.dap.DebugSocketServer;
import org.rascalmpl.vscode.lsp.uri.ProjectURIResolver;
import org.rascalmpl.vscode.lsp.uri.TargetURIResolver;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.impl.VSCodeVFSClient;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.io.StandardTextWriter;

/**
 * This class runs a Rascal terminal REPL that
 * connects to a running LSP server instance to
 * provide IDE feature to the user of a terminal instance.
 */
public class LSPTerminalREPL extends RascalInterpreterREPL {
    private final int ideServicePort;
    private final Set<String> dirtyModules = ConcurrentHashMap.newKeySet();
    private DebugSocketServer debugServer;

    private LSPTerminalREPL(int ideServicesPort) {
        this.ideServicePort = ideServicesPort;
    }

    @Override
    public Map<String, String> availableCommandLineOptions() {
        var result = super.availableCommandLineOptions();
        result.put("debugging", "enable debugging (true/false)");
        return result;
    }

    @Override
    protected IDEServices buildIDEService(PrintWriter err, IRascalMonitor monitor, Terminal term) {
        try {
            return new TerminalIDEClient(ideServicePort, err, monitor, term);
        } catch (IOException e) {
            throw new IllegalStateException("Could not build IDE service for REPL", e);
        }
    }

    @Override
    protected Evaluator buildEvaluator(Reader input, PrintWriter stdout, PrintWriter stderr, IDEServices services) {
        var evaluator = super.buildEvaluator(input, stdout, stderr, services);
        try {
            debugServer = new DebugSocketServer(evaluator, (TerminalIDEClient) services);

            // setup forwards to the VS Code URI resolvers
            URIResolverRegistry reg = URIResolverRegistry.getInstance();
            reg.registerLogical(new ProjectURIResolver(services::resolveProjectLocation));
            reg.registerLogical(new TargetURIResolver(services::resolveProjectLocation));

            // as VS Code starts us at the project root, we can use the current working directory to know which project we're at
            ISourceLocation projectDir = PathConfig.inferProjectRoot(URIUtil.createFileLocation(System.getProperty("user.dir")));

            // try to use the project:// scheme as often as possible, for a canonical experience between different people
            // running the same project in their IDE, or on the commandline
            if (URIResolverRegistry.getInstance().isDirectory(projectDir)) {
                var folderName = URIUtil.getLocationName(projectDir);
                var tentativeProjectLoc = URIUtil.correctLocation("project", folderName, "");

                // if the project loc is a true alias for the project directory, then we might as well use it
                if (services.resolveProjectLocation(tentativeProjectLoc).equals(projectDir)) {
                    projectDir = tentativeProjectLoc;
                }
            }

            // now let's calculate the path config
            PathConfig pcfg;
            if (projectDir != null) {
                pcfg = PathConfig.fromSourceProjectRascalManifest(projectDir, RascalConfigMode.INTERPRETER, true);
            }
            else {
                pcfg = new PathConfig();
                pcfg.addSourceLoc(URIUtil.rootLocation("std"));
            }

            // make sure to always add rascal-lsp (even if it wasn't in the pom.xml)
            // TODO: what if it was already in the pom.xml? PathConfig does a de-dup automatically.
            var lspJar = PathConfig.resolveProjectOnClasspath("rascal-lsp");
            // the interpreter must find the Rascal sources of util::LanguageServer etc.
            pcfg = pcfg.addSourceLoc(JarURIResolver.jarify(lspJar));
            // the interpreter must load the Java parts for calling util::IDEServices and registerLanguage
            pcfg = pcfg.addLibLoc(lspJar);

            stdout.println("Rascal " + RascalManifest.getRascalVersionNumber());
            stdout.println("Rascal-lsp " + getRascalLspVersion(lspJar));

            stdout.println("Rascal Search path: ");
            for (IValue srcPath : pcfg.getSrcs()) {
                // printing and configuring the evalutor at the same time
                ISourceLocation path = (ISourceLocation)srcPath;
                stdout.println("- " + path);
                evaluator.addRascalSearchPath(path);
                reg.watch(path, true, d -> sourceLocationChanged(path, d));
            }

            stdout.println("Rascal Class Loader path: ");
            for (IValue entry: pcfg.getLibsAndTarget()) {
                stdout.println("- " + entry);
            }
            ClassLoader cl = new SourceLocationClassLoader(pcfg.getLibsAndTarget(), ClassLoader.getSystemClassLoader());
            evaluator.addClassLoader(cl);

            if (!pcfg.getMessages().isEmpty()) {
                stdout.println("PathConfig messages:");
                Messages.write(pcfg.getMessages(), stdout);
                services.registerDiagnostics(pcfg.getMessages());
            }


        }
        catch (IOException | URISyntaxException e) {
            e.printStackTrace(stderr);
        }
        return evaluator;
    }

    private final Pattern debuggingCommandPattern = Pattern.compile("^\\s*:set\\s+debugging\\s+(true|false)");
    private @Nullable ICommandOutput handleDebuggerCommand(String command) {
        Matcher matcher = debuggingCommandPattern.matcher(command);
        if (!matcher.find()) {
            return null;
        }
        String message;
        if(matcher.group(1).equals("true")){
            if(!debugServer.isClientConnected()){
                ((TerminalIDEClient) services).startDebuggingSession(debugServer.getPort());
                message = "Debugging session started.";
            }
            else {
                message = "Debugging session was already running.";
            }
        }
        else {
            if(debugServer.isClientConnected()){
                debugServer.terminateDebugSession();
                message = "Debugging session stopped.";
            }
            else {
                message = "Debugging session was not running.";
            }
        }
        return () -> new AsciiStringOutputPrinter(message);
    }


    @Override
    public ICommandOutput handleInput(String command) throws InterruptedException, ParseError, StopREPLException {
        var result = handleDebuggerCommand(command);
        if (result != null) {
            return result;
        }
        Set<String> changes = new HashSet<>();
        changes.addAll(dirtyModules);
        dirtyModules.removeAll(changes);
        eval.reloadModules(eval.getMonitor(), changes, URIUtil.rootLocation("reloader"));
        return super.handleInput(command);
    }

    private void sourceLocationChanged(ISourceLocation srcPath, ISourceLocationChanged d) {
        if (URIUtil.isParentOf(srcPath, d.getLocation()) && d.getLocation().getPath().endsWith(".rsc")) {
            ISourceLocation relative = URIUtil.relativize(srcPath, d.getLocation());
            relative = URIUtil.removeExtension(relative);

            String modName = relative.getPath();
            if (modName.startsWith("/")) {
                modName = modName.substring(1);
            }
            modName = modName.replace("/", "::");
            modName = modName.replace("\\", "::");
            dirtyModules.add(modName);
        }
    }

    private static String getRascalLspVersion(ISourceLocation lspJar) {
        try {
            return new Manifest(URIResolverRegistry.getInstance()
                .getInputStream(URIUtil.getChildLocation(lspJar, "META-INF/MANIFEST.MF")))
                .getMainAttributes().getValue("Specification-Version");
        } catch (IOException e) {
            return "Unknown";
        }
    }



    @SuppressWarnings("java:S899") // it's fine to ignore the result of createNewFile
    private static Path getHistoryFile() throws IOException {
        var home = Paths.get(System.getProperty("user.home"));
        var rascal = home.resolve(".rascal");

        if (!Files.exists(rascal)) {
            Files.createDirectories(rascal);
        }

        return rascal.resolve(".repl-history-rascal-terminal-jline3");
    }



    public static void main(String[] args) throws InterruptedException, IOException {
        int ideServicesPort = -1;
        int vfsPort = -1;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--ideServicesPort")) {
                ideServicesPort = Integer.parseInt(args[++i]);
            }
            else if (args[i].equals("--vfsPort")) {
                vfsPort = Integer.parseInt(args[++i]);
            }
        }

        if (ideServicesPort == -1) {
            throw new IllegalArgumentException("missing --ideServicesPort commandline parameter");
        }

        if (vfsPort != -1) {
            VSCodeVFSClient.buildAndRegister(vfsPort);
        }

        var terminalBuilder = TerminalBuilder.builder()
            .dumb(true) // enable fallback
            .system(true);

        if (OSUtils.IS_WINDOWS) {
            terminalBuilder.encoding(StandardCharsets.UTF_8);
        }

        try {
            var repl = new BaseREPL(new RascalReplServices(new LSPTerminalREPL(ideServicesPort), getHistoryFile()), terminalBuilder.build());
            repl.run();
            System.exit(0); // kill the other threads
        }
        catch (IOException e) {
            e.printStackTrace();
            System.err.println("Rascal terminal terminated exceptionally; press any key to exit process.");
            System.in.read();
            System.exit(1);
        }
    }
}

