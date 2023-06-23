package org.rascalmpl.vscode.lsp.dap;

import io.usethesource.vallang.*;
import io.usethesource.vallang.type.Type;
import io.usethesource.vallang.visitors.IValueVisitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RascalVariableVisitor implements IValueVisitor<List<ReferencedVariable>, RuntimeException> {

    private final SuspendedStateManager stateManager;
    private final Type visitedType;

    public RascalVariableVisitor(SuspendedStateManager stateManager, Type visitedType){
        this.stateManager = stateManager;
        this.visitedType = visitedType;
    }

    @Override
    public List<ReferencedVariable> visitString(IString o) throws RuntimeException {
        return new ArrayList<>();
    }

    @Override
    public List<ReferencedVariable> visitReal(IReal o) throws RuntimeException {
        return new ArrayList<>();
    }

    @Override
    public List<ReferencedVariable> visitRational(IRational o) throws RuntimeException {
        return new ArrayList<>();
    }

    @Override
    public List<ReferencedVariable> visitList(IList o) throws RuntimeException {
        List<ReferencedVariable> result = new ArrayList<>();
        for (int i = 0; i < o.length(); i++) {
            ReferencedVariable newVar = new ReferencedVariable(visitedType.isList() ? visitedType.getElementType() : o.getElementType(),Integer.toString(i), o.get(i));
            if(newVar.hasSubFields()){
                stateManager.addNewReferencedVariable(newVar);
            }
            result.add(newVar);
        }
        return result;
    }

    @Override
    public List<ReferencedVariable> visitSet(ISet o) throws RuntimeException {
        List<ReferencedVariable> result = new ArrayList<>();
        int i = 0;
        for(IValue value: o){
            ReferencedVariable newVar = new ReferencedVariable(visitedType.isSet() ? visitedType.getElementType() : o.getElementType(),Integer.toString(i), value);
            if(newVar.hasSubFields()){
                stateManager.addNewReferencedVariable(newVar);
            }
            result.add(newVar);
            i++;
        }
        return result;
    }

    @Override
    public List<ReferencedVariable> visitSourceLocation(ISourceLocation o) throws RuntimeException {
        return new ArrayList<>();
    }

    @Override
    public List<ReferencedVariable> visitTuple(ITuple o) throws RuntimeException {
        List<ReferencedVariable> result = new ArrayList<>();
        Type toUse = visitedType.isTuple() ? visitedType : o.getType();
        for (int i = 0; i < o.arity(); i++) {
            ReferencedVariable newVar = new ReferencedVariable(toUse.getFieldType(i), toUse.hasFieldNames() ? toUse.getFieldName(i) : Integer.toString(i), o.get(i));
            if(newVar.hasSubFields()){
                stateManager.addNewReferencedVariable(newVar);
            }
            result.add(newVar);
        }
        return result;
    }

    @Override
    public List<ReferencedVariable> visitNode(INode o) throws RuntimeException {
        List<ReferencedVariable> result = new ArrayList<>();

        for (int i = 0; i < o.arity(); i++) {
            ReferencedVariable newVar = new ReferencedVariable(o.get(i).getType(), Integer.toString(i), o.get(i));
            if(newVar.hasSubFields()){
                stateManager.addNewReferencedVariable(newVar);
            }
            result.add(newVar);
        }
        return result;
    }

    @Override
    public List<ReferencedVariable> visitConstructor(IConstructor o) throws RuntimeException {
        List<ReferencedVariable> result = new ArrayList<>();

        for (int i = 0; i < o.arity(); i++) {
            ReferencedVariable newVar = new ReferencedVariable(o.getConstructorType().getFieldType(i), o.getConstructorType().hasFieldNames() ? o.getConstructorType().getFieldName(i) : Integer.toString(i), o.get(i));
            if(newVar.hasSubFields()){
                stateManager.addNewReferencedVariable(newVar);
            }
            result.add(newVar);
        }
        if (o.mayHaveKeywordParameters()) {
            Map<String, IValue> parameters = o.asWithKeywordParameters().getParameters();
            parameters.forEach((name, value) -> {
                ReferencedVariable newVar = new ReferencedVariable(value.getType(), '['+name+']', value);
                if(newVar.hasSubFields()){
                    stateManager.addNewReferencedVariable(newVar);
                }
                result.add(newVar);
            });
        }

        return result;
    }

    @Override
    public List<ReferencedVariable> visitInteger(IInteger o) throws RuntimeException {
        return new ArrayList<>();
    }

    @Override
    public List<ReferencedVariable> visitMap(IMap o) throws RuntimeException {
        List<ReferencedVariable> result = new ArrayList<>();

        for (io.usethesource.vallang.IValue key : o) {
            //TODO: key.toString() should have a limit in length
            ReferencedVariable newVar = new ReferencedVariable(visitedType.isMap() ? visitedType.getValueType() : o.getValueType(), key.toString(), o.get(key));
            if(newVar.hasSubFields()){
                stateManager.addNewReferencedVariable(newVar);
            }
            result.add(newVar);
        }
        return result;
    }

    @Override
    public List<ReferencedVariable> visitBoolean(IBool boolValue) throws RuntimeException {
        return new ArrayList<>();
    }

    @Override
    public List<ReferencedVariable> visitExternal(IExternalValue externalValue) throws RuntimeException {
        return new ArrayList<>();
    }

    @Override
    public List<ReferencedVariable> visitDateTime(IDateTime o) throws RuntimeException {
        return new ArrayList<>();
    }
}
