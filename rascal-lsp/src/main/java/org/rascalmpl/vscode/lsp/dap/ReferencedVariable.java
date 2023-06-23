package org.rascalmpl.vscode.lsp.dap;

import io.usethesource.vallang.IValue;
import io.usethesource.vallang.io.StandardTextWriter;
import io.usethesource.vallang.type.Type;
import org.rascalmpl.interpreter.utils.LimitedResultWriter;

import java.io.IOException;
import java.io.Writer;

public class ReferencedVariable{
    private static final int MAX_SIZE_STRING_NAME = 128;
    private int referenceID;
    private final Type type;
    private final String name;
    private final IValue value;

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
        // took from Rascal Eclipse debug.core.model.RascalValue
        Writer w = new LimitedResultWriter(MAX_SIZE_STRING_NAME);
        try {
            new StandardTextWriter(true, 2).write(value, w);
            return w.toString();
        } catch (LimitedResultWriter.IOLimitReachedException e) {
            return w.toString();
        }
        catch (IOException e) {
            return "error during serialization...";
        }
    }

    public boolean hasSubFields(){
        if(type == null) return false;

        return type.isList() || type.isMap() || type.isSet() || type.isAliased() || type.isNode() || type.isConstructor() || type.isRelation() || type.isTuple() || type.isDateTime();
    }
}
