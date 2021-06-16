/*
 * Copyright (c) 2018-2021, NWO-I CWI and Swat.engineering
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.rascalmpl.vscode.lsp.terminal;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.util.Map;

import org.rascalmpl.ideservices.IDEServices;
import org.rascalmpl.interpreter.Evaluator;
import org.rascalmpl.interpreter.env.GlobalEnvironment;
import org.rascalmpl.interpreter.env.ModuleEnvironment;
import org.rascalmpl.interpreter.load.StandardLibraryContributor;
import org.rascalmpl.interpreter.utils.RascalManifest;
import org.rascalmpl.library.util.PathConfig;
import org.rascalmpl.library.util.PathConfig.RascalConfigMode;
import org.rascalmpl.repl.BaseREPL;
import org.rascalmpl.repl.ILanguageProtocol;
import org.rascalmpl.repl.RascalInterpreterREPL;
import org.rascalmpl.shell.ShellEvaluatorFactory;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.uri.classloaders.SourceLocationClassLoader;
import org.rascalmpl.values.ValueFactoryFactory;
import org.rascalmpl.vscode.lsp.uri.ProjectURIResolver;
import org.rascalmpl.vscode.lsp.uri.TargetURIResolver;

import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IValueFactory;
import jline.Terminal;
import jline.TerminalFactory;

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
        super(makeInterpreter(terminal, services), null, stdin, stderr, stdout, true, terminal.isAnsiSupported(), getHistoryFile(), terminal, null);
    }

    private static ILanguageProtocol makeInterpreter(Terminal terminal, final IDEServices services) throws IOException, URISyntaxException {
        RascalInterpreterREPL repl =
            new RascalInterpreterREPL(prettyPrompt, allowColors, getHistoryFile()) {
                @Override
                protected Evaluator constructEvaluator(InputStream input, OutputStream stdout, OutputStream stderr) {
                    GlobalEnvironment heap = new GlobalEnvironment();
                    ModuleEnvironment root = heap.addModule(new ModuleEnvironment(ModuleEnvironment.SHELL_MODULE, heap));
                    IValueFactory vf = ValueFactoryFactory.getValueFactory();
                    Evaluator evaluator = new Evaluator(vf, input, stderr, stdout, root, heap);
                    evaluator.addRascalSearchPathContributor(StandardLibraryContributor.getInstance());
                    evaluator.addRascalSearchPath(URIUtil.correctLocation("lib", "rascal-lsp", ""));
                    URIResolverRegistry reg = URIResolverRegistry.getInstance();

                    ISourceLocation projectDir = ShellEvaluatorFactory.inferProjectRoot(new File(System.getProperty("user.dir")));
                    String projectName = new RascalManifest().getProjectName(projectDir);

                    reg.registerLogical(new ProjectURIResolver(services::resolveProjectLocation));
                    reg.registerLogical(new TargetURIResolver(services::resolveProjectLocation));

                    try {
                        PathConfig pcfg = PathConfig.fromSourceProjectRascalManifest(projectDir, RascalConfigMode.INTERPETER);

                        for (IValue path : pcfg.getSrcs()) {
                            evaluator.addRascalSearchPath((ISourceLocation) path);
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

                    evaluator.setMonitor(services);

                    return evaluator;
                }

                @Override
                public void handleInput(String line, Map<String, InputStream> output, Map<String, String> metadata)
                    throws InterruptedException {
                    super.handleInput(line, output, metadata);

                    for (String mimetype : output.keySet()) {
                        if (!mimetype.contains("html") && !mimetype.startsWith("image/")) {
                            continue;
                        }

                        services.browse(URIUtil.assumeCorrect(metadata.get("url")));
                    }
                }
            };

        repl.setMeasureCommandTime(false);

        return repl;
    }

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

        for (int i = 0; i < args.length; i++) {
            if ("--ideServicesPort".equals(args[i])) {
                ideServicesPort = Integer.parseInt(args[++i]);
            }
        }

        if (ideServicesPort == -1) {
            throw new IllegalArgumentException("missing --ideServicesPort commandline parameter");
        }

        try {
            new LSPTerminalREPL(TerminalFactory.get(), new TerminalIDEClient(ideServicesPort)).run();
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

