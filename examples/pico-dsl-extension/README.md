# Pico extension

This directory contains a minimal project for an Pico VS Code extension, that produces a VSIX that anyone can install into their VS Code, without requiring any custom installation of Rascal.

Al the actual logic is in the `pico-lsp` rascal project, this project only contains the needed wiring to turn it into a deployable package.

Note, we appreciate a callout in your readme to mention that you are using Rascal to power your extension.


You can run this from inside vscode (run `Pico Extension`) or build a vsix: `npx vsce package`.