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

Previous versions also bundled language analysis support for the Java language.
You can find this now the [java-air package](https://www.rascal-mpl.org/docs/Packages/). 
Add it to your pom.xml or have a look at the C/C++ or PHP support packages.

## Requirements

* Installed JDK _11_
* However, the extension will propose to download the right JDK for you if you do not have it yet.

## Extension Settings

No settings so far.

## Known Issues

This extension is stabilizing, some stuff is still a bit slow, but people are using it in their production development environments. Your constructive feedback is much appreciated at <https://github.com/usethesource/rascal-language-servers/issues>.

**This extension works best with Java 11; but running it on Java 17 reportedly works as well**

The Rascal type-checker now has a new binary backward compatibility feature, such that `.tpl` files remain usable
in many more situations. Also the type-checker detects and reports possible `.tpl` file incompatibility from now on.
Typically, the previous versions of .tpl files are not compatible with the new ones, so to avoid spurious errors
you _must remove all pre-existing `.tpl` files_ after upgrading. Use `mvn clean`, for example. Or remove your
`target` or `bin` folder in every Rascal project. This backwards compatiblitiy functionality is available since rascal 0.40.17, typepal 0.14.8, rascal-maven-plugin 0.28.9, and Rascal VS Code 0.12.0.

For other things we are working on have a look here:
   * https://github.com/usethesource/rascal-language-servers/issues ; on the current extension
   * https://github.com/usethesource/rascal/issues ; on the Rascal language independent of the IDE

## LICENSE

Rascal and this extension are licensed under the BSD2 open-source license. Some files
and libraries are licensed with the Eclipse Licence v2. See [LICENSE](todo)
