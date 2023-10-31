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

- [ ] typepal and rascal-core compile in the continuous integration environment and no tests fail
- [ ] release typepal
- [ ] release rascal-core
- [ ] bump typepal and rascal-core versions in rascal-maven-plugin to latest releases
- [ ] release rascal-maven-plugin
- [ ] bump rascal-maven-plugin dependency in rascal and rascal-lsp projects
- [ ] bump typepal and rascal-core versions in rascal-lsp project
- [ ] fix new errors and warnings in rascal and rascal-lsp project

# Manual version checks

- [ ] Continuous Integration runs all unit and integration tests and fails no test
- [ ] Maximum number of compiler warnings are resolved
- [ ] Version numbers are verified manually

# Actual release

- [ ] write release notes in README.md (used on the marketplace pages)
- [ ] release rascal project (when resolving SNAPSHOT dependencies choose the right versions of vallang etc, and make sure to bump the new rascal SNAPSHOT release one minor version)
- [ ] release rascal-language-servers project (take care to choose the right release versions of typepal and rascal-core you release earlier and choose their new SNAPSHOT dependencies to the latest)
   - [ ] Set the proper version inside `rascal-lsp/pom.xml` (in most cases, remove `-SNAPSHOT)
   - [ ] Set proper version information in `rascal-vscode-extension/package.json` (in most cases by removing `-head` from the current version)
   - [ ] `git commit -am "[release] set release version"`
   - [ ] `git tag vNewVersion`
   - [ ] `git tag rascal-lsp-NewVersion` (fill the rascal-lsp version that is released here, so for example rascal-lsp-2.12.0)
   - [ ] Set next version in package.json with `head` suffix; e.g. `0.2.0-head`
   - [ ] Set next version in pom.xml with `snapshot` suffix; e.g. `2.2.0-SNAPSHOT`
   - [ ] `git commit -am "[release] prepare for next release`
   - [ ] `git push`
   - [ ] `git push --tags`
- [ ] test the released vsix file from the VScode marketplace
- [ ] Make Github release, copying the release notes from the vscode extension readme


