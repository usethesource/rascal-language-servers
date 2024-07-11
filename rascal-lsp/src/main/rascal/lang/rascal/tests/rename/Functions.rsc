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

test bool backtrackOverloadFromUse() = {0, 1, 2} == testRenameOccurrences("x = foo(3);", decls = "
    'int foo(int x) = x when x \< 2;
    'default int foo(int x) = x;
", cursorAtOldNameOccurrence = -1);

test bool backtrackOverloadFromDef() = {0, 1, 2} == testRenameOccurrences("x = foo(3);", decls = "
    'int foo(int x) = x when x \< 2;
    'default int foo(int x) = x;
");

test bool patternOverloadFromUse() = {0, 1, 2, 3} == testRenameOccurrences("x = size([1, 2]);", decls = "
    'int size(list[&T] _: []) = 0;
    'int size(list[&T] _: [_, *l]) = 1 + size(l);
", cursorAtOldNameOccurrence = -1, oldName = "size", newName = "sizeof");

test bool patternOverloadFromDef() = {0, 1, 2, 3} == testRenameOccurrences("x = size([1, 2]);", decls = "
    'int size(list[&T] _: []) = 0;
    'int size(list[&T] _: [_, *l]) = 1 + size(l);
", oldName = "size", newName = "sizeof");

test bool typeOverloadFromUse() = {0, 1, 2, 3, 4, 5, 6} == testRenameOccurrences("x = size([1, 2]);", decls = "
    'int size(list[&T] _: []) = 0;
    'int size(list[&T] _: [_, *l]) = 1 + size(l);
    '
    'int size(set[&T] _: {}) = 0;
    'int size(set[&T] _: {_, *s}) = 1 + size(s);
", cursorAtOldNameOccurrence = -1, oldName = "size", newName = "sizeof");

test bool typeOverloadFromDef() = {0, 1, 2, 3, 4, 5, 6} == testRenameOccurrences("x = size([1, 2]);", decls = "
    'int size(list[&T] _: []) = 0;
    'int size(list[&T] _: [_, *l]) = 1 + size(l);
    '
    'int size(set[&T] _: {}) = 0;
    'int size(set[&T] _: {_, *s}) = 1 + size(s);
", oldName = "size", newName = "sizeof");

test bool arityOverloadFromUse() = {0, 1, 2, 3, 4, 5} == testRenameOccurrences("x = concat(\"foo\", \"bar\");", decls = "
    'str concat(str s) = s;
    'str concat(str s1, str s2) = s1 + concat(s2);
    'str concat(str s1, str s2, str s3) = s1 + concat(s2, s3);
", cursorAtOldNameOccurrence = -1, oldName = "concat", newName = "foo");

test bool arityOverloadFromDef() = {0, 1, 2, 3, 4, 5} == testRenameOccurrences("x = concat(\"foo\", \"bar\");", decls = "
    'str concat(str s) = s;
    'str concat(str s1, str s2) = s1 + concat(s2);
    'str concat(str s1, str s2, str s3) = s1 + concat(s2, s3);
", oldName = "concat", newName = "foo");

test bool overloadClash() = {0} == testRenameOccurrences("", decls = "
    'int foo = 0;
    'int foo(int x) = x when x \< 2;
");

test bool crossModuleOverload() = testRenameOccurrences((
    "Str": <"
        'str concat(str s) = s;
        'str concat(str s1, str s2) = s1 + concat(s2);
    ", {0, 1, 2}>
    , "Main": <"
        'extend Str;
        'str concat(str s1, str s2, str s3) = s1 + concat(s2, s3);
    ", {0, 1}>
), <"Main", "concat", 0>, newName = "conc");

test bool simpleTypeParams() = {0, 1} == testRenameOccurrences("
    '&T foo(&T l) = l;
", oldName = "T", newName = "U");

test bool typeParamsFromReturn() = {0, 1, 2} == testRenameOccurrences("
    '&T foo(&T l) {
    '   &T m = l;
    '   return m;
    '}
", oldName = "T", newName = "U");

test bool keywordTypeParamFromReturn() = {0, 1, 2} == testRenameOccurrences("
    '&T foo(&T \<: int l, &T \<: int kw = 1) = l + kw;
", oldName = "T", newName = "U");

test bool typeParamsFromFormal() = {0, 1, 2} == testRenameOccurrences("
    '&T foo(&T l) {
    '   &T m = l;
    '   return m;
    '}
", oldName = "T", newName = "U", cursorAtOldNameOccurrence = 1);

test bool typeParamsClash() = {0, 1} == testRenameOccurrences("
    '&T foo(&T l, &U m) = l;
", oldName = "T", newName = "U", cursorAtOldNameOccurrence = 1);

test bool typeParamsListReturn() = {0, 1, 2} == testRenameOccurrences("
    'list[&T] foo(&T l) {
    '   list[&T] m = [l, l];
    '   return m;
    '}
", oldName = "T", newName = "U", cursorAtOldNameOccurrence = 1);
