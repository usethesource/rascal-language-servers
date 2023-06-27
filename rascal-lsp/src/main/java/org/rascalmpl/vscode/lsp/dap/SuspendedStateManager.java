package org.rascalmpl.vscode.lsp.dap;

import org.rascalmpl.debug.IRascalFrame;
import org.rascalmpl.interpreter.Evaluator;
import org.rascalmpl.interpreter.result.IRascalResult;

import java.util.*;

public class SuspendedStateManager {

    private Evaluator evaluator;
    private IRascalFrame[] currentStackFrames;
    private HashMap<Integer, ReferencedVariable> variables;
    private HashMap<Integer, IRascalFrame> scopes;
    private int referenceCounter;


    public SuspendedStateManager(Evaluator evaluator){
        this.evaluator = evaluator;
        this.variables = new HashMap<>();
        this.scopes = new HashMap<>();
    }

    public void suspended(){
        Stack<IRascalFrame> stack = evaluator.getCurrentStack();
        currentStackFrames = stack.toArray(IRascalFrame[]::new);
        referenceCounter = 0;//currentStackFrames.length+1;
        this.variables.clear();
        this.scopes.clear();
    }

    public IRascalFrame[] getCurrentStackFrames(){
        return currentStackFrames;
    }

    public IRascalFrame getCurrentStackFrame(){
        return currentStackFrames[currentStackFrames.length - 1];
    }

    public int addScope(IRascalFrame frame){
        scopes.put(++referenceCounter, frame);
        return referenceCounter;
    }

    public List<ReferencedVariable> getVariablesByParentReferenceID(int referenceID){
        List<ReferencedVariable> variableList = new ArrayList<>();

        if(referenceID < 0) return variableList;

        // referenceID is a stack frame reference id
        if(scopes.containsKey(referenceID)){
            IRascalFrame frame = scopes.get(referenceID);
            this.getReferencedVariableListFromFrame(frame, variableList);
            return variableList;
        }

        if(!variables.containsKey(referenceID)) return variableList;

        // referenceID is a variable ID
        ReferencedVariable var = variables.get(referenceID);
        return var.getValue().accept(new RascalVariableVisitor(this, var.getType()));
    }

    public void getReferencedVariableListFromFrame(IRascalFrame parentFrame, List<ReferencedVariable> variableList){
        for (String varname : parentFrame.getFrameVariables()) {
            IRascalResult result = parentFrame.getFrameVariable(varname);
            ReferencedVariable refResult = new ReferencedVariable(result.getStaticType(), varname, result.getValue());
            if(refResult.hasSubFields()){
                addNewReferencedVariable(refResult);
            }
            variableList.add(refResult);
        }
    }

    public void addNewReferencedVariable(ReferencedVariable variable){
        variable.setReferenceID(++referenceCounter);
        variables.put(referenceCounter, variable);
    }

}
