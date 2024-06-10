module RenameExamples

void examples() { // renaming foo -> bar in all examples

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
