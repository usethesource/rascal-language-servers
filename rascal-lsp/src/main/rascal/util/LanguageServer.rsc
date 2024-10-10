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
import analysis::diff::edits::TextEdits;
import IO;
import ParseTree;
import Message;

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
* use `ParseTree::parser` instead of writing your own function to ensure syntax highlighting is fast
}
alias Parser           = Tree (str _input, loc _origin);

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
alias Summarizer       = Summary (loc _origin, Tree _input);

@synopsis{A focus provides the currently selected language constructs around the cursor.}
@description{
A ((Focus)) list starts with the bottom tree, commonly a lexical identifier if
the cursor is inside an identifer, and ends with the start non-terminal (the whole tree). Everything
in between is a spine of language constructs ((Library:ParseTree)) nodes between the top and the bottom node.

The location of each element in the focus list is around (inclusive) the current cursor selection.
This means that:
* every next element in the list is one of the children of the previous.
* typically the list starts with a smallest tree and ends with the entire `start` tree.
* singleton lists may occur in case the cursor is on a layout or literal element of the top production.
* the `start[X]` tree is typically preceded by the `X` tree.
* the first tree is a whole lexical tree if the cursor is inside an identifier or constant
* the first tree is a (context-free) syntax tree if the cursor is on the whitespace in between literals and lexicals.
* the focus list may be empty in case of top-level ambiguity or parse errors.

The ((Focus)) is typically provided to the ((LanguageService))s below, such that language
engineers can provide language-directed tools, which are relevant to the current interest
of the user.
}
@benefits{
* All functions that accept a ((Focus)) can use list matching to filter locations of interest.
}
@pitfalls{
* Functions that use list matching on their ((Focus)) parameter must provide a default that returns the empty list or empty set.
}
alias Focus = list[Tree];

@synopsis{Function profile for outliner contributions to a language server}
alias Outliner         = list[DocumentSymbol] (Tree _input);

@synopsis{Function profile for lenses contributions to a language server}
alias LensDetector     = rel[loc src, Command lens] (Tree _input);

@synopsis{Function profile for executor contributions to a language server}
alias CommandExecutor  = value (Command _command);

@synopsis{Function profile for inlay contributions to a language server}
alias InlayHinter      = list[InlayHint] (Tree _input);

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
@deprecated{The ((FocusDocumenter)) has replaced this type.}
alias Documenter = set[str] (loc _origin, Tree _fullTree, Tree _lexicalAtCursor);

@synopsis{Function profile for documentation contributions to a language server}
@description{
A ((FocusDocumenter)) is called on-demand, when documentation is requested by the IDE user.
The current selection is used to create a ((Focus)) that we can use to select the right
functionality with. It is possible several constructs are in "focus", and then we can
provide several pieces of documentation.
}
@benefits{
* is focused on a single documentation request, so does not need full program analysis.
}
@pitfalls{
* should be extremely fast in order to provide interactive access.
* careful use of `@memo` may help to cache dependencies, but this is tricky!
}
alias FocusDocumenter = set[str] (Focus _focus);

@synopsis{Function profile for retrieving code actions focused around the current cursor}
@description{
Next to the quickfix commands that may be attached to diagnostic ((Message))s, the LSP
can produce refactoring and quickfix or visualization actions specific for what is near
or under the current cursor.

An action contributor is called on demand when a user presses a light-bulb or asks for quick-fixes.
The implementor is asked to produce only actions that pertain what is under the current cursor.
}
alias CodeActionContributor = list[CodeAction] (Focus _focus);

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
@deprecated{Use ((FocusDefiner)) instead.}
alias Definer = set[loc] (loc _origin, Tree _fullTree, Tree _lexicalAtCursor);

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
alias FocusDefiner = set[loc] (Focus _focus);

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
@deprecated{Use ((FocusReferrer)) instead}
alias Referrer = set[loc] (loc _origin, Tree _fullTree, Tree _lexicalAtCursor);

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
alias FocusReferrer = set[loc] (list[Tree] _focus);

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
@deprecated{Use ((FocusImplementer)) instead.}
alias Implementer = set[loc] (loc _origin, Tree _fullTree, Tree _lexicalAtCursor);

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
alias FocusImplementer = set[loc] (Focus _focus);

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
* ((executor)) executes the commands registered by ((lenses)) and ((inlayHinter))s.

Many language contributions received a ((Focus)) parameter. This helps to create functionality that
is syntax-directed: relevant to the current syntactical constructs under the cursor.
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
    | documenter(FocusDocumenter documenter)
    | definer(FocusDefiner definer)
    | referrer(FocusReferrer reference)
    | implementer(FocusImplementer implementer)
    | actions(CodeActionContributor actions)
    ;

