package org.rascalmpl.vscode.lsp.dap;

import io.usethesource.vallang.ISourceLocation;
import org.rascalmpl.debug.IRascalFrame;
import org.rascalmpl.interpreter.Evaluator;
import org.rascalmpl.interpreter.result.IRascalResult;
import org.rascalmpl.uri.URIUtil;

import java.util.*;

public class SuspendedState {

    private final Evaluator evaluator;
    private IRascalFrame[] currentStackFrames;
    private final HashMap<Integer, RascalVariable> variables;
    private final HashMap<Integer, IRascalFrame> scopes;
    private int referenceIDCounter;

    private boolean isSuspended;


    public SuspendedState(Evaluator evaluator){
        this.evaluator = evaluator;
        this.variables = new HashMap<>();
        this.scopes = new HashMap<>();
    }

    public void suspended(){
        Stack<IRascalFrame> stack = evaluator.getCurrentStack();
        currentStackFrames = stack.toArray(IRascalFrame[]::new);
        referenceIDCounter = 0;
        this.variables.clear();
        this.scopes.clear();
        this.isSuspended = true;
    }

    public ISourceLocation getCurrentLocation(){
        return evaluator.getCurrentPointOfExecution() != null ?
            evaluator.getCurrentPointOfExecution()
            : URIUtil.rootLocation("stdin");
    }

    public void resumed(){
        this.isSuspended = false;
    }

    public boolean isSuspended() {
        return isSuspended;
    }

    public IRascalFrame[] getCurrentStackFrames(){
        return currentStackFrames;
    }

    public IRascalFrame getCurrentStackFrame(){
        return currentStackFrames[currentStackFrames.length - 1];
    }

    public int addScope(IRascalFrame frame){
        scopes.put(++referenceIDCounter, frame);
        return referenceIDCounter;
    }

    public List<RascalVariable> getVariables(int referenceID, int startIndex, int maxCount){
        List<RascalVariable> variableList = new ArrayList<>();

        if(referenceID < 0) return variableList;

        // referenceID is a stack frame reference id
        if(scopes.containsKey(referenceID)){
            IRascalFrame frame = scopes.get(referenceID);
            List<String> frameVariables = new ArrayList<>(frame.getFrameVariables());
            frameVariables.sort(String::compareTo);
            int endIndex = maxCount == -1 ? frameVariables.size() : Math.min(frameVariables.size(), startIndex + maxCount);
            for (String varname : frameVariables.subList(startIndex, endIndex)) {
                IRascalResult result = frame.getFrameVariable(varname);
                RascalVariable refResult = new RascalVariable(result.getStaticType(), varname, result.getValue());
                if(refResult.hasSubFields()){
                    addVariable(refResult);
                }
                variableList.add(refResult);
            }
            return variableList;
        }

        if(!variables.containsKey(referenceID)) return variableList;

        // referenceID is a variable ID
        RascalVariable var = variables.get(referenceID);
        return var.getValue().accept(new RascalVariableVisitor(this, var.getType()));
    }

    public void addVariable(RascalVariable variable){
        variable.setReferenceID(++referenceIDCounter);
        variables.put(referenceIDCounter, variable);
    }

}
