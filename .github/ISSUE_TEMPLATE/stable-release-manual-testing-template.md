---
name: Stable release manual testing template
about: This is a list of things to do and to check at the time of a stable release 
title: "[RELEASE] version 0.x.x"
labels: release testing
assignees: ''

---

# Preliminaries

* Every time this document says "release X" ; we mean to execute the instructions of this Wiki page: https://github.com/usethesource/rascal/wiki/How-to-make-a-release-of-a-Rascal-implemenation-project
* The current release instructions are focused on the rascal-language-servers project and the VScode extension that is included there
* If you edit this template, then please push relevant improvements to the template itself for future reference.

# Pre-releasing dependent tools in unstable

First a "pre-release" of the supporting compiler/typechecker tools must be done, so we know we are releasing a consistently compiled standard library.

- [x] typepal and rascal-core compile in the continuous integration environment and no tests fail
- [x] release typepal
- [x] release rascal-core
- [x] bump typepal and rascal-core versions in rascal-maven-plugin to latest releases
- [x] release rascal-maven-plugin
- [x] bump rascal-maven-plugin dependency in rascal and rascal-lsp projects
- [x] bump typepal and rascal-core versions in rascal-lsp project
- [x] fix new errors and warnings in rascal and rascal-lsp project

# Manual version checks

- [x] Continuous Integration runs all unit and integration tests and fails no test
- [x] Maximum number of compiler warnings are resolved
- [x] Version numbers are verified manually

# Manual feature tests

- [x] Build VScode extension locally download and install latest .vsix file in VScode for testing purposes

The list below was copied from the rascal-eclipse release; have to adapt while we go:

- [x] Open a Rascal REPL using the CMD+P start-rascal-terminal command (without a project)
- [x] Manually create a new Rascal project, with src folder, META-INF/RASCAL.MF file with `Required-Libraries: |lib://rascal-lsp|`
- [x] Can edit a new Rascal file in the Rascal project
- [x] Save on Rascal file triggers type-checker, errors visible
- [x] Rascal outline works
- [x] Clicking links in REPL opens editors and websites
- [x] `rascal>1 + 1` on the REPL
- [x] `import IO; println("Hello Rascal!");`
- [x] in editor, click on use of name jumps to definition
- [x]  jump-to-definition also work to libraries and inside of libraries #150
- [x] clicking in outline jumps to editor to right position
- [x] syntax highlighting in editor works
- [x] add dependency on another project by editing `RASCAL.MF`: `Require-Libraries: |lib://otherProject|`, import a module and test the type-checker as well as the interpreter for correct resolution, also test a new REPL and import a library modules from the imported library.
- [x] Run `import demo::lang::pico::LanguageServer;` and  `demo::lang::pico::LanguageServer::main();` and test the editor of some example pico files:
   - [x] pico syntax highlighting
   - [x] pico parse errors
   - [x] pico jump-to-definition
   - [x] pico code lenses with command (Rename variables a to b)
- [ ] try `:edit demo::lang::pico::LanguageServer` #151

# Actual release

- [x] write release notes in README.md (used on the marketplace pages)
- [x] release rascal project (when resolving SNAPSHOT dependencies choose the right versions of vallang etc, and make sure to bump the new rascal SNAPSHOT release one minor version)
- [ ] release rascal-language-servers project (take care to choose the right release versions of typepal and rascal-core you release earlier and choose their new SNAPSHOT dependencies to the latest)
   - [x] release rascal-lsp (`cd rascal-lsp; mvn clean compile; mvn release:prepare`)
   - [x] Set proper version information in package.json; probably by removing `-head` from the current version
   - [x] `git commit -am "[release] set release version]"`
   - [x] `git tag vNewVersion`
   - [x] `git push --tags`
   - [x] Set next version in package.json with `head` suffix; e.g. `v0.2.0-head`
   - [x] `git commit -am "[release] prepare for next release`
- [x] test the released vsix file from the VScode marketplace


