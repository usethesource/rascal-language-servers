module lang::rascal::tests::rename::ValidNames

import lang::rascal::tests::rename::TestUtils;
import lang::rascal::lsp::refactor::Exception;

test bool renameToReservedName() {
    edits = getEdits("int foo = 8;", 0, "foo", "int");
    newNames = {name | e <- edits, changed(_, replaces) := e
                     , r <- replaces, replace(_, name) := r};

    return newNames == {"\\int"};
}

@expected{illegalRename}
test bool renameToUsedReservedName() = testRename("
    'int \\int = 0;
    'int foo = 8;
", newName = "int");

@expected{illegalRename}
test bool newNameIsNonAlphaNumeric() = testRename("int foo = 8;", newName = "b@r");

@expected{illegalRename}
test bool newNameIsNumber() = testRename("int foo = 8;", newName = "8");

@expected{illegalRename}
test bool newNameHasNumericPrefix() = testRename("int foo = 8;", newName = "8abc");

@expected{illegalRename}
test bool newNameIsEscapedInvalid() = testRename("int foo = 8;", newName = "\\8int");
