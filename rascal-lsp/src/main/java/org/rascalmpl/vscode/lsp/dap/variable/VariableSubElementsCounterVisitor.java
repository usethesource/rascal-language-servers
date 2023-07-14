package org.rascalmpl.vscode.lsp.dap.variable;

import io.usethesource.vallang.*;
import io.usethesource.vallang.visitors.IValueVisitor;

public class VariableSubElementsCounterVisitor implements IValueVisitor<VariableSubElementsCounter, RuntimeException> {

    @Override
    public VariableSubElementsCounter visitString(IString o) throws RuntimeException {
        return new VariableSubElementsCounter(0, 0);
    }

    @Override
    public VariableSubElementsCounter visitReal(IReal o) throws RuntimeException {
        return new VariableSubElementsCounter(0, 0);
    }

    @Override
    public VariableSubElementsCounter visitRational(IRational o) throws RuntimeException {
        return new VariableSubElementsCounter(0, 0);
    }

    @Override
    public VariableSubElementsCounter visitList(IList o) throws RuntimeException {
        return new VariableSubElementsCounter(0, o.length());
    }

    @Override
    public VariableSubElementsCounter visitSet(ISet o) throws RuntimeException {
        return new VariableSubElementsCounter(0, o.size());
    }

    @Override
    public VariableSubElementsCounter visitSourceLocation(ISourceLocation o) throws RuntimeException {
        return new VariableSubElementsCounter(0, 0);
    }

    @Override
    public VariableSubElementsCounter visitTuple(ITuple o) throws RuntimeException {
        return new VariableSubElementsCounter(0, o.arity());
    }

    @Override
    public VariableSubElementsCounter visitNode(INode o) throws RuntimeException {
        return new VariableSubElementsCounter(0, o.arity());
    }

    @Override
    public VariableSubElementsCounter visitConstructor(IConstructor o) throws RuntimeException {
        return new VariableSubElementsCounter((o.mayHaveKeywordParameters() ? o.asWithKeywordParameters().getParameters().size() : 0)+o.arity(), 0);
    }

    @Override
    public VariableSubElementsCounter visitInteger(IInteger o) throws RuntimeException {
        return new VariableSubElementsCounter(0, 0);
    }

    @Override
    public VariableSubElementsCounter visitMap(IMap o) throws RuntimeException {
        return new VariableSubElementsCounter(0, o.size());
    }

    @Override
    public VariableSubElementsCounter visitBoolean(IBool boolValue) throws RuntimeException {
        return new VariableSubElementsCounter(0, 0);
    }

    @Override
    public VariableSubElementsCounter visitExternal(IExternalValue externalValue) throws RuntimeException {
        return new VariableSubElementsCounter(0, 0);
    }

    @Override
    public VariableSubElementsCounter visitDateTime(IDateTime o) throws RuntimeException {
        if(o.isDateTime()){
            return new VariableSubElementsCounter(9, 0);
        }
        else if(o.isDate()){
            return new VariableSubElementsCounter(3, 0);
        }
        else{
            return new VariableSubElementsCounter(6, 0);
        }
    }
}
