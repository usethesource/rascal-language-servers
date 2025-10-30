# Rascal - Language Servers

This project encapsulates both the Language Server Protocol (LSP) implementation
of Rascal itself, and the LSP "generator" that can instantiate fresh LSPs for 
languages defined in or implemented in Rascal.

Currently we provide installers and client-specific features for VScode and 
no others.

This project is no longer in beta stage; it is under development but used regularly by commercial, educational and academic users.
The VScode extension can be found [here](https://marketplace.visualstudio.com/items?itemName=usethesource.rascalmpl).

### Origin story

* 2022 many contributions come from http://SWAT.engineering next to CWI SWAT.
* *2021--...* You can consider this project an evolution of
the [https://github.com/usethesource/rascal-eclipse](rascal-eclipse) project, but it offers more and better features.
On the other hand the rascal-eclipse project is still much more mature.
   * * Includes "first level" generation of IDEs from language descriptions, just-like before
* *2009--...* Rascal-eclipse merged from the Eclipse IMP project and the Rascal project, as an evolution of the earlier ASF+SDF Meta-Environment 2.x
   * Rascal merges the functionality of ASF, SDF and RScript into a comprehensive and cohesive single language
   * Scannerless top-down parsing
   * Vallang extends to concepts of the ATerm library to include (immutable) sets and indexed relations
   * Includes "first level" generation of IDEs from language descriptions
* *1998-2008* The ASF+SDF Meta-Environment 2.x was a language workbench based on:
   * SDF - declarative syntax definition, scannerless GLR parsing
   * ASF - conditional rewrite rules over concrete syntax
   * RScript - relational calculus DSL for fact analysis
   * ToolBus - strict separation of computation from coordination based on ACP
   * ATerm library - maximal sharing of terms and automatic garbage collection
   * A rewrite of its pre-decessor (see below) in C, Java, T-Script and ASF+SDF
   * It offers the generation of interactive programming environments (IDEs)
* *1984-1998* That environment was an evolution of the earlier ASF+SDF system built in Centaur Lisp
   * Generating Interactive Programming Environments from language definitions
   * Incremental context-free general parser generation
   * Incremental term rewriting engines

[![CI](https://github.com/usethesource/rascal-language-servers/actions/workflows/build.yaml/badge.svg)](https://github.com/usethesource/rascal-language-servers/actions/workflows/build.yaml)

