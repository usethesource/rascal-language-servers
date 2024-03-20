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
@bootstrapParser
module util::LanguageServer

import util::Reflective;
import IO;
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
    = language(PathConfig pcfg, str name, set[str] extensions, str mainModule, str mainFunction);

@deprecated{Please upgrade to the new constructor of the Language ADT}
Language language(PathConfig pcfg, str name, str extension, str mainModule, str mainFunction)
    = language(pcfg, name, {extension}, mainModule, mainFunction);

@synopsis{Function profile for parser contributions to a language server}
@description{
    The parser function takes care of parsing the tree once after every change in the IDE.
    This parse tree is then used for both syntax highlighting and other language server functions.
}
@pitfalls {
    * use ((function::ParseTree::parser)) instead of writing your own function to ensure syntax highlighting is fast
    * when customizing token type categories, do not use textmate scopes, but use [LSP's token types](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#semanticTokenTypes)
}
alias Parser           = Tree (str /*input*/, loc /*origin*/);

@synopsis{Function profile for summarizer contributions to a language server}
@description{
Summarizers provide information about the declarations and uses in the current file
which can be used to populate the information needed to implement interactive IDE
features.

There are two places a Summarizer can be called:
* Summarizers can be called after _file save_, in this case we use ((builder))s. Builders typically also have side-effects on disk (leaving generated code or API descriptions in the target folder), and they may run whole-program analysis and compilation steps.
* Or they can be called while typing, in this case we use ((analyzer))s. Analyzers typically use stored or cached information from other files, but focus their own analysis on their own file. Analyzers may use incremental techniques.

A summarizer provides the same information as the following contributors combined:
* ((documenter))
* ((definer))
* ((referrer))
* ((implementer))

The difference is that these contributions are executed on-demand (pulled), while Summarizers
are executed after build or after typing (push).
}
alias Summarizer       = Summary (loc /*origin*/, Tree /*input*/);

@synopsis{Function profile for outliner contributions to a language server}
alias Outliner         = list[DocumentSymbol] (Tree /*input*/);

@synopsis{Function profile for lenses contributions to a language server}
alias LensDetector     = rel[loc src, Command lens] (Tree /*input*/);

@synopsis{Function profile for executor contributions to a language server}
alias CommandExecutor  = value (Command /*command*/);

@synopsis{Function profile for inlay contributions to a language server}
alias InlayHinter      = list[InlayHint] (Tree /*input*/);

@synopsis{Function profile for documentation contributions to a language server}
@description{
A documenter is called on-demand, when documentation is requested by the IDE user.
}
@benefits{
* is focused on a single documentation request, so does not need full program analysis.
}
@pitfalls{
* should be extremely fast in order to provide interactive access.
* careful use of `@memo` may help to cache dependencies, but this is tricky!
}
alias Documenter       = set[str] (loc /*origin*/, Tree /*fullTree*/, Tree /*lexicalAtCursor*/);

@synopsis{Function profile for definer contributions to a language server}
@description{
A definer is called on-demand, when a definition is requested by the IDE user.
}
@benefits{
* is focused on a single definition request, so does not need full program analysis.
}
@pitfalls{
* should be extremely fast in order to provide interactive access.
* careful use of `@memo` may help to cache dependencies, but this is tricky!
}
alias Definer          = set[loc] (loc /*origin*/, Tree /*fullTree*/, Tree /*lexicalAtCursor*/);

@synopsis{Function profile for referrer contributions to a language server}
@description{
A referrer is called on-demand, when a reference is requested by the IDE user.
}
@benefits{
* is focused on a single reference request, so does not need full program analysis.
}
@pitfalls{
* should be extremely fast in order to provide interactive access.
* careful use of `@memo` may help to cache dependencies, but this is tricky!
}
alias Referrer         = set[loc] (loc /*origin*/, Tree /*fullTree*/, Tree /*lexicalAtCursor*/);

@synopsis{Function profile for implementer contributions to a language server}
@description{
An implementer is called on-demand, when an implementation is requested by the IDE user.
}
@benefits{
* is focused on a single implementation request, so does not need full program analysis.
}
@pitfalls{
* should be extremely fast in order to provide interactive access.
* careful use of `@memo` may help to cache dependencies, but this is tricky!
}
alias Implementer      = set[loc] (loc /*origin*/, Tree /*fullTree*/, Tree /*lexicalAtCursor*/);

@synopsis{Each kind of service contibutes the implementation of one (or several) IDE features.}
@description{
Each LanguageService provides one aspect of definining the language server protocol.
* ((parser)) maps source code to a parse tree and indexes each part based on offset and length
* ((analyzer)) indexes a file as a ((Summary)), offering precomputed relations for looking up
documentation, definitions, references, implementations and compiler errors and warnings.
   * ((analyzer))s focus on their own file, but may reuse cached or stored indices from other files.
   * ((analyzer))s have to be quick since they run in an interactive editor setting.
   * ((analyzer))s may store previous results (in memory) for incremental updates.
   * ((analyzer))s are triggered during typing, in a short typing pause.
* ((builder)) is similar to an `analyzer`, but it may perform computation-heavier additional checks.
   * ((builder))s typically run whole-program analyses and compilation steps.
   * ((builder))s have side-effects, they store generated code or code indices for future usage by the next build step, or by the next analysis step.
   * ((builder))s are triggered on _save-file_ events; they _push_ information to an internal cache.
   * Warning: ((builder))s are _not_ triggered when a file changes on disk outside of VS Code; instead, this results in a change event (not a save event), which triggers the ((analyzer)).
* the following contributions are _on-demand_ (pull) versions of information also provided by the analyzer and builder summaries.
   * a ((documenter)) is a fast and location specific version of the `documentation` relation in a ((Summary)).
   * a ((definer)) is a fast and location specific version of the `definitions` relation in a ((Summary)).
   * a ((referrer)) is a fast and location specific version of the `references` relation in a ((Summary)).
   * an ((implementer)) is a fast and location specific version of the `implementations` relation in a ((Summary)).
* ((outliner)) maps a source file to a pretty hierarchy for visualization in the "outline" view
* ((lenses)) discovers places to add "lenses" (little views embedded in the editor on a separate line) and connects commands to execute to each lense
* ((inlayHinter)) discovers plances to add "inlays" (little views embedded in the editor on the same line). Unlike ((lenses)) inlays do not offer command execution.
* ((executor)) executes the commands registered by ((lenses)) and ((inlayHinter))s
}
data LanguageService
    = parser(Parser parser)
    | analyzer(Summarizer summarizer
        , bool providesDocumentation = true
        , bool providesDefinitions = true
        , bool providesReferences = true
        , bool providesImplementations = true)
    | builder(Summarizer summarizer
        , bool providesDocumentation = true
        , bool providesDefinitions = true
        , bool providesReferences = true
        , bool providesImplementations = true)
    | outliner(Outliner outliner)
    | lenses(LensDetector detector)
    | inlayHinter(InlayHinter hinter)
    | executor(CommandExecutor executor)
    | documenter(Documenter documenter)
    | definer(Definer definer)
    | referrer(Referrer reference)
    | implementer(Implementer implementer)
    ;

@deprecated{Please use ((builder)) or ((analyzer))}
@synopsis{A summarizer collects information for later use in interactive IDE features.}
LanguageService summarizer(Summarizer summarizer
        , bool providesDocumentation = true
        , bool providesDefinitions = true
        , bool providesReferences = true
        , bool providesImplementations = true) {
    println("Summarizers are deprecated. Please use builders (triggered on save) and analyzers (triggered on change) instead.");
    return builder(summarizer
        , providesDocumentation = providesDocumentation
        , providesDefinitions = providesDefinitions
        , providesReferences = providesReferences
        , providesImplementations = providesImplementations);
}

@synopsis{A model encodes all IDE-relevant information about a single source file.}
@description{
* `src` refers to the "compilation unit" or "file" that this model is for.
* `messages` collects all the errors, warnings and error messages.
* `documentation` maps uses of concepts to a documentation message that can be shown as a hover.
* `definition` maps use locations to declaration locations to implement "jump-to-definition".
* `references` maps declaration locations to use locations to implement "jump-to-references".
* `implementations` maps the declaration of a type/class to its implementations "jump-to-implementations".
}
data Summary = summary(loc src,
    rel[loc, Message] messages = {},
    rel[loc, str]     documentation = {},
    rel[loc, loc]     definitions = {},
    rel[loc, loc]     references = {},
    rel[loc, loc]     implementations = {}
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

@synopsis{Represents one inlayHint for display in an editor}
@description{
* `position` where the hint should be placed, by default at the beginning of this location, the `atEnd` can be set to true to change this
* `label` text that should be printed in the ide, spaces in front and back of the text are trimmed and turned into subtle spacing to the content around it.
* `kind` his either `type()` or `parameter()` which influences styling in the editor.
* `toolTip` optionally show extra information when hovering over the inlayhint.
* `atEnd` instead of appearing at the beginning of the position, appear at the end.
}
data InlayHint
    = hint(
        loc position,
        str label,
        InlayKind kind,
        str toolTip = "",
        bool atEnd = false
    );

@synopsis{Style of an inlay}
data InlayKind
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
void unregisterLanguage(str name, set[str] extensions, str mainModule = "", str mainFunction = "") {
    unregisterLanguage(language(pathConfig(), name, extensions, mainModule, mainFunction));
}

@deprecated{Replaced by the new overload that takes an set of extensions}
@synopsis{Spins down and removes a previously registered language server}
void unregisterLanguage(str name, str extension, str mainModule = "", str mainFunction = "") {
    unregisterLanguage(name, {extension}, mainModule = mainModule, mainFunction = mainFunction);
}
