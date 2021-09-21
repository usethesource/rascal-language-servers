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
* Rascal terminal: highly integrated terminal REPL that allows you tox:
   * script and experiment with ad-hoc code analyses and visualizations
   * register new DSLs with VScode using the P-LSP
   * experiment and test new DSL features

The Rascal VScode extension currently is bundled with the following libraries:
   * Rascal standard library
   * Rascal-LSP LanguageServer library
   * Java analysis library based on Eclipse JDT

## Requirements

* Installed JDK _8_ 
* JAVA_HOME set to this JDK

## Extension Settings

No settings so far.

## Known Issues

This extension is under development, some stuff is still buggy. Please consider it an alpha-quality try-out version
Your constructive feedback is much appreciated at <https://github.com/usethesource/rascal-language-servers/issues>.

**This extension only works with Java 8; it has to be ported to Java 11 and higher still.**

For other things we are working on have a look here:
   * https://github.com/usethesource/rascal-language-servers/issues ; on the current extension
   * https://github.com/usethesource/rascal/issues ; on the Rascal language independent of the IDE
  
## Release Notes

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
