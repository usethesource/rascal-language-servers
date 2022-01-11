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

* Installed JDK _8_ (11 is also allowed)
* JDK on PATH *or* configured in JAVA_HOME environment variable

## Extension Settings

No settings so far.

## Known Issues

This extension is under development, some stuff is still buggy. Please consider it an alpha-quality try-out version.
Your constructive feedback is much appreciated at <https://github.com/usethesource/rascal-language-servers/issues>.

**This extension works best Java 8; most features work well with Java 11, but higher will cause breakage.**

For other things we are working on have a look here:
   * https://github.com/usethesource/rascal-language-servers/issues ; on the current extension
   * https://github.com/usethesource/rascal/issues ; on the Rascal language independent of the IDE

## Release Notes

### 0.1.7

* bumping log4j2 dependency for compliance


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

### 0.1.1

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
