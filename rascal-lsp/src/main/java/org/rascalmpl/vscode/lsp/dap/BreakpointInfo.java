package org.rascalmpl.vscode.lsp.dap;

import org.eclipse.lsp4j.debug.Source;

public class BreakpointInfo {
    private final int id;
    private final Source source;

    public BreakpointInfo(int id, Source source) {
        this.id = id;
        this.source = source;
    }

    public int getId() {
        return id;
    }

    public Source getSource() {
        return source;
    }
}
