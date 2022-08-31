/*
 * Copyright (c) 2018-2021, NWO-I CWI and Swat.engineering
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
@license{
  Copyright (c) 2021 NWO-I Centrum Wiskunde & Informatica
  All rights reserved. This program and the accompanying materials
  are made available under the terms of the Eclipse Public License v1.0
  which accompanies this distribution, and is available at
  http://www.eclipse.org/legal/epl-v10.html
}
@contributor{Jurgen J. Vinju - Jurgen.Vinju@cwi.nl - CWI}
module util::LanguageServer

import util::Reflective;

import ParseTree;

data Language
    = language(PathConfig pcfg, str name, str extension, str mainModule, str mainFunction);

alias Parser           = Tree (str /*input*/, loc /*origin*/);
alias Summarizer       = Summary (loc /*origin*/, Tree /*input*/);
alias Outliner         = list[DocumentSymbol] (Tree /*input*/);
alias Completer        = list[Completion] (Tree /*input*/, str /*prefix*/, int /*requestOffset*/);
alias Builder          = list[Message] (list[loc] /*sources*/, PathConfig /*pcfg*/);
alias LensDetector     = rel[loc src, Command lens] (Tree /*input*/);
alias CommandExecutor  = value (Command /*command*/);
alias InlayHinter      = list[InlayHint] (Tree /*input*/);
// these single mappers get caller for every request that a user makes, they should be quick as possible
// carefull use of memo can help with caching dependencies
alias Documenter       = set[str] (loc /*origin*/, Tree /*fullTree*/, Tree /*lexicalAtCursor*/);
alias Definer          = set[loc] (loc /*origin*/, Tree /*fullTree*/, Tree /*lexicalAtCursor*/);
alias Referrer         = set[loc] (loc /*origin*/, Tree /*fullTree*/, Tree /*lexicalAtCursor*/);
alias Implementer      = set[loc] (loc /*origin*/, Tree /*fullTree*/, Tree /*lexicalAtCursor*/);

@synopsis{Each kind of service contibutes the implementation of one (or several) IDE features.}
data LanguageService
    = parser(Parser parser)
    | summarizer(Summarizer summarizer
        , bool providesDocumentation = true
        , bool providesDefinitions = true
        , bool providesReferences = true
        , bool providesImplementations = true)
    | outliner(Outliner outliner)
    | completer(Completer completer)
    | builder(Builder builder)
    | lenses(LensDetector detector)
    | inlayHinter(InlayHinter hinter)
    | executor(CommandExecutor executor)
    | documenter(Documenter documenter)
    | definer(Definer definer)
    | referrer(Referrer reference)
    | implementer(Implementer implementer)
    ;

@synopsis{A model encodes all IDE-relevant information about a single source file.}
data Summary = summary(loc src,
    rel[loc, Message] messages = {},
    rel[loc, str]     documentation = {},   // documentation for each location
    rel[loc, loc]     definitions = {},     // links to the definitions of names
    rel[loc, loc]     references = {},      // links to the uses of definitions
    rel[loc, loc]     implementations = {}  // links to the implementations of declarations
);

data Completion = completion(str newText, str proposal=newText);

@synopsis{DocumentSymbol encodes a sorted and hierarchical outline of a source file}
data DocumentSymbol
    = symbol(
        str name,
        DocumentSymbolKind kind,
        loc range,
        loc selection=range,
        str detail="",
        list[DocumentSymbol] children=[]
    );

data DocumentSymbolKind
	= \file()
	| \module()
	| \namespace()
	| \package()
	| \class()
	| \method()
	| \property()
	| \field()
	| \constructor()
	| \enum()
	| \interface()
	| \function()
	| \variable()
	| \constant()
	| \string()
	| \number()
	| \boolean()
	| \array()
	| \object()
	| \key()
	| \null()
	| \enumMember()
	| \struct()
	| \event()
	| \operator()
	| \typeParameter()
    ;

data DocumentSymbolTag
    = \deprecated()
    ;

data CompletionProposal = sourceProposal(str newText, str proposal=newText);

data Command(str title="")
    = noop()
    ;

data InlayHint
    = hint(
        loc position, // should point to a cursor position (begin and end column same)
        str label, // text that should be printed in the ide, spaces in front and back of the text are trimmed and turned into subtle spacing to the content around it.
        InlayKind kind,
        str toolTip = "" // optionally show extra information when hovering over the inlayhint
    );

data InlayKind // this determines style
    = \type()
    | parameter()
    ;

@javaClass{org.rascalmpl.vscode.lsp.parametric.RascalInterface}
java void registerLanguage(Language lang);