@deprecated{
This is a backward compatibility layer for the pre-existing ((Documenter)) alias.

To replace an old-style ((Documenter)) with a new style ((FocusDocumenter)) follow
this scheme:

```rascal
set[loc] oldImplementer(loc document, Tree selection, Tree fullTree) {
    ...
}
// by this scheme:
set[loc] newImplementer([Tree selection, *Tree _spine, Tree fullTree]) {
  loc document = selection@\loc.top;
  ...
}
default set[loc] newImplementer(list[Tree] _focus) = {};
```
}
LanguageService documenter(Documenter d) {
    set[str] focusAcceptor([Tree lex, *Tree _spine, Tree fullTree]) {
        return d(lex@\loc.top, fullTree, lex);
    }

    default set[str] focusAcceptor (list[Tree] _focus) {
        return {};
    }

    return documenter(focusAcceptor);
}


@deprecated{
This is a backward compatibility layer for the pre-existing ((Definer)) alias.

To replace an old-style ((Definer)) with a new style ((FocusDefiner)) follow
this scheme:

```rascal
set[loc] oldDefiner(loc document, Tree selection, Tree fullTree) {
    ...
}
// by this scheme:
set[loc] newDefiner([Tree selection, *Tree _spine, Tree fullTree]) {
  loc document = selection@\loc.top;
  ...
}
default set[loc] newDefiner(list[Tree] _focus) = {};
```
}
LanguageService definer(Definer d) {
    set[loc] focusAcceptor([Tree lex, *Tree _spine, Tree fullTree]) {
        return d(lex@\loc.top, fullTree, lex);
    }

    default set[loc] focusAcceptor (list[Tree] _focus) {
        return {};
    }

    return definer(focusAcceptor);
}


@synopsis{Registers an old-style ((Referrer))}
@deprecated{
This is a backward compatibility layer for the pre-existing ((Referrer)) alias.

To replace an old-style ((Referrer)) with a new style ((FocusReferrer)) follow
this scheme.

```rascal
set[loc] oldReferrer(loc document, Tree selection, Tree fullTree) {
    ...
}
// by this scheme:
set[loc] newReferrer([Tree selection, *Tree _spine, Tree fullTree]) {
  loc document = selection@\loc.top;
  ...
}
default set[loc] newReferrer(list[Tree] _focus) = {};
```
}
LanguageService referrer(Referrer d) {
    set[loc] focusAcceptor([Tree lex, *Tree _spine, Tree fullTree]) {
        return d(lex@\loc.top, fullTree, lex);
    }

    default set[loc] focusAcceptor (list[Tree] _focus) {
        return {};
    }

    return referrer(focusAcceptor);
}

@synopsis{Registers an old-style ((Implementer))}
@deprecated{
This is a backward compatibility layer for the pre-existing ((Implementer)) alias.

To replace an old-style ((Implementer)) with a new style ((FocusImplementer)) follow
this scheme:

```rascal
set[loc] oldImplementer(loc document, Tree selection, Tree fullTree) {
    ...
}
// by this scheme:
set[loc] newImplementer([Tree selection, *Tree _spine, Tree fullTree]) {
  loc document = selection@\loc.top;
  ...
}
```
}
LanguageService implementer(Implementer d) {
    set[loc] focusAcceptor([Tree lex, *Tree _spine, Tree fullTree]) {
        return d(lex@\loc.top, fullTree, lex);
    }

    default set[loc] focusAcceptor (list[Tree] _focus) {
        return {};
    }

    return implementer(focusAcceptor);
}

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
* `definitions` maps use locations to declaration locations to implement "jump-to-definition".
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

@synopsis{Attach any command to a message for it to be exposed as a quick-fix code action automatically.}
@description{
The fixes you provide with a message will be hinted at by a light-bulb in the editor's margin.
Every fix listed here will be a menu item in the pop-up menu when the bulb is activated (via short-cut or otherwise).

Note that for a ((CodeAction)) to be executed, you must either provide `edits` directly and/or handle
a ((util::LanguageServer::Command)) and add its execution to the ((CommandExecutor)) contribution function.
}
@benefits{
* the information required to produce an error message is usually also required for the fix. So this
coupling of message with fixes may come in handy.
}
@pitfalls{
* the code for error messaging may become cluttered with code for fixes. It is advisable to only _collect_ information for the fix
and store it in a ((util::LanguageServer::Command)) constructor inside the ((CodeAction)), or to delegate to a function that produces
the right ((DocumentEdit))s immediately.
* don't forget to extend ((util::LanguageServer::Command)) with a new constructor and ((CommandExecutor)) with a new overload to handle that constructor.
}
data Message(list[CodeAction] fixes = []);

@synopsis{A Command is a parameter to a CommandExecutor function.}
@description{
Commands can be any closed term a() pure value without open variables or function/closure values embedded in it). Add any constructor you need to express the execution parameters
of a command.

