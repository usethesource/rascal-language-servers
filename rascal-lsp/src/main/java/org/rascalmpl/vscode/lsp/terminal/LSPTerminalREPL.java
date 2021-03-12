package org.rascalmpl.vscode.lsp.terminal;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.util.Map;

import org.rascalmpl.ideservices.IDEServices;
import org.rascalmpl.interpreter.Evaluator;
import org.rascalmpl.repl.BaseREPL;
import org.rascalmpl.repl.ILanguageProtocol;
import org.rascalmpl.repl.RascalInterpreterREPL;
import org.rascalmpl.shell.ShellEvaluatorFactory;
import org.rascalmpl.uri.URIUtil;

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
                    return ShellEvaluatorFactory.getDefaultEvaluator(input, stdout, stderr);
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

    public static void main(String[] args) throws InterruptedException {
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
            System.exit(1);
        }
    }
}

