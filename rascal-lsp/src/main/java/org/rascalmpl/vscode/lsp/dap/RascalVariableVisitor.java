package org.rascalmpl.vscode.lsp.dap;

import io.usethesource.vallang.*;
import io.usethesource.vallang.impl.reference.ValueFactory;
import io.usethesource.vallang.type.Type;
import io.usethesource.vallang.type.TypeFactory;
import io.usethesource.vallang.visitors.IValueVisitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RascalVariableVisitor implements IValueVisitor<List<RascalVariable>, RuntimeException> {

    private final SuspendedState stateManager;
    private final Type visitedType;

    public RascalVariableVisitor(SuspendedState stateManager, Type visitedType){
        this.stateManager = stateManager;
        this.visitedType = visitedType;
    }

    @Override
    public List<RascalVariable> visitString(IString o) throws RuntimeException {
        return new ArrayList<>();
    }

    @Override
    public List<RascalVariable> visitReal(IReal o) throws RuntimeException {
        return new ArrayList<>();
    }

    @Override
    public List<RascalVariable> visitRational(IRational o) throws RuntimeException {
        return new ArrayList<>();
    }

    @Override
    public List<RascalVariable> visitList(IList o) throws RuntimeException {
        List<RascalVariable> result = new ArrayList<>();
        for (int i = 0; i < o.length(); i++) {
            RascalVariable newVar = new RascalVariable(visitedType.isList() ? visitedType.getElementType() : o.getElementType(),Integer.toString(i), o.get(i));
            addVariableToResult(newVar, result);
        }
        return result;
    }

    @Override
    public List<RascalVariable> visitSet(ISet o) throws RuntimeException {
        List<RascalVariable> result = new ArrayList<>();
        int i = 0;
        for(IValue value: o){
            RascalVariable newVar = new RascalVariable(visitedType.isSet() ? visitedType.getElementType() : o.getElementType(),Integer.toString(i), value);
            addVariableToResult(newVar, result);
            i++;
        }
        return result;
    }

    @Override
    public List<RascalVariable> visitSourceLocation(ISourceLocation o) throws RuntimeException {
        return new ArrayList<>();
    }

    @Override
    public List<RascalVariable> visitTuple(ITuple o) throws RuntimeException {
        List<RascalVariable> result = new ArrayList<>();
        Type toUse = visitedType.isTuple() ? visitedType : o.getType();
        for (int i = 0; i < o.arity(); i++) {
            RascalVariable newVar = new RascalVariable(toUse.getFieldType(i), toUse.hasFieldNames() ? toUse.getFieldName(i) : Integer.toString(i), o.get(i));
            addVariableToResult(newVar, result);
        }
        return result;
    }

    @Override
    public List<RascalVariable> visitNode(INode o) throws RuntimeException {
        List<RascalVariable> result = new ArrayList<>();

        for (int i = 0; i < o.arity(); i++) {
            RascalVariable newVar = new RascalVariable(o.get(i).getType(), Integer.toString(i), o.get(i));
            addVariableToResult(newVar, result);
        }
        return result;
    }

    @Override
    public List<RascalVariable> visitConstructor(IConstructor o) throws RuntimeException {
        List<RascalVariable> result = new ArrayList<>();

        for (int i = 0; i < o.arity(); i++) {
            RascalVariable newVar = new RascalVariable(o.getConstructorType().getFieldType(i), o.getConstructorType().hasFieldNames() ? o.getConstructorType().getFieldName(i) : Integer.toString(i), o.get(i));
            addVariableToResult(newVar, result);
        }
        if (o.mayHaveKeywordParameters()) {
            Map<String, IValue> parameters = o.asWithKeywordParameters().getParameters();
            parameters.forEach((name, value) -> {
                RascalVariable newVar = new RascalVariable(value.getType(), '['+name+']', value);
                addVariableToResult(newVar, result);
            });
        }

        return result;
    }

    @Override
    public List<RascalVariable> visitInteger(IInteger o) throws RuntimeException {
        return new ArrayList<>();
    }

    @Override
    public List<RascalVariable> visitMap(IMap o) throws RuntimeException {
        List<RascalVariable> result = new ArrayList<>();

        for (io.usethesource.vallang.IValue key : o) {
            RascalVariable newVar = new RascalVariable(visitedType.isMap() ? visitedType.getValueType() : o.getValueType(), RascalVariableUtils.getDisplayString(key), o.get(key));
            addVariableToResult(newVar, result);
        }
        return result;
    }

    @Override
    public List<RascalVariable> visitBoolean(IBool boolValue) throws RuntimeException {
        return new ArrayList<>();
    }

    @Override
    public List<RascalVariable> visitExternal(IExternalValue externalValue) throws RuntimeException {
        return new ArrayList<>();
    }

    @Override
    public List<RascalVariable> visitDateTime(IDateTime o) throws RuntimeException {
        List<RascalVariable> result = new ArrayList<>();

        if(o.isDate() || o.isDateTime()){
            result.add(new RascalVariable(TypeFactory.getInstance().integerType(), "year", ValueFactory.getInstance().integer(o.getYear())));
            result.add(new RascalVariable(TypeFactory.getInstance().integerType(), "month", ValueFactory.getInstance().integer(o.getMonthOfYear())));
            result.add(new RascalVariable(TypeFactory.getInstance().integerType(), "day", ValueFactory.getInstance().integer(o.getDayOfMonth())));
        }

        if(o.isTime() || o.isDateTime()){
            result.add(new RascalVariable(TypeFactory.getInstance().integerType(), "hour", ValueFactory.getInstance().integer(o.getHourOfDay())));
            result.add(new RascalVariable(TypeFactory.getInstance().integerType(), "minute", ValueFactory.getInstance().integer(o.getMinuteOfHour())));
            result.add(new RascalVariable(TypeFactory.getInstance().integerType(), "second", ValueFactory.getInstance().integer(o.getSecondOfMinute())));
            result.add(new RascalVariable(TypeFactory.getInstance().integerType(), "millisecond", ValueFactory.getInstance().integer(o.getMillisecondsOfSecond())));
            result.add(new RascalVariable(TypeFactory.getInstance().integerType(), "timezoneOffsetHours", ValueFactory.getInstance().integer(o.getTimezoneOffsetHours())));
            result.add(new RascalVariable(TypeFactory.getInstance().integerType(), "timezoneOffsetMinutes", ValueFactory.getInstance().integer(o.getTimezoneOffsetMinutes())));
        }

        return result;
    }

    private void addVariableToResult(RascalVariable newVar, List<RascalVariable> resultList){
        if(newVar.hasSubFields()){
            stateManager.addVariable(newVar);
        }
        resultList.add(newVar);
    }
}
