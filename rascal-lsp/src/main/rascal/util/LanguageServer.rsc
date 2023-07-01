@license{
Copyright (c) 2018-2023, NWO-I CWI and Swat.engineering
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
@contributor{Jurgen J. Vinju - Jurgen.Vinju@cwi.nl - CWI}
@contributor{Davy Landman - davy.landman@swat.engineering - Swat.engineering BV}
@synopsis{Bridges {DSL,PL,Modeling} language features to the language server protocol.}
@description{
Using the ((registerLanguage)) function you can connect any parsers, checkers,
source-to-source transformers, visualizers, etc. that are made with Rascal, to the 
Language Server Protocol. 
}
@benefits{
* Turn your language implementation into an interactive IDE at almost zero cost.
}
module util::LanguageServer

import util::Reflective;
import ParseTree;

@synopsis{Definition of a language server by its meta-data}
@description{
The ((registerLanguage)) function takes this as its parameter to generate and run
a fresh language protocol server.
}
@benefits{
* each registered language is run in its own Rascal run-time environment.
* reloading a language is always done in a fresh environment.
}
@pitfalls{
* even though ((registerLanguage)) is called in a given run-time environment,
the registered language runs in another instance of the JVM and of Rascal.
}
data Language
    = language(PathConfig pcfg, str name, str extension, str mainModule, str mainFunction);

@synopsis{Function profile for parser contributions to a language server}
alias Parser           = Tree (str /*input*/, loc /*origin*/);

@synopsis{Function profile for summarizer contributions to a language server}
alias Summarizer       = Summary (loc /*origin*/, Tree /*input*/);

@synopsis{Function profile for outliner contributions to a language server}
alias Outliner         = list[DocumentSymbol] (Tree /*input*/);

//alias Completer        = list[Completion] (Tree /*input*/, str /*prefix*/, int /*requestOffset*/);
//alias Builder          = list[Message] (list[loc] /*sources*/, PathConfig /*pcfg*/);

@synopsis{Function profile for lenses contributions to a language server}
alias LensDetector     = rel[loc src, Command lens] (Tree /*input*/);

@synopsis{Function profile for executor contributions to a language server}
alias CommandExecutor  = value (Command /*command*/);

@synopsis{Function profile for inlay contributions to a language server}
alias InlayHinter      = list[InlayHint] (Tree /*input*/);

// these single mappers get caller for every request that a user makes, they should be quick as possible
// carefull use of memo can help with caching dependencies
@synopsis{Function profile for documentation contributions to a language server}
alias Documenter       = set[str] (loc /*origin*/, Tree /*fullTree*/, Tree /*lexicalAtCursor*/);

@synopsis{Function profile for definer contributions to a language server}
alias Definer          = set[loc] (loc /*origin*/, Tree /*fullTree*/, Tree /*lexicalAtCursor*/);

@synopsis{Function profile for referrer contributions to a language server}
alias Referrer         = set[loc] (loc /*origin*/, Tree /*fullTree*/, Tree /*lexicalAtCursor*/);

@synopsis{Function profile for implementer contributions to a language server}
alias Implementer      = set[loc] (loc /*origin*/, Tree /*fullTree*/, Tree /*lexicalAtCursor*/);

@synopsis{Each kind of service contibutes the implementation of one (or several) IDE features.}
@description{
Each LanguageService provides one aspect of definining the language server protocol.
* a `parser` maps source code to a parse tree and indexes each part based on offset and length
* a `summarizer` indexes a file as a ((Summary)), offering precomputed relations for looking up
documentation, definitions, references, implementations and compiler errors and warnings.
* a `outliner` maps a source file to a pretty hierarchy for visualization in the "outline" view
* a `lenses` discovers places to add "lenses" (little views embedded in the editor) and connects commands to execute to each lense
* an `inlayHinter` is like lenses but inbetween words
* a `executor` executes the commands registered by `lenses` and `inlayHinters`
* a `documenter` is a fast and location specific version of the `documentation` relation in a ((Summary)).
* a `definer` is a fast and location specific version of the `definitions` relation in a ((Summary)).
* a `referrer` is a fast and location specific version of the `references` relation in a ((Summary)).
* an `implementer` is a fast and location specific version of the `implementations` relation in a ((Summary)).
}
data LanguageService
    = parser(Parser parser)
    | summarizer(Summarizer summarizer
        , bool providesDocumentation = true
        , bool providesDefinitions = true
        , bool providesReferences = true
        , bool providesImplementations = true)
    | outliner(Outliner outliner)
// TODO | completer(Completer completer)
// TODO | builder(Builder builder)
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
        loc position, // where the hint should be placed, by default at the beginning of this location, the `atEnd` can be set to true to change this
        str label, // text that should be printed in the ide, spaces in front and back of the text are trimmed and turned into subtle spacing to the content around it.
        InlayKind kind,
        str toolTip = "", // optionally show extra information when hovering over the inlayhint
        bool atEnd = false // instead of appearing at the beginning of the position, appear at the end
    );

data InlayKind // this determines style
    = \type()
    | parameter()
    ;

@javaClass{org.rascalmpl.vscode.lsp.parametric.RascalInterface}
@synopsis{Generates and instantiates a new language server for the given language}
@description{
We register languages by uploading the meta-data of the implementation to a "lanuage-parametric" language server.
1. The meta-data is used to instantiate a fresh run-time to execute the main-module.
2. The extension is registered with the IDE to link to this new runtime.
3. Each specific extension is mapped to a specific part of the language server protocol.

By registering a language twice, more things can happen:
* existing contributions are re-loaded and overwritten with the newest version.
* new contributions to an existing language (`Language` constructor instance), will be added to the existing LSP server instance. You can use this to load expensive features later or more lazily.
* errors appear during loading or first execution of the contribution. The specific contribution is then usually aborted and unregistered.

Because registerLanguage has effect in a different OS process, errors and warnings are not printed in the calling execution context.
In general look at the "Parametric Rascal Language Server" log tab in the IDE to see what is going on.

However since language contributions are just Rascal functions, it is advised to simply test them first right there in the terminal.
Use `util::Reflective::getProjectPathConfig` for a representative configuration.
}
java void registerLanguage(Language lang);


@javaClass{org.rascalmpl.vscode.lsp.parametric.RascalInterface}
@synopsis{Spins down and removes a previously registered language server}
java void unregisterLanguage(Language lang);

@synopsis{Spins down and removes a previously registered language server}
void unregisterLanguage(str name, str extension, str mainModule = "", str mainFunction = "") {
    unregisterLanguage(language(pathConfig(), name, extension, mainModule, mainFunction));
}
