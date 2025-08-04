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
@contributor{Jurgen J. Vinju - Jurgen.Vinju@cwi.nl - CWI}
@contributor{Davy Landman - davy.landman@swat.engineering - Swat.engineering BV}
@contributor{Sung-Shik Jongmans - sung-shik.jongmans@swat.engineering - Swat.engineering BV}
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
import Exception;

@synopsis{Definition of a language server by its meta-data.}
@description{
The ((registerLanguage)) function takes this as its parameter to generate and run
a fresh language protocol server. Every language server is run in its own Rascal execution
environment. The ((Language)) data-type defines the parameters of this run-time, such
that ((registerLanguage)) can boot and initialize new instances.

* `pcfg` sets up search paths for Rascal modules and libraries required to run the language server
* `name` is the name of the language
* `extensions` are the file extensions that bind this server to editors of files with these extensions.
* `mainModule` is the Rascal module to load to start the language server
* `mainFunction` is a function of type `set[LanguageService] ()` that produces the implementation of the language server
as a set of collaborating ((LanguageService))s.
}
@benefits{
* each registered language is run in its own Rascal run-time environment.
* reloading a language is always done in a fresh environment.
* instances of ((Language)) can be easily serialized and communicated in interactive language engineering environments.
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
@deprecated{Used only in deprecated functions}
alias Parser           = Tree (str _input, loc _origin);

@synopsis{Function profile for summarizer contributions to a language server}
@deprecated{Used only in deprecated functions}
alias Summarizer       = Summary (loc _origin, Tree _input);

@synopsis{A focus provides the currently selected language constructs around the cursor.}
@description{
A ((Focus)) list starts with the bottom tree, commonly a lexical identifier if
the cursor is inside an identifer, and ends with the start non-terminal (the whole tree). Everything
in between is a spine of language constructs ((Library:ParseTree)) nodes between the top and the bottom node.

The location of each element in the focus list is around (inclusive) the current cursor selection.
This means that:
* every next element in the list is (one of) the parents of the previous.
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
@deprecated{Only in use in deprecated functions.}
alias Outliner         = list[DocumentSymbol] (Tree _input);

@synopsis{Function profile for lenses contributions to a language server}
@deprecated{Only in use in deprecated functions.}
alias LensDetector     = rel[loc src, Command lens] (Tree _input);

@synopsis{Function profile for lenses contributions to a language server}
alias OrderedLensDetector     = lrel[loc src, Command lens] (Tree _input);

@synopsis{Function profile for executor contributions to a language server}
@deprecated{Only in use in deprecated functions.}
alias CommandExecutor  = value (Command _command);

@synopsis{Function profile for inlay contributions to a language server}
@deprecated{Only in use in deprecated functions.}
alias InlayHinter      = list[InlayHint] (Tree _input);

@deprecated{Only in use in deprecated functions}
alias Documenter = set[str] (loc _origin, Tree _fullTree, Tree _lexicalAtCursor);

@deprecated{Only in use in deprecated functions}
alias CodeActionContributor = list[CodeAction] (Focus _focus);

@synopsis{Function profile for definer contributions to a language server}
@deprecated{Use ((definition)) instead.}
alias Definer = set[loc] (loc _origin, Tree _fullTree, Tree _lexicalAtCursor);

@synopsis{Function profile for referrer contributions to a language server}
@deprecated{Use ((references)) instead}
alias Referrer = set[loc] (loc _origin, Tree _fullTree, Tree _lexicalAtCursor);

@synopsis{Function profile for implementer contributions to a language server}
@deprecated{Use ((implementation)) instead.}
alias Implementer = set[loc] (loc _origin, Tree _fullTree, Tree _lexicalAtCursor);

@synopsis{Each kind of service contibutes the implementation of one (or several) IDE features.}
@description{
Each LanguageService constructor provides one aspect of definining the language server protocol (LSP).
Their names coincide exactly with the services which are documented [here](https://microsoft.github.io/language-server-protocol/).

* The ((parsing)) service that maps source code strings to a ((ParseTree::Tree)) is essential and non-optional.
All other other services are optional.
   * By providing a parser which produces annotated parse ((ParseTree::Tree))s, editor features such as parse error locations, syntax highlighting and
selection assistance are immediately enabled.
   * The ((parsing)) service is activated after every change in an editor document (when a suitable pause has occurred)
   * All downstream services are based on the ((ParseTree::Tree)) that is produced here. In
particular downstream services make use of the `src` origin fields that the parser must produce.
   * Parsers can be obtained automatically using the ((ParseTree::parser)) or ((ParseTree::parsers)) functions, like so `parser(#start[Program])`.
Like this a fast parser is obtained that does not require a global interpreter lock. If you pass in a normal Rascal function, which is fine, the global
interpreter lock will make the editor services less responsive.
   * Currently, `@category` tags are ignored in the following special case:
        * if a parse tree has a `syntax` non-terminal node `n` with a category
          (either declared as part of `n`, or inherited from an ancestors),
        * and if `n` has a `syntax` non-terminal node `m` as a child,
        * then the category of `n` is ignored in the subtree rooted at `m`
          (regardless of whether a category is declared as part of `m`).
     This special case is deprecated and will be removed in a future release. In
     anticipation of the removal, users that rely on this special case for
     syntax highlighting can update their grammars and explicitly opt-out of the
     special case by passing `usesSpecialCaseHighlighting = false` when
     registering the ((parsing)) service.
* The ((analysis)) service indexes a file as a ((Summary)), offering precomputed relations for looking up
hover documentation, definition with uses, references to declarations, implementations of types and compiler errors and warnings.
   * ((analysis)) focuses on their own file, but may reuse cached or stored indices from other files.
   * ((analysis)) has to be quick since they run in an interactive editor setting.
   * ((analysis)) may store previous results (in memory) for incremental updates.
   * ((analysis)) is triggered on-demand during typing, in a short typing pause. So you have to provide a reasonable fast function (0.5 seconds is a good target response time).
   * ((analysis)) pushes their result on a local stack; which is efficiently queried by the LSP features on-demand.
* The ((util::LanguageServer::build)) service is similar to an ((analysis)), but it may perform computation-heavier additional checks or take time generate source code and binary code that makes the code in the editor executable.
   * ((util::LanguageServer::build))s typically run whole-program analyses and compilation steps.
   * ((util::LanguageServer::build))s have side-effects, they store generated code or code indices for future usage by the next build step, or by the next analysis step.
   * ((util::LanguageServer::build))s are triggered on _save-file_ events; they _push_ information to an internal cache.
   * Warning: ((util::LanguageServer::build))s are _not_ triggered when a file changes on disk outside of VS Code; instead, this results in a change event (not a save event), which triggers the ((analyzer)).
   * If `providesDocumentation` is false, then the ((hover)) service may be activated. Same for `providesDefinitions` and `providesDocumentation`
))
* the following contributions are _on-demand_ (pull) versions of information also provided by the ((analysis)) and ((util::LanguageServer::build)) summaries.
   * you can provide these more lightweight on-demand services _instead of_ the ((Summary)) versions.
   * these functions are run synchronously after a user interaction. The run-time of each service corresponds directly to the UX response time.
   * a ((hover)) service is a fast and location specific version of the `documentation` relation in a ((Summary)).
   * a ((definition)) service is a fast and location specific version of the `definitions` relation in a ((Summary)).
   * a ((references)) service is a fast and location specific version of the `references` relation in a ((Summary)).
   * an ((implementation)) service is a fast and location specific version of the `implementations` relation in a ((Summary)).
* The ((documentSymbol)) service maps a source file to a pretty hierarchy for visualization in the "outline" view and "symbol search" features.
* The ((codeLens)) service discovers places to add "lenses" (little views embedded in the editor on a separate line) and connects commands to execute to each lense
* The ((inlayHint)) service discovers plances to add "inlays" (little views embedded in the editor on the same line). Unlike ((lenses)) inlays do not offer command execution.
* The ((execution)) service executes the commands registered by ((lenses)) and ((inlayHinter))s.
* The ((actions)) service discovers places in the editor to add "code actions" (little hints in the margin next to where the action is relevant) and connects ((CodeAction))s to execute when the users selects the action from a menu.
* The ((util::LanguageServer::rename)) service renames an identifier by collecting the edits required to rename all occurrences of that identifier. It might fail and report why in diagnostics.
   * The optional `prepareRename` service argument discovers places in the editor where a ((util::LanguageServer::rename)) is possible. If renameing the location is not supported, it should throw an exception.
* The ((didRenameFiles)) service collects ((DocumentEdit))s corresponding to renamed files (e.g. to rename a class when the class file was renamed). The IDE applies the edits after moving the files. It might fail and report why in diagnostics.
* The ((selectionRange)) service discovers selections around a cursor, that a user might want to select. It expects the list of source locations to be in ascending order of size (each location should be contained by the next) - similar to ((Focus)) trees.

Many services receive a ((Focus)) parameter. The focus lists the syntactical constructs under the current cursor, from the current
leaf all the way up to the root of the tree. This list helps to create functionality that is syntax-directed, and always relevant to the
programmer.

To start developing an LSP extension step-by-step:
1. first write a SyntaxDefinition in Rascal and register it via the ((parsing)) service. Use ((registerLanguage)) from the terminal ((REPL-REPL)) to
test it immediately. Create some example files for your language to play around with.
2. either make an ((analysis)) service that produces a ((Summary)) _or_ start ((hover)), ((definition)), ((references)) and ((implementation))
lookup services. Each of those four services require the same information that is useful for filling a ((Summary)) with an ((analysis)) or a ((builder)).
3. the ((documentSymbol)) service is next, good for the outline view and also quick search features.
4. the to add interactive features, optionally ((inlayHint)), ((codeLens)) and ((codeAction)) can be created to add visible hooks in the UI to trigger
your own ((CodeAction))s and Commands
   * create an ((execution)) service to give semantics to each command. This includes creating ((DocumentEdit))s but also ((util::IDEServices))
   can be used to have interesting effects in the IDE.
   * ((CodeAction))s can also be attached to error, warning and into ((Message))s as a result of ((parsing)), ((analysis)) or ((util::LanguageServer::build)).
   Such actions will lead to "quick-fix" UX options in the editor.
}
@benefits{
* You can create editor services thinking only of your programming language or domain-specific language constructs. All of the communication
and (de)serialization and scheduling is taken care of.
* It is always possible and useful to test your services manually in the ((REPL-REPL)). This is the preferred way of testing and debugging language services.
* Except for the ((parsing)) service, all services are independent of each other. If one fails, or is removed, the others still work.
* Language services in general can be unit-tested easily by providing example parse trees and testing properties of their output. Write lots of test functions!
* LanguageServices are editor-independent/IDE-independent via the LSP protocol. In principle they can work with any editor that implements LSP 3.17 or higher.
* Older Eclipse DSL plugins via the rascal-eclipse plugin are easily ported to ((util::LanguageServer)).
}
@pitfalls{
* If one of the services does not type-check in Rascal, or throws an exception at ((registerLanguage)) time, the extension fails completely. Typically the editor produces a parse error on the first line of the code. The
failure is printed in the log window of the IDE.
* Users have expectations with the concepts of ((references)), ((definition)), ((implementation)) which are based on
typical programming language concepts. Since these are all just `rel[loc, loc]` it can be easy to confound them.
   * ((references)) point from declarations sites to use sites
   * ((definition)) points the other way around, from a use to the declaration, but only if a value is associated there explicitly or implicitly.
   * ((implementation)) points from abstract declarations (interfaces, classes, function signatures) to more concrete realizations of those declarations.
* `providesDocumentation` is deprecated. Use `providesHovers` instead.
}
data LanguageService
    = parsing(Tree (str _input, loc _origin) parsingService
        , bool usesSpecialCaseHighlighting = true)
    | analysis(Summary (loc _origin, Tree _input) analysisService
        , bool providesDocumentation = true
        , bool providesHovers = providesDocumentation
        , bool providesDefinitions = true
        , bool providesReferences = true
        , bool providesImplementations = true)
    | build(Summary (loc _origin, Tree _input) buildService
        , bool providesDocumentation = true
        , bool providesHovers = providesDocumentation
        , bool providesDefinitions = true
        , bool providesReferences = true
        , bool providesImplementations = true)
    | documentSymbol(list[DocumentSymbol] (Tree _input) documentSymbolService)
    | codeLens      (lrel[loc src, Command lens] (Tree _input) codeLensService)
    | inlayHint     (list[InlayHint] (Tree _input) inlayHintService)
    | execution     (value (Command _command) executionService)
    | hover         (set[str] (Focus _focus) hoverService)
    | definition    (set[loc] (Focus _focus) definitionService)
    | references    (set[loc] (Focus _focus) referencesService)
    | implementation(set[loc] (Focus _focus) implementationService)
    | codeAction    (list[CodeAction] (Focus _focus) codeActionService)
    | rename        (tuple[list[DocumentEdit], set[Message]] (Focus _focus, str newName) renameService
        , loc (Focus _focus) prepareRenameService = defaultPrepareRenameService)
    | didRenameFiles(tuple[list[DocumentEdit], set[Message]] (list[DocumentEdit] fileRenames) didRenameFilesService)
    | selectionRange(list[loc](Focus _focus) selectionRangeService)
    | callHierarchy (set[loc] (Focus _focus, Summary _s) callHierarchyService)
    | incomingCalls (rel[loc toDef, loc call] (CallHierarchyItem _f, Tree _input, Summary _s) incomingCallsService)
    | outgoingCalls (rel[loc fromDef, loc call] (CallHierarchyItem _f, Tree _input, Summary _s) outgoingCallsService)
    ;

loc defaultPrepareRenameService(Focus _:[Tree tr, *_]) = tr.src when tr.src?;
default loc defaultPrepareRenameService(Focus focus) { throw IllegalArgument(focus, "Element under cursor does not have source location"); }

data CallHierarchyItem
    = item(
        str name,
        DocumentSymbolKind kind,
        loc src,
        loc selection = src, // location of `name` typically needs to come from parse tree
        set[DocumentSymbolTag] tags = {}, // as of now only `deprecated()`, probably unused often
        str detail = "", // e.g. function signature
        value \data = () // to share state between `prepareCallHierarchy` and `incomingCalls`/`outgoingCalls`
    );

@deprecated{Backward compatible with ((parsing)).}
@synopsis{Construct a `parsing` ((LanguageService))}
LanguageService parser(Parser parser) = parsing(parser);

@deprecated{Backward compatible with ((codeLens))}
@synopsis{Construct a ((codeLens)) ((LanguageService))}
@description{
Not only translates to the old name of the LanguageService,
it also maps the list to an arbitrarily ordered set as it was before.
}
@benefits{
* If you need your lenses in a stable order in the editor,
use the ((codeLens)) constructor instead to provide a function that
uses an ordered list.
}
LanguageService lenses(LensDetector detector) = codeLens(lrel[loc src, Command lens] (Tree input) {
    return [*detector(input)];
});

@deprecated{Backward compatible with ((codeAction))}
@synopsis{Construct a ((codeAction)) ((LanguageService))}
LanguageService actions(CodeActionContributor contributor) = codeAction(contributor);

@deprecated{Backward compatible with ((util::LanguageServer::build))}
@synopsis{Construct a ((util::LanguageServer::build)) ((LanguageService))}
LanguageService builder(Summarizer summarizer) = build(summarizer);

@deprecated{Backward compatible with ((documentSymbol))}
@synopsis{Construct a ((documentSymbol)) ((LanguageService))}
LanguageService outliner(Outliner outliner) = documentSymbol(outliner);

@deprecated{Backward compatible with ((inlayHint))}
@synopsis{Construct a ((inlayHint)) ((LanguageService))}
LanguageService inlayHinter(InlayHinter hinter) = inlayHint(hinter);

@deprecated{Backward compatible with ((execution))}
@synopsis{Construct a ((execution)) ((LanguageService))}
LanguageService executor(CommandExecutor executor) = execution(executor);

@deprecated{
This is a backward compatibility layer for the pre-existing ((Documenter)) alias.

To replace an old-style ((Documenter)) with a new style ((hover)) service follow
this scheme:

```rascal
set[loc] oldDocumenter(loc document, Tree selection, Tree fullTree) {
    ...
}
// by this scheme:
set[loc] newHoverService([Tree selection, *Tree _spine, Tree fullTree]) {
  loc document = selection@\loc.top;
  ...
}
default set[loc] newHoverService(list[Tree] _focus) = {};
```
}
LanguageService documenter(Documenter d) {
    set[str] focusAcceptor([Tree lex, *Tree _spine, Tree fullTree]) {
        return d(lex@\loc.top, fullTree, lex);
    }

    default set[str] focusAcceptor (list[Tree] _focus) {
        return {};
    }

    return hover(focusAcceptor);
}

@deprecated{
This is a backward compatibility layer for the pre-existing ((Definer)) alias.

To replace an old-style ((Definer)) with a new style ((definition)) service follow
this scheme:

```rascal
set[loc] oldDefiner(loc document, Tree selection, Tree fullTree) {
    ...
}
// by this scheme:
set[loc] newDefinitionService([Tree selection, *Tree _spine, Tree fullTree]) {
  loc document = selection@\loc.top;
  ...
}
default set[loc] newDefinitionService(list[Tree] _focus) = {};
```
}
LanguageService definer(Definer d) {
    set[loc] focusAcceptor([Tree lex, *Tree _spine, Tree fullTree]) {
        return d(lex@\loc.top, fullTree, lex);
    }

    default set[loc] focusAcceptor (list[Tree] _focus) {
        return {};
    }

    return definition(focusAcceptor);
}


@synopsis{Registers an old-style ((Referrer))}
@deprecated{
This is a backward compatibility layer for the pre-existing ((Referrer)) alias.

To replace an old-style ((Referrer)) with a new style ((references)) service follow
this scheme.

```rascal
set[loc] oldReferrer(loc document, Tree selection, Tree fullTree) {
    ...
}
// by this scheme:
set[loc] newReferencesService([Tree selection, *Tree _spine, Tree fullTree]) {
  loc document = selection@\loc.top;
  ...
}
default set[loc] newReferencesService(list[Tree] _focus) = {};
```
}
LanguageService referrer(Referrer d) {
    set[loc] focusAcceptor([Tree lex, *Tree _spine, Tree fullTree]) {
        return d(lex@\loc.top, fullTree, lex);
    }

    default set[loc] focusAcceptor (list[Tree] _focus) {
        return {};
    }

    return references(focusAcceptor);
}

@synopsis{Registers an old-style ((Implementer))}
@deprecated{
This is a backward compatibility layer for the pre-existing ((Implementer)) alias.

To replace an old-style ((Implementer)) with a new style ((implementation)) service follow
this scheme:

```rascal
set[loc] oldImplementer(loc document, Tree selection, Tree fullTree) {
    ...
}
// by this scheme:
set[loc] newImplementationService([Tree selection, *Tree _spine, Tree fullTree]) {
  loc document = selection@\loc.top;
  ...
}
default set[loc] newImplementationService(list[Tree] _focus) = {};

```
}
LanguageService implementer(Implementer d) {
    set[loc] focusAcceptor([Tree lex, *Tree _spine, Tree fullTree]) {
        return d(lex@\loc.top, fullTree, lex);
    }

    default set[loc] focusAcceptor (list[Tree] _focus) {
        return {};
    }

    return implementation(focusAcceptor);
}

@deprecated{Please use ((util::LanguageServer::build)) or ((analysis))}
@synopsis{A summarizer collects information for later use in interactive IDE features.}
LanguageService summarizer(Summarizer summarizer
        , bool providesDocumentation = true
        , bool providesHovers = providesDocumentation
        , bool providesDefinitions = true
        , bool providesReferences = true
        , bool providesImplementations = true) {
    println("Summarizers are deprecated. Please use builders (triggered on save) and analyzers (triggered on change) instead.");
    return build(summarizer
        , providesDocumentation = providesDocumentation
        , providesHovers = providesHovers
        , providesDefinitions = providesDefinitions
        , providesReferences = providesReferences
        , providesImplementations = providesImplementations);
}

@deprecated{Please use ((util::LanguageServer::build)) or ((analysis))}
@synopsis{An analyzer collects information for later use in interactive IDE features.}
LanguageService analyzer(Summarizer summarizer
        , bool providesDocumentation = true
        , bool providesDefinitions = true
        , bool providesReferences = true
        , bool providesImplementations = true) {
    return analysis(summarizer
        , providesDocumentation = providesDocumentation
        , providesDefinitions = providesDefinitions
        , providesReferences = providesReferences
        , providesImplementations = providesImplementations);
}

@synopsis{A model encodes all IDE-relevant information about a single source file.}
@description{
* `src` refers to the "compilation unit" or "file" that this model is for.
* `messages` collects all the errors, warnings and error messages.
* `documentation` is the deprecated name for `hovers`
* `hovers` maps uses of concepts to a documentation message that can be shown as a hover.
* `definitions` maps use locations to declaration locations to implement "jump-to-definition".
* `references` maps declaration locations to use locations to implement "jump-to-references".
* `implementations` maps the declaration of a type/class to its implementations "jump-to-implementations".
}
data Summary = summary(loc src,
    rel[loc, Message] messages = {},
    rel[loc, str]     documentation = {},
    rel[loc, str]     hovers = documentation,
    rel[loc, loc]     definitions = {},
    map[loc, tuple[str id, loc idLoc, DocumentSymbolKind kind, set[DocumentSymbolTag] tags]] definitionDetails = (),
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
        list[DocumentSymbol] children=[],
        set[DocumentSymbolTag] tags = {}
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
data CodeAction(
    list[DocumentEdit] edits = [],
    Command command          = noop(),
    str title                = command.title,
    CodeActionKind kind      = quickfix())
    = action();

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

@javaClass{org.rascalmpl.vscode.lsp.parametric.RascalInterface}
@synopsis{Produce a ((Focus)) for a given tree and cursor position}
@description{
This function exists to be able to unit test ((LanguageService))s that
accept a ((Focus)) parameter, indepently of using ((registerLanguage)).

* `line` is a 1-based indication of what the current line is
* `column` is a 0-based indication of what the current column is.
}
@benefits{
* test services without spinning up an LSP server or having to run UI tests.
Each UI interaction is tested generically for you already.
}
@pitfalls{
* LSP indexing is different, but those differences are resolved in the implementation of the protocol. On the Rascal side, we see the above.
Differences are width of the character encoding for non-ASCII characters, and lines are 0-based, etc.
}
java Focus computeFocusList(Tree input, int line, int column);
