package org.rascalmpl.vscode.lsp.dap;

import io.usethesource.vallang.IValue;
import io.usethesource.vallang.type.Type;

import java.io.IOException;
import java.io.Writer;

public class ReferencedVariable{
    private int referenceID;
    private Type type;
    private String name;
    private IValue value;

    public ReferencedVariable(Type type, String name, IValue value){
        this.referenceID = -1;
        this.type = type;
        this.name = name;
        this.value = value;
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
        //TODO: add max size
        return value.toString();
    }

    public boolean hasSubFields(){
        if(type == null) return false;

        return type.isList() || type.isMap() || type.isSet() || type.isAliased() || type.isNode() || type.isConstructor() || type.isRelation() || type.isTuple();
    }
}
