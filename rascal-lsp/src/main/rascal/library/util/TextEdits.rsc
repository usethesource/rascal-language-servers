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
module util::TextEdits

import analysis::diff::edits::TextEdits;

@synopsis{A ((analysis::diff::edits::TextEdits::TextEdit)) with additional context for LSP.}
@description{
In LSP, text edits can contain extra information w.r.t. ((analysis::diff::edits::TextEdits::TextEdit)).
* label: Human-readable string that describes the change.
* description: Human-readable string that additionally describes the change, rendered less prominently.
* needsConfirmation: Flags whether the user should confirm this change. By default, this is false, which means that ((util::TextEdits::TextEdit))s are applied without user confirmation.

Typically, clients provide options to group edits by label/description when showing them to the user.
See the [LSP documentation](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#changeAnnotation) for more details.

Note: to easily annotate all text edits in a ((analysis::diff::edits::TextEdits::FileSystemChange)), use the convenience keywords on ((util::TextEdits::FileSystemChange)).
}
@pitfalls{
When `needsConfirmation = false` for all edits, the client will typically apply them without showing any information from the annotations to the user.
}
data TextEdit(str label = "", str description = label, bool needsConfirmation = false);

@synopsis{A ((analysis::diff::edits::TextEdits::FileSystemChange)) with additional context for LSP.}
@description{
Provides extra context for all contained ((util::TextEdits::TextEdit))s at once.
}
data FileSystemChange(str label = "", str description = "", bool needsConfirmation = false);

@synopsis{Shorthand for file changes, with additional context for LSP.}
@description{
Provides extra context for all contained ((util::TextEdits::TextEdit))s at once.
}
FileSystemChange changed(list[TextEdit] edits:[replace(loc l, str _), *_], str label = "", str description = "", bool needsConfirmation = false)
    = changed(l.top, edits, label=label, description=description, needsConfirmation=needsConfirmation);
