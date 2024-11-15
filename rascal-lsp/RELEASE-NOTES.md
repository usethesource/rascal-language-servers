# Rascal LSP release notes

Note that rascal-lsp releases are bundled with VS Code releases, however due to historic reasons, their versions do not align. Until they do we'll denote both the VS Code and the rascal LSP release next to each other.

## Release 2.21.0 (VS Code: 0.12.0)

* New feature: The "Rename Symbol" command (default: `F2`) is now supported for all identifiers in Rascal modules. Renaming is safe, so the semantics of Rascal code before/after renaming is the same.

* New feature: Code Actions (default: `CTRL+.`) are now supported in Rascal modules to analyze and transform code (e.g.: visualization of import graphs; simplification of functions). Code actions can also be defined for DSLs; see the changes to module `util::LanguageServer` below.

* New feature: Keywords, numbers, strings (single-line), regular expressions, comments, and tags are now highlighted in Rascal modules even in the presence of parse errors. This feature uses a TextMate grammar for Rascal, generated using [`rascal-textmate`](https://github.com/SWAT-engineering/rascal-textmate).

* Upgrade to a greatly improved version of the Rascal type checker, including:
  * Backward-compatibility between different versions of libraries. After this upgrade, you won't have to keep all your dependencies aligned with the latest released Rascal version. We think we have developed a scheme that should work for all future upgrades, but there might be a few bumps in the road the coming releases.
  * Better type checking errors (roughly 3 years of bugfixes)
  * Increased performance for partial type checks
  * Deprecation warnings for deprecated functions

* Upgrade to Rascal 0.40.17, including:
  * A new `mvn` scheme for referencing jars in the maven repository
  * Improvements to json/xml/html deserialization, including better origin tracking
  * A new REPL progress bar that you can also use via `util::Monitor`
  * Improvements to our support for defining pretty printing
  * Clipboard control from Rascal code
  * Upgraded the Java support in m3
  * Various bugfixes

  See the [release notes](https://www.rascal-mpl.org/release-notes/rascal-0-40-x-release-notes/) of Rascal 0.40.17 for details.

* For DSL extension developers:
  * The present release is updated to work with Node.js 18. The next release will be updated to work with Node.js 20, to align with the VS Code engine and our dependencies.
  * Changes to module `util::LanguageServer`:
    * Code Actions can be defined using constructor `action` of type `CodeAction`, and registered using constructor `codeAction` of type `LanguageService`.
    * Constructors in type `LanguageService` are renamed to align them with the corresponding requests in LSP. Usage of the old names is now deprecated.
    * Keyword parameter `useSpecialCaseHighlighting` is introduced on constructor `parsing` of type `LanguageService` (default: `true`). It is used to control whether or not the semantic highlighter should apply an odd special case (i.e., categories of `syntax` non-terminals are sometimes ignored); the semantic highlighter has been applying this special case for several releases. Usage of the special case is now deprecated.
    * Constructor `codeLens` of type `LanguageService` has a function parameter with return type `lrel` instead of `rel` as before. This is to ensure that multiple code lenses for a single line are always ordered in the same way. Usage of return type `rel` for this function parameter is now deprecated.
    * Type `Focus` is introduced. It is used to declare the parameters of on-demand services (`hover`, `definition`, `referenes`, `implementation`) instead of `loc`-`Tree`-`Tree` triples as before. Unlike such triples, a value of type `Focus` provides the *full* context (list of subtrees) of the cursor position. Usage of the triples is now deprecated.

    For each deprecated item:
    * In the present release, support is retained for backward-compatibility, but existing code *should* be updated.
    * In a future release, support will be removed, and existing code *must* be updated. (In the case of keyword parameter `useSpecialCaseHighlighting`, the default will first become `false` before it is removed.)

    See module `util::LanguageServer` for details, and module `demo::lang::pico::LanguageServer` for examples.

* Other improvements:
  * New feature: When the Rascal LSP server crashes, VS Code will now report the crash in a notification, including a button to open a GitHub issue.
  * New feature: The default names of Rascal terminals can now be configured via setting `Rascal > Terminal > Name: Origin Format`.
  * New feature: Project setups are now checked for common errors.
  * Fixed "Start Rascal Terminal and Import this module" command

## Release 2.20.2 (VS Code: 0.11.2)

* bumping to rascal 0.34.2:
  * improved heuristic of json mapping for an empty map
  * fixed bugs related to grammar fusing for modules with extends & imports

## Release 2.20.1 (VS Code: 0.11.1)

* bugfix for syntax highlighting categories

## Release 2.20.0 (VS Code: 0.11.0)

* there is a new "Rascal Configuration" view that shows the resolved dependencies per project xml, and allows you to browse the rascal sources inside of those.
* the `summarizer` contribution for DSLs has been replaced by `analyzer` and `builder`. Please review your language setup and migrate to builders (only on save), and analyzer (every change in the IDE). We have tried to keep the behavior as close to the original summarizer, but it will not be triggered on-demand anymore.
* small improvements:
  * DSLs can now register multiple extensions for a single language
  * we check for common rascal project setup errors when starting a new REPL
  * automatic reloading of changes now also works for cross-project dependencies
  * We have a new and improved version of the rascal logo
  * downstream dependencies were updated to the latest version

## Release 2.19.3 (VS Code: 0.10.2)

* upgrading to rascal 0.34.1 to fix a regression in concrete syntax
* the REPL is marked as non-transient, until we can properly reconnect it after a reload of the window

## Release 2.19.1 (VS Code: 0.10.1)

* bugfix for preloaded parsers

## Release 2.19.0 (VS Code: 0.10.0)

* the automatic JVM downloader will now prompt you for updates if they are available
* Every REPL now gets named after the project they are connected to
* For deployed DSL extensions (via the npm published version of this plugin) we have a temporary interface to supply a preloaded parser
* We now automatically run our pre-release test on every PR/commit via VS Code extension tester
* upgraded to rascal 0.34.0:
  * various performance improvements that improve import time and memory pressure
  * removal of some unused resolvers (benchmark/testdata/test-modules/boot)
  * various small bug fixes
* bug fixes:
  * fixed a bug where changes in a pom.xml would only be visible after a VS Code restart. Now it only takes a restart of the REPL. (or re-trigger the type checker)
  * fixed a bug where we would register schema's that were already registered (zip error message in the debug console)

## Release 2.18.0 (VS Code: 0.9.0)

* Webviews opened from Rascal can now have a title and view column
* There is now a setting to influence how much memory a REPL gets allocated (`rascal.interpreter.maxHeapSize` & `rascal.interpreter.stackSize`).
* The Rascal REPL now has a icon (it requires a bugfix in vscode 1.80 to correctly show)
* upgraded to rascal 0.33.7:
  * Better support for ModuleParserStorage
  * Changes to support the new webview capabilities
  * Various bugfixes
* This release includes an "easter egg"; an experimental debugger for Rascal which is currently under test.

## Release 2.17.2 (VS Code: 0.8.3)

* upgraded to rascal 0.33.5:
  * Fixed a second bug aroung ModuleParserStorage
  * Preparing for debugger API


## Release 2.17.1 (VS Code: 0.8.2)

* Upgraded to rascal 0.33.3:
  * Fixed a bug around ModuleParserStorage (see `lang::rascal::grammar::storage::ModuleParserStorage`)
  * Bugfixes with the lib resolver
  * New feature: `IO::findResources` that replaces some functionality that people used the `lib://` scheme for.

## Release 2.14.1 (VS Code: 0.8.1)

* Bugfix release for regression in typechecker introduced in v0.8.0

## Release 2.14.0 (VS Code: 0.8.0)

* This release comes with rascal 0.32.0 which features:
   * In ParseTree, `storeParsers` and `loadParsers` offer a way to skip loading and executing a parser generator after deployment. Parsers can be stored in an opaque binary format that is very quickly loaded again and wrapped as a Rascal function with the same interface as what the `parsers` function generates.
   * The interpreter uses `ModuleName.parsers` to parse modules with concrete syntax for every module file `ModuleName.rsc` that is in the same jar file.
   * 8 to 12% improvement in generated parser efficiency
   * several issues with respect to URI syntax resolved
   * fixes for #1805, #1804 and #1793; related to the consistency of the documentation (broken links and broken code examples)
   * M3 now has a default `implicitDeclarations` set which programming languages can fill with entities that are defined by language but do not have a source position anywhere (not even in the standard library).
   * M3 now has "specifications" (read test functions) that check what it means for a typical abstract syntax tree to be correct and how correct M3 relations relate to one another. For example, in an M3 model something should not be used if it is not declared, unless there are compilation errors.
   * Project names in the `project://` scheme are now always to be lowercase letters, hyphens (`-`) or underscores (`_`), as in `|project://my-project-name/src/main/rascal|`
* This release is based on rascal-maven-plugin 0.19.2 and rascal-tutor 0.11.4, which come with:
   * API functions now have their signature first, before the other documentation sections are printed
   * API modules have a link to their github source pages
   * All packages must report their FUNDING sources now
   * All packages must report their CITATION preferences now
   * All packages must report their LICENSE now
   * All packages automatically report their dependencies now as sourced from pom.xml
   * All code examples, if tagged with `error`, must also throw an error.
   * Fixed all code examples that were falsely tagged with `error`
* Finally, these are the changes local to the rascal-language-servers and rascal-lsp projects:
   * PR #262 fixed three important issues with the LSP server interactions that had effect on the quality of the hover help and the references feature. In particular when more source files were involved (e.g. in the Rascal case) sometimes seemingly random results are shown, or none. This fix removes the root cause of the confusion and also rationalizes some code around the mapping from cursor locations to locations in the summaries.
   * util::LanguageServer is now minimally documented and will appear on rascal-mpl.shortly in the Packages section under the `rascal-mpl` package.
   * Checks in RASCAL.MF files have been extended and improved.
   * Loading of classes from dependent projects has improved in the VScode terminal and the language-parametrized LSP; there was a bug which caused arbitrary jar files to be loaded instead of the ones declared in the `pom.xml`. Usually the last dependency in the pom file "won". This also means that `lib://myDependency` would sometimes implicitly resolve to `lib://myLaterDependency` and throw IO exceptions and `exists` tests set to `false`.

## Release 2.13.4 (VS Code: 0.7.0)

* Fixes issues with IDEServices::registerDiagnostics where only the first error would be registered.
* Fixes issue with Rascal library loading where Rascal source files where loaded in a different order from the path than their accompanied .class files.
* util::Reflective::newRascalProject now guarantees syntax regex for project folders and project names.
* XML, HTML and JSON parsers now provide source origin location with every node.
* Improved support for Windows PATHs in dependency resolution code of Rascal
* Fixed UTF8 encoding issue in the Lucene library
* Documentation improvements
* Several minor issues.
* Added more validation features for RASCAL.MF files.

## Release 2.12.1 (VS Code: 0.6.3)

* bugfix for writes to VS Code virtual file systems initiated from rascal (only first 8KB of the write was correctly transferred)


## Release 2.12.0 (VS Code: 0.6.1)

* bumped rascal to 0.28.3 for performance fixes around maven and various bugfixes
* typechecking with latest 0.14.6 version of rascal-maven-plugin (and using linked rascal-library) to avoid outdated typepal files
* now allowing jdk 17 as runtime, we'll still install 11 as default, but jdk 17 seems to work.
* Bugfix for automatic jdk downloader on aarch64 osx (Apple M1 for example)
* RASCAL.MF files are now checked for common errors and incorrect configurations

## Release 2.11.4 (VS Code: 0.6.0)

* bumped rascal to 0.28.0 for the addition of vis::Graphs and fixes in util::Sampling and vis::Charts.
* fixed issues with the IDEServices::edit function, in the VScode terminal context and VScode parameterized DSL LSP server context, with logical locations and locations with line/column information (both previously unsupported), for use in interactive visualizations (for example).
* allow vscode extensions to use rascal-vscode independently of installed rascal; this means DSL implementations can run their own native extension when they are finished being developed.
* as a result, rascal-vscode is now also an independent npm package.

## Release 2.11.3 (VS Code: 0.5.6)

* fixes [issue](https://github.com/usethesource/rascal/issues/203) which was caused by an issue in the rascal project.
* bumped rascal to 0.27.3 for the above fix.

## Release 2.11.2 (VS Code: 0.5.5)

This release is about including a new version of the rascal project, 0.27.2:

* fixes [issue](https://github.com/usethesource/rascal/issues/1719) with variable scope leakages for optimized visit statements
* adds util::Validator for validating complex untyped hierarchical structures (such as obtained from XML, JSON or YAML)
* added vis::Charts for eight basic chart types
* redesign of lang::json::IO with more natural mapping to JSON objects, but more precarious constraints for mapping back. See util::Validator for bridging the new gap if you have a complex ADT with lots of overloading and many constructors per type. The default mapping only supports simple enums and single constructors per ADT.

## Release 2.11.0 (VS Code: 0.5.2)

* fixed efficiency issue in use of the `project:///` scheme for browsing file hierarchies, like for example in getASTs function for Java.
* new logo

## Release 2.10.0 (VS Code: 0.5.1)

* upgraded to latest version of rascal (0.26.2) to fix an issue that become more present with the multiple evaluators feature

## Release 2.9.0 (VS Code: 0.5.0)

* Now using rascal 0.24.7
* Rascal DSLs can now use multiple evaluators if they want (see discussion in [#181](https://github.com/usethesource/rascal-language-servers/issues/181))
* Rascal DSLs do not get typepal on the PathConfig/evaluator search path by default. Only rascal & rascal-lsp.
* `unregisterLanguage` was added to clear all or parts of a language
* Rascal REPLs get a progressbar while starting. This provides more feedback that something is happening, especially until the  [performance regression on windows](https://github.com/usethesource/rascal-language-servers/issues/187) is fixed.

## Release 2.7.0 (VS Code: 0.4.0)

* Now using rascal 0.24.6
* Better support pom.xml dependency resolution
* Rascal LSP commands can return values that are available in typescript
* Bugfix for handling def/ref/hover request in the "start" region of a rascal parse tree
* Moved to LSP 3.17 using the native inlayHint implementation
* Updated java & node dependencies to latest releases
* Improved performance of DSL init (specifically the time spent on generating parsers)


## Release 2.4.0 (VS Code: 0.3.0)

* Rascal DSLs get more flexibility (and performance) in how they contribute the information back to VS Code. Summaries work as base, but developers can defined custom functions for specific requests from VS Code.
* Rascal DSLs can parse without waiting for other request to finish
* Now using Rascal 0.24.2

## Release 2.2.2 (VS Code: 0.2.4)

* reduced frequency of summary calls for DSLs

## Release 2.2.1 (VS Code: 0.2.3)

* updated typepal dependency to reduce debug prints

## Release 2.2.0 (VS Code: 0.2.2)

* Virtual file systems of VSCode are now available in rascal
* Bumped dependencies of java and nodejs
* rascal version bumped to 0.23.2
* Improved REPL integration for browsing and editing files from the command line

## Release 2.1.0 (VS Code: 0.2.1)

* Version bump of rascal dependency from 0.23.0 to 0.23.1, which makes browsing the standard libraries possible due to fixing an incompatible type file format.
* Version bump of rascal dependencies from 0.22.1 to 0.23.0; this includes bugfixes around prefix matching with concrete syntax trees and the use of the `private` modifier (if one alternative is private, they are now all private).
* Version bumps for typepal and rascal-core with improved typechecking efficiency.
* Better error reporting for the Rascal LSP server and the parameterized LSP server.
* Fixes around status progress bars.
* Fix for packaging of rascal-lsp jar (which helped remove spurious type-checking errors in clients of the LanguageServer library modules)

## Release 2.0.0 (VS Code: 0.2.0)

* Moved to Java 11
* Upgrade to newer Rascal type-checker
* Early support for cross-project rascal dependencies for both REPL & type-checker
* Rascal features work even without a project open (opening a single rascal file for example)
* Improved status bar messages for DSLs and making sure to always clear finished tasked
* Increases performance of project source location operations
* Automatic java detection ignores JREs
* Fixed bug that prevented detection of new projects added to the workspace

## Release 1.2.2 (VS Code: 0.1.7)

* Security release, bumping log4j dependency

## Release 1.2.0 (VS Code: 0.1.6

* Rascal is now properly reporting progress
* Parametric DSLs can defined inlayHints to annotate a source tree
