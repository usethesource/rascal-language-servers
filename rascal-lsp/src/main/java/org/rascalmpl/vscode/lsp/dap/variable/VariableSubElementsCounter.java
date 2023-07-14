package org.rascalmpl.vscode.lsp.dap.variable;

/**
    Get by visiting Rascal Values, used to indicate the number of sub elements of a variable to the IDE
 **/
public class VariableSubElementsCounter {
    private int namedVariables;
    private int indexedVariables;

    public VariableSubElementsCounter(int namedVariables, int indexedVariables){
        this.namedVariables = namedVariables;
        this.indexedVariables = indexedVariables;
    }

    public int getNamedVariables() {
        return namedVariables;
    }

    public int getIndexedVariables() {
        return indexedVariables;
    }

    public void setNamedVariables(int namedVariables) {
        this.namedVariables = namedVariables;
    }

    public void setIndexedVariables(int indexedVariables) {
        this.indexedVariables = indexedVariables;
    }

}
