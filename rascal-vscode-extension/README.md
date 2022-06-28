# Rascal Metaprogramming Language and Language Workbench

Rascal MPL is a programming language specifically designed to analyze and manipulate code in
any data format or programming language. This means code generation, code analysis, code visualization,
anything that you can do to code and with code, Rascal is designed to make it easy.

In particular the creation of IDEs for new Programming Languages and Domain Specific Languages (DSLs)
is easy with Rascal. The Rascal VScode extension is a so-called Language Workbench.

Have a look here for more information:
   * http://www.rascal-mpl.org
   * http://www.usethesource.io

## Features

* R-LSP: IDE features for the Rascal metaprogramming language (parsing, syntax highlighting, type-checking, REPL terminal)
* P-LSP: "language parameterized" LSP server for languages expressed in Rascal. From a syntax definition you can start running your own LSP in vscode in a few steps.
* Rascal terminal: highly integrated terminal REPL that allows you to:
   * script and experiment with ad-hoc code analyses and visualizations
   * register new DSLs with VSCode using the P-LSP
   * experiment and test new DSL features

The Rascal VScode extension currently is bundled with the following libraries:
   * Rascal standard library
   * Rascal-LSP LanguageServer library
   * Java analysis library based on Eclipse JDT

## Requirements

* Installed JDK _11_
* However, the extension will propose to download the right JDK for you if you do not have it yet.

## Extension Settings

No settings so far.

## Known Issues

This extension is under development, some stuff is still buggy. Please consider it an alpha-quality try-out version.
Your constructive feedback is much appreciated at <https://github.com/usethesource/rascal-language-servers/issues>.

**This extension works best with Java 11; higher will cause breakage.**

For other things we are working on have a look here:
   * https://github.com/usethesource/rascal-language-servers/issues ; on the current extension
   * https://github.com/usethesource/rascal/issues ; on the Rascal language independent of the IDE

## Release Notes


### 0.3.0

* Rascal DSLs get more flexibility (and performance) in how they contribute the information back to VS Code. Summaries work as base, but developers can defined custom functions for specific requests from VS Code.
* Rascal DSLs can parse without waiting for other request to finish
* Now using Rascal 0.24.2
### 0.2.4

* reduced frequency of summary calls for DSLs

### 0.2.3

* updated typepal dependency to reduce debug prints

### 0.2.2

* Virtual file systems of VSCode are now available in rascal
* Bumped dependencies of java and nodejs
* rascal version bumped to 0.23.2
* Improved REPL integration for browsing and editing files from the command line

### 0.2.1

* Version bump of rascal dependency from 0.23.0 to 0.23.1, which makes browsing the standard libraries possible due to fixing an incompatible type file format.
* Version bump of rascal dependencies from 0.22.1 to 0.23.0; this includes bugfixes around prefix matching with concrete syntax trees and the use of the `private` modifier (if one alternative is private, they are now all private).
* Version bumps for typepal and rascal-core with improved typechecking efficiency.
* Better error reporting for the Rascal LSP server and the parameterized LSP server.
* Fixes around status progress bars.
* Fix for packaging of rascal-lsp jar (which helped remove spurious type-checking errors in clients of the LanguageServer library modules)

### 0.2.0

* Moved to Java 11
* Upgrade to newer Rascal type-checker
* Early support for cross-project rascal dependencies for both REPL & type-checker
* Rascal features work even without a project open (opening a single rascal file for example)
* Improved status bar messages for DSLs and making sure to always clear finished tasked
* Increases performance of project source location operations
* Automatic java detection ignores JREs
* Fixed bug that prevented detection of new projects added to the workspace

### 0.1.7

* Security release, bumping log4j dependency

### 0.1.6

* Rascal is now properly reporting progress
* Parametric DSLs can defined inlayHints to annotate a source tree

### 0.1.5

* Rascal LSP server now starts lazily to reduce memory overhead.
* Parametric LSP server now starts lazily to reduce memory overhead.
* Fixed missing print of Rascal version in the REPL.
* Added print of Rascal-lsp version in the REPL.
* Added automatic JDK download if none is available.
* Added `registerLanguage` to public extension API.

### 0.1.4

* Improved semantic tokenization and highlighting for Rascal and DSLs. Tokenization has been refined to allow differential coloring of nested nonterminals.
* Added folding regions for Rascal based on its grammar.
* Added folding regions for DSLs based on the grammar.
* Fixed REPL autoreloading for changed files.

### 0.1.3

* Bugfixes for the features below.

### 0.1.2

* Bugfixes for the features below

### 0.1.1

* Added feature to publish diagnostics while scripting code analyses in the terminal (uses util::IDEServices)
* Added feature of clickable logical URI's, like "|java+class://java/util/List|" in the terminal such that they resolve to the code location of the declared entity.

### 0.1.0

* Added features for progress reporting from both the R-LSP and the P-LSP
* Added applyEdits functionality from the terminal and the P-LSP
* Added code lenses for the P-LSP
* Fixed a number of synchronization issues

### 0.0.3

* Fixed bug that disabled the IDE generator functionality.

### 0.0.2

* Updated this Readme

### 0.0.1

* Initial release of rudimentary support for the Rascal language
* A terminal REPL for Rascal
* A parametrized LSP that can be configured directly from the Rascal REPL using `registerLanguage`

## LICENSE

Rascal and this extension are licensed under the BSD2 open-source license. Some files
and libraries are licensed with the Eclipse Licence v2. See [LICENSE](todo)
