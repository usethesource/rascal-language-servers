module lang::rascal::lsp::refactor::TextEdits

extend analysis::diff::edits::TextEdits;

alias ChangeAnnotationId = str;

data ChangeAnnotation
    = changeAnnotation(str label, str description, bool needsConfirmation)
    ;

data TextEdit
    = replace(loc range, str replacement, ChangeAnnotationId annotation)
    ;

alias ChangeAnnotationRegister =
    ChangeAnnotationId(str label, str description, bool needsConfirmation);
