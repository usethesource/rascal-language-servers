module lang::rascal::tests::rename::FormalParameters

import lang::rascal::tests::rename::TestUtils;
import lang::rascal::lsp::refactor::Exception;

test bool outerNestedFunctionParameter() = {0, 3} == testRenameOccurrences("
    'int f(int foo) {
    '   int g(int foo) {
    '       return foo;
    '   }
    '   return f(foo);
    '}
");

test bool innerNestedFunctionParameter() = {1, 2} == testRenameOccurrences("
    'int f(int foo) {
    '   int g(int foo) {
    '       return foo;
    '   }
    '   return f(foo);
    '}
", cursorAtOldNameOccurrence = 1);

test bool publicFunctionParameter() = {0, 1} == testRenameOccurrences("", decls = "
    'public int f(int foo) {
    '   return foo;
    '}
");

test bool defaultFunctionParameter() = {0, 1} == testRenameOccurrences("", decls = "
    'int f(int foo) {
    '   return foo;
    '}
");

test bool privateFunctionParameter() = {0, 1} == testRenameOccurrences("", decls = "
    'private int f(int foo) {
    '   return foo;
    '}
");

@expected{unsupportedRename}
test bool nestedKeywordParameter() = {0, 1, 2} == testRenameOccurrences("
    'int f(int foo = 8) = foo;
    'int x = f(foo = 10);
");

@expected{unsupportedRename}
test bool keywordParameter() = testRename(
    "int x = f(foo = 10);"
    decls="int f(int foo = 8) = foo;"
);

@expected{illegalRename} test bool doubleParameterDeclaration1() = testRename("int f(int foo, int bar) = 1;");
@expected{illegalRename} test bool doubleParameterDeclaration2() = testRename("int f(int bar, int foo) = 1;");

@expected{illegalRename} test bool doubleNormalAndKeywordParameterDeclaration1() = testRename("int f(int foo, int bar = 9) = 1;");
@expected{illegalRename} test bool doubleNormalAndKeywordParameterDeclaration2() = testRename("int f(int bar, int foo = 8) = 1;");

@expected{illegalRename} test bool doubleKeywordParameterDeclaration1() = testRename("int f(int foo = 8, int bar = 9) = 1;");
@expected{illegalRename} test bool doubleKeywordParameterDeclaration2() = testRename("int f(int bar = 9, int foo = 8) = 1;");

test bool renameParamToConstructorName() = {0, 1} == testRenameOccurrences(
    "int f(int foo) = foo;",
    decls = "data Bar = bar();"
);

@expected{illegalRename}
test bool renameParamToUsedConstructorName() = testRename(
    "Bar f(int foo) = bar(foo);",
    decls = "data Bar = bar(int x);"
);

test bool paremeterShadowsParameter1() = {0, 3} == testRenameOccurrences("
    'int f1(int foo) {
    '   int f2(int foo) {
    '       int baz = 9;
    '       return foo + baz;
    '   }
    '   return f2(foo);
    '}
");

test bool paremeterShadowsParameter2() = {1, 2} == testRenameOccurrences("
    'int f1(int foo) {
    '   int f2(int foo) {
    '       int baz = 9;
    '       return foo + baz;
    '   }
    '   return f2(foo);
    '}
", cursorAtOldNameOccurrence = 1);

@expected{illegalRename}
test bool paremeterShadowsParameter3() = testRename("
    'int f(int bar) {
    '   int g(int baz) {
    '       int h(int foo) {
    '           return bar;
    '       }
    '       return h(baz);
    '   }
    '   return g(bar);
    '}
");

@expected{illegalRename}
test bool captureFunctionParameter() = testRename("
    'int f(int foo) {
    '   int bar = 9;
    '   return foo + bar;
    '}
");

@expected{illegalRename}
test bool doubleVariableAndParameterDeclaration() = testRename("
    'int f(int foo) {
    '   int bar = 9;
    '   return foo + bar;
    '}
");
