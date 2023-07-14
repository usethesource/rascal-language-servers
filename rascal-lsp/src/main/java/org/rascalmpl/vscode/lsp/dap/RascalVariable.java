package org.rascalmpl.vscode.lsp.dap;

import io.usethesource.vallang.IValue;
import io.usethesource.vallang.type.Type;

public class RascalVariable {
    private int referenceID;
    private final Type type;
    private final String name;
    private final IValue value;
    private final String displayValue;
    private int namedVariables = 0;
    private int indexedVariables = 0;

    public RascalVariable(Type type, String name, IValue value){
        this.referenceID = -1;
        this.type = type;
        this.name = name;
        this.value = value;
        this.displayValue = RascalVariableUtils.getDisplayString(value);
    }

    public void setNamedVariables(int namedVariables) {
        this.namedVariables = namedVariables;
    }

    public void setIndexedVariables(int indexedVariables) {
        this.indexedVariables = indexedVariables;
    }

    public int getReferenceID(){
        return referenceID;
    }

    public Type getType(){
        return type;
    }

    public void setReferenceID(int referenceID) {
        this.referenceID = referenceID;
    }

    public String getName() {
        return name;
    }

    public IValue getValue() {
        return value;
    }

    public String getDisplayValue(){
        return displayValue;
    }

    public boolean hasSubFields(){
        if(type == null) return false;

        return type.isList() || type.isMap() || type.isSet() || type.isAliased() || type.isNode() || type.isConstructor() || type.isRelation() || type.isTuple() || type.isDateTime();
    }

    public int getNamedVariables() {
        return namedVariables;
    }

    public int getIndexedVariables() {
        return indexedVariables;
    }
}
