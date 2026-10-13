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
module lang::rascal::lsp::Formatter

import ParseTree;
import analysis::diff::edits::TextEdits;
import lang::rascal::\syntax::Rascal;
extend lang::rascal::format::Rascal;
import util::Formatters;

// TODO: change this code to keep the formatter function in a private global
// constant variable after `RAP: keyword fields in function types` has been implemented.
// We can then pass the `opts` parameter to the generated function instead of the
// generator. Currently there is no way to pass keyword parameters to higher-order functions in Rascal.
// See also https://github.com/usethesource/rascal/issues/2077
@synopsis{Whole file formatter for Rascal}
list[TextEdit] rascalFormattingService([start[Module] top], FormattingOptions opts)
    = treeEditFormatter(#start[Module], toBox, opts = opts)(top);

@synopsis{Range formatter for Rascal}
default list[TextEdit] rascalFormattingService([Tree selected, *_], FormattingOptions opts)
    = subTreeEditFormatter(#start[Module], toBox, opts = opts)(selected);
