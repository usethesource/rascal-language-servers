package org.rascalmpl.vscode.lsp.dap;

import io.usethesource.vallang.IValue;
import io.usethesource.vallang.type.Type;

public class ReferencedVariable{
    private int referenceID;
    private final Type type;
    private final String name;
    private final IValue value;
    private final String displayValue;

    public ReferencedVariable(Type type, String name, IValue value){
        this.referenceID = -1;
        this.type = type;
        this.name = name;
        this.value = value;
        this.displayValue = RascalVariableUtils.getDisplayString(value);
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
}
