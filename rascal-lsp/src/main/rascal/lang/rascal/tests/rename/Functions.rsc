module lang::rascal::tests::rename::Functions

import lang::rascal::tests::rename::TestUtils;
// import lang::rascal::lsp::refactor::Exception;

test bool nestedFunctionParameter() = {0, 1} == testRenameOccurrences("
    'int f(int foo, int baz) {
    '   return foo;
    '}
");

test bool nestedRecursiveFunctionName() = {0, 1, 2, 3} == testRenameOccurrences("
    'int fib(int n) {
    '   switch (n) {
    '       case 0: {
    '           return 1;
    '       }
    '       case 1: {
    '           return 1;
    '       }
    '       default: {
    '           return fib(n - 1) + fib(n - 2);
    '       }
    '   }
    '}
    '
    'fib(7);
", oldName = "fib", newName = "fibonacci", cursorAtOldNameOccurrence = -1);

test bool recursiveFunctionName() = {0, 1, 2, 3} == testRenameOccurrences("fib(7);", decls = "
    'int fib(int n) {
    '   switch (n) {
    '       case 0: {
    '           return 1;
    '       }
    '       case 1: {
    '           return 1;
    '       }
    '       default: {
    '           return fib(n - 1) + fib(n - 2);
    '       }
    '   }
    '}
", oldName = "fib", newName = "fibonacci", cursorAtOldNameOccurrence = -1);

test bool nestedPublicFunction() = {0, 1} == testRenameOccurrences("
    'public int foo(int f) {
    '   return f;
    '}
    'foo(1);
");

test bool nestedDefaultFunction() = {0, 1} == testRenameOccurrences("
    'int foo(int f) {
    '   return f;
    '}
    'foo(1);
");

test bool nestedPrivateFunction() = {0, 1} == testRenameOccurrences("
    'private int foo(int f) {
    '   return f;
    '}
    'foo(1);
");

test bool publicFunction() = {0, 1} == testRenameOccurrences("foo(1);", decls = "
    'public int foo(int f) {
    '   return f;
    '}
");

test bool defaultFunction() = {0, 1} == testRenameOccurrences("foo(1);", decls = "
    'int foo(int f) {
    '   return f;
    '}
");

test bool privateFunction() = {0, 1} == testRenameOccurrences("foo(1);", decls = "
    'private int foo(int f) {
    '   return f;
    '}
");
