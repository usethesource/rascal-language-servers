# Rascal Metaprogramming Language and Language Workbench

Rascal MPL is a programming language specifically designed to analyze and manipulate code in 
any data format or programming language. This means code generation, code analysis, code visualization,
anything that you can do to code and with code, Rascal is designed to make it easy.

In particular the creation of IDEs for new Programming Languages and Domain Specific Languages (DSLs)
is easy with Rascal. The Rascal VScode extension is a so-called Language Workbench.

## Features

* IDE features for the Rascal metaprogramming language (parsing, syntax highlighting, type-checking, REPL terminal)
* A language paramatrized LSP server for languages expressed in Rascal. From a syntax definition you can start running your own LSP in vscode in a few steps.

## Requirements

* Installed JDK 8 
* JAVA_HOME set to this JDK

## Extension Settings

No settings so far.

## Known Issues

* This extension is under development, some stuff is still buggy.

## Release Notes

Users appreciate release notes as you update your extension.

### 0.0.3

* Fixed bug that disabled the IDE generator functionality.

### 0.0.2

* updated this Readme
### 0.0.1

* Initial release of rudimentary support for the Rascal language
* A terminal REPL for Rascal
* A parametrized LSP that can be configured directly from the Rascal REPL using `registerLanguage`

## LICENSE

Rascal and this extension are licensed under the BSD2 open-source license. Some files
and libraries are licensed with the Eclipse Licence v2. See [LICENSE](todo)
