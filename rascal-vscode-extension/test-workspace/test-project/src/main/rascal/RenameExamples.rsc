module RenameExamples

void examples() { // renaming foo -> bar in all examples

    // queries voor illegal renaming
    // 1. Is <new-name> already resolvable from a scope where <current-name> is currently used?
    // 2. Is <new-name> implicitly declared in a scope from where <current-name> can be resolved?

    bool isLegalRename(TModel tm, set[loc] usesOfOldName, str newName) {
        for (loc use <- currentUses) {
            for (Scope scope <- tm.scopes, isContainedInScope(scope, use)) {
                // 1. Is <newName> already reachable in *any* scope where <oldName> is used
                // In this case, renaming will lead to changes in name capturing
                if (isDeclaredInThisOrParentScope(tm, scope, newName)) {
                    return false;
                }
                if (isDeclaredInThisScope(tm, scope, newName), isDeclaredInThisOrParentScope(tm, scope, use)) {
                    return false;
                }
            }
        }
        return true;
    }

    {   // shadowing in inner scope
        // legal: type-correct and semantics-preserving
        int foo = 8;
        {
            int bar = 9;
        }
    }

    {   // shadowing in inner scope
        // legal: type-correct and semantics-preserving
        int foo = 8;
        int f(int bar) {
            return bar;
        }
    }

    {   // declaration in same scope becomes use
        // illegal: type-correct but not semantics-preserving
        int foo = 8;
        bar = 9;
    }

    {   // declaration in inner scope becomes use
        // illegal: type-correct but not semantics-preserving
        int foo = 8;
        {
            bar = 9;
        }
    }

    {   // double declaration
        // illegal: not type-correct
        int foo = 8;
        int bar = 9;
    }

    {   // double declaration
        // illegal: not type-correct
        int f(int foo) {
            int bar = 9;
            return foo + bar;
        }
    }

    {   // independent names
        // legal: type-correct and semantics-preserving
        {
            int foo = 8;
        }
        {
            int bar = 9;
        }
    }

    {   // declaration in inner scope becomes use
        // illegal: type-correct but not semantics-preserving
        int foo = 8;
        if (bar := 9) {
            temp = 2 * bar;
        }
    }

    {   // declaration in outer scope becomes shadowed
        // illegal: type-correct but not semantics-preserving
        int foo = 8;
        if (int bar := 9) {
            foo = 2 * bar;
        }
    }
}
