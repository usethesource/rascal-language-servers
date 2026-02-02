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
import java.util.ArrayList;

import org.rascalmpl.repl.rascal.RascalInterpreterREPL;
import org.rascalmpl.shell.RascalShell;
import org.rascalmpl.vscode.lsp.uri.jsonrpc.impl.VSCodeVFSClient;

/**
 * This class runs a Rascal terminal REPL that
 * connects to a running LSP server instance to
 * provide IDE feature to the user of a terminal instance.
 */
public class LSPTerminalREPL extends RascalInterpreterREPL {
    public static void main(String[] args) throws IOException {
        int vfsPort = -1;

        var rascalShellArgs = new ArrayList<String>();

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--vfsPort")) {
                vfsPort = Integer.parseInt(args[++i]);
            } else {
                rascalShellArgs.add(args[i]);
            }
        }

        if (vfsPort != -1) {
            VSCodeVFSClient.buildAndRegister(vfsPort);
        }

        RascalShell.main(rascalShellArgs.toArray(new String[rascalShellArgs.size()]));
    }
}

