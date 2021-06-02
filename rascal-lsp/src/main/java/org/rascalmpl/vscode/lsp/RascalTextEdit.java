package org.rascalmpl.vscode.lsp;

import org.eclipse.lsp4j.TextEdit;
import org.rascalmpl.vscode.lsp.util.locations.ColumnMaps;
import org.rascalmpl.vscode.lsp.util.locations.Locations;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;

public class RascalTextEdit {
    private final ISourceLocation range;
    private final IString change;

    public RascalTextEdit(ISourceLocation range, IString change) {
        this.range = range;
        this.change = change;
    }

    public IString getChange() {
        return change;
    }

    public ISourceLocation getRange() {
        return range;
    }

    public TextEdit convert(ColumnMaps columns) {
        return new TextEdit(Locations.toRange(range, columns), change.getValue());
    }
}
