package org.rascalmpl.vscode.lsp.dap;

import io.usethesource.vallang.IValue;
import io.usethesource.vallang.type.Type;

import org.rascalmpl.debug.IRascalFrame;
import org.rascalmpl.interpreter.Evaluator;
import org.rascalmpl.interpreter.result.IRascalResult;

import java.util.*;

public class SuspendedStateManager {

    private Evaluator evaluator;
    private IRascalFrame[] currentStackFrames;
    private HashMap<Integer, ReferencedVariable> variables;
    private int referenceCounter;


    public SuspendedStateManager(Evaluator evaluator){
        this.evaluator = evaluator;
        this.variables = new HashMap<>();
    }

    public void suspended(){
        Stack<IRascalFrame> stack = evaluator.getCurrentStack();
        currentStackFrames = stack.toArray(IRascalFrame[]::new);
        referenceCounter = currentStackFrames.length+1;
        this.variables.clear();
    }

    public IRascalFrame[] getCurrentStackFrames(){
        return currentStackFrames;
    }

    public IRascalFrame getCurrentStackFrame(){
        return currentStackFrames[currentStackFrames.length - 1];
    }

    public List<ReferencedVariable> getVariablesByParentReferenceID(int referenceID){
        List<ReferencedVariable> variableList = new ArrayList<>();

        if(referenceID < 0) return variableList;

        // referenceID is a stack frame reference id
        if(referenceID-1 < currentStackFrames.length){
            IRascalFrame frame = currentStackFrames[referenceID-1];
            for (String varname : frame.getFrameVariables()) {
                IRascalResult result = frame.getFrameVariable(varname);
                ReferencedVariable refResult = new ReferencedVariable(result.getStaticType(), varname, result.getValue());
                if(refResult.hasSubFields()){
                    addNewReferencedVariable(refResult);
                }
                variableList.add(refResult);
            }
            return variableList;
        }

        if(!variables.containsKey(referenceID)) return variableList;

        // referenceID is a variable ID
        ReferencedVariable var = variables.get(referenceID);
        return var.getValue().accept(new RascalVariableVisitor(this, var.getType()));
    }

    public void addNewReferencedVariable(ReferencedVariable variable){
        variable.setReferenceID(referenceCounter);
        variables.put(referenceCounter, variable);
        referenceCounter++;
    }

}

class ReferencedVariable{
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
