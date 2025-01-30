@license{
Copyright (c) 2018-2025, NWO-I CWI and Swat.engineering
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice,
this list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
POSSIBILITY OF SUCH DAMAGE.
}
@bootstrapParser
module util::refactor::Exception

import analysis::typepal::TModel;

import Message;
import Set;

alias Capture = tuple[loc def, loc use];

data IllegalRenameReason
    = invalidName(str name, str identifierDescription)
    | doubleDeclaration(loc old, set[loc] new)
    | captureChange(set[Capture] captures)
    | definitionsOutsideWorkspace(set[loc] defs)
    ;

data RenameException
    = illegalRename(str message, set[IllegalRenameReason] reason)
    | unsupportedRename(str message, rel[loc location, str message] issues = {})
    | unexpectedFailure(str message)
    ;

str describe(invalidName(name, idDescription)) = "\'<name>\' is not a valid <idDescription>";
str describe(doubleDeclaration(_, _)) = "it causes double declarations";
str describe(captureChange(_)) = "it changes program semantics";
str describe(definitionsOutsideWorkspace(_)) = "it renames definitions outside of currently open projects";

void throwAnyErrors(TModel tm) {
    throwAnyErrors(tm.messages);
}

void throwAnyErrors(set[Message] msgs) {
    throwAnyErrors(toList(msgs));
}

void throwAnyErrors(list[Message] msgs) {
    errors = {msg | msg <- msgs, msg is error};
    if (errors != {}) throw errors;
}