You write the ((CommandExecutor)) to interpret each kind of ((util::LanguageServer::Command)) individually.
A ((Command) constructor must have fields or keyword fields that hold the parameters of the
to-be-executed command.

Commands are produced for delayed and optional execution by:
* ((LensDetector)), where the will be executed if the lens is selected in the editor
* ((CodeActionContributor)), where they will appear in context-menus for quick-fix and refactoring
* ((Message)), where they will appear in context-menus on lines with error or warning diagnostics

See also ((CodeAction)); a wrapper for ((util::LanguageServer::Command)) for fine-tuning UI interactions.
}
@examples{
```rascal
// here we invent a new command name `showFlowDiagram` which is parametrized by a loc:
data Command = showFlowDiagram(loc src);

// and we have our own evaluator that executes the showFlowDiagram command by starting an interactive view:
value evaluator(showFlowDiagram(loc src)) {
    showInteractiveContent(flowDiagram(src));
    return true;
}
```
}
@pitfalls{
* Sometimes a command must be wrapped in a ((CodeAction)) to make it effective (see ((CodeActionContributor)) and ((Message)) )
* the `noop()` command will always be ignored.
* _never_ add first-class functions or closures as a parameter or keyword field to a `Command`. The Command will
be serialized, sent to the LSP client, and then sent back to the LSP server for execution. Functions can not be
serialized, so that would lead to run-time errors.
}
data Command(str title="")
    = noop()
    ;

@synopsis{Code actions encapsulate computed effects on source code like quick-fixes, refactorings or visualizations.}
@description{
Code actions are an intermediate representation of what is about to happen to the source code that is loaded in the IDE,
or even in a live editor. They communicate what can (possibly) be done to improve the user's code, who might choose one of the options
from a list, or even look at different outcomes ahead-of-time in a preview.

The `edits` and `command` parameters are both optional, and can be provided at the same time as well.

If ((DocumentEdit))[edits] are provided then:
1. edits can be used for preview of a quick-fix of refactoring
2. edits are always applied first before any `command` is executed.
3. edits can always be undone via the undo command of the IDE

If a ((util::LanguageServer::Command))[command] is provided, then:
1. The title of the command is shown to the user
2. The user picks this code action (from a list or pressed "OK" in a dialog)
3. Any `edits` (see above) are applied first
4. The command is executed on the server side via the ((CommandExecutor)) contribution
   * Many commands use ((util::IDEServices::applyDocumentsEdits)) to provide additional changes to the input
   * Other commands might use ((util::IDEServices::showInteractiveContent)) to start a linked webview inside of the IDE
   * Also ((util::IDEServices::registerDiagnostics)) is a typical effect of a ((CodeAction)) ((util::LanguageServer::Command)).
5. The effects of commands can be undone if they where ((DocumentEdit))s, but other effects like diagnostics and
interactive content have to be cleaned or closed in their own respective fashions.
}
@benefits{
* CodeActions provide tight integration with the user experience in the IDE. Including sometimes previews, and always the undo stack.
* CodeActions can be implemented "on the language level", abstracting from UI and scheduling details. See also ((analysis::diff::edits)) for
tools that can produce lists of ((DocumentEdit))s by diffing parse trees or abstract syntax trees.
* `edits` are applied on the latest editor content for the current editor; live to the user.
* ((util::IDEServices::applyDocumentsEdits)) also works on open editor contents for the current editor.
* The parse tree for the current file is synchronized with the call to a ((CodeActionContributor)) such that edits
and input are computed in-sync.
}
@pitfalls{
* ((util::IDEServices::applyDocumentsEdits)) and `edits` when pointing to other files than the current one, may
or may not work on the current editor contents. If you want to be safe it's best to only edit the current file.
}
data CodeAction
    = action(
        list[DocumentEdit] edits = [],
        Command command          = noop(),
        str title                = command.title,
        CodeActionKind kind      = quickfix()
    );

@synopsis{Kinds are used to prioritize menu options and choose relevant icons in the UI.}
@description{
This is an _open_ data type. The constructor names are used
to compute the string values for the LSP by capitalizing and
joining parent/children with periods.
}
@examples{
`refactor(rewrite())` becomes `refactor.rewrite` under the hood of the LSP.
}
data CodeActionKind
    = empty()
    | refactor(RefactorKind refactor = rewrite())
    | quickfix()
    | source(SourceActionKind source = organizeImports())
    ;

@synopsis{Used to prioritize menu options and choose relevant icons in the UI.}
@description{
This is an open list and can be extended by the language engineer at will.
These names should be indicative of what will happen to the source code when the action is chosen.
}
@pitfalls{
* You as language engineer are responsible for implementing the right action with these names.
}
data SourceActionKind
    = organizeImports()
    | fixAll()
    ;

@synopsis{Used to prioritize menu options and choose relevant icons in the UI.}
@description{
This is an open list and can be extended by the language engineer at will.
These names should be indicative of what will happen to the source code when the action is chosen.
}
@pitfalls{
* You as language engineer are responsible for implementing the right action with these names.
}
data RefactorKind
    = extract()
    | inline()
    | rewrite()
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
