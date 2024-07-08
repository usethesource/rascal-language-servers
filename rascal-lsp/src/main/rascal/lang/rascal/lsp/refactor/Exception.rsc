module lang::rascal::lsp::refactor::Exception

data Cursor;

alias Capture = tuple[loc def, loc use];

data IllegalRenameReason
    = invalidName(str name)
    | doubleDeclaration(loc old, set[loc] new)
    | captureChange(set[Capture] captures)
    | definitionsOutsideWorkspace(set[loc] defs)
    ;

data RenameException
    = illegalRename(Cursor cursor, set[IllegalRenameReason] reason)
    | unsupportedRename(rel[loc location, str message] issues)
    | unexpectedFailure(str message)
    ;
